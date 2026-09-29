#!/bin/bash
# End-to-end check of the mcp-tasks-server example: the Cassini REST API that writes the tasks, the MCP server that
# reads them (through the MCP Inspector CLI, @modelcontextprotocol/inspector 2.6.0, run via `npx -y`), the Mansart
# transaction that makes a bulk write all or nothing, the dev console's pool panel, and the PostgreSQL database
# surviving a restart.
#
# Usage:
#   ./test-tasks.sh --start
#   TASKS_URL=http://127.0.0.1:18093 DEVCONSOLE_PORT=18094 ./test-tasks.sh --start
#
# --start is required: the script starts the already-built server itself, through the launcher that
# vidocq:package generates under target/<dist>/bin/, because its checks count the tasks seeded in a fresh
# database, read the dev console, and restart the server. It builds nothing: run `mvn package` on the module,
# or `mvn verify` from the repository root, first.
#
# The server runs as a dev launch (VIDOCQ_LAUNCH_MODE=dev, which turns the dev console on). It listens on the port
# of TASKS_URL, 18093 by default, with the dev console on DEVCONSOLE_PORT, 18094 by default: not the 18090 and 18092
# of vidocq.properties, so the script can run next to a vidocq:dev session. It uses a PostgreSQL database of its own,
# a Docker container named mcp-tasks-e2e-<pid of this script> (postgres:16-alpine, the dev service's image) on a free
# loopback port, removed on exit; the launcher runs no dev service. The script refuses to start when either
# port is taken, stops the server at once if its log mentions port 8080 or 8888, and always stops it on exit
# (trap), pass or fail. The server log is mcp-tasks-server-run.log, next to this script.
#
# The database password is never printed: the checks report how many lines hold it, which must be 0.
#
# Exits non-zero if any check fails; always prints a PASS/FAIL summary.
set -u

INSPECTOR_PKG="@modelcontextprotocol/inspector@2.6.0"
TASKS_URL="${TASKS_URL:-http://127.0.0.1:18093}"
TASKS_URL="${TASKS_URL%/}"
DEVCONSOLE_PORT="${DEVCONSOLE_PORT:-18094}"
MCP_URL="$TASKS_URL/mcp"
START=0

for arg in "$@"; do
    case "$arg" in
        --start) START=1 ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

BASE=$(cd "$(dirname "$0")" && pwd)
MODULE="$BASE/mcp-tasks-server"
LAUNCHER=""
LOG="$BASE/mcp-tasks-server-run.log"
PG_CONTAINER="mcp-tasks-e2e-$$"
PG_IMAGE="postgres:16-alpine"
PG_PORT=""
SERVER_PID=""
WORK=""

if [ "$START" -ne 1 ]; then
    echo "Usage: $0 --start" >&2
    echo "  The checks count the tasks seeded in a fresh database, read the dev console and restart the server, so" >&2
    echo "  this script always starts a server of its own, on a database file of its own (see the header)." >&2
    exit 1
fi

# --- Prerequisites -----------------------------------------------------------------------------

for tool in npx node curl lsof docker; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "FAIL: $tool not found on PATH (npx and node come with Node.js, for the MCP Inspector CLI; docker runs" >&2
        echo "      the PostgreSQL database)." >&2
        exit 1
    fi
done

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA_BIN="${JAVA_BIN:-java}"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    echo "FAIL: no java executable found (checked \"$JAVA_BIN\"). Set JAVA_HOME to a JDK 25+ install." >&2
    exit 1
fi
JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | head -1 | sed -nE 's/.*version "([0-9]+).*/\1/p')
if ! [ "${JAVA_MAJOR:-0}" -ge 25 ] 2>/dev/null; then
    echo "FAIL: JDK 25 or newer is required to run this server (Vidocq ships Java 25 class files)." >&2
    echo "      Found: $("$JAVA_BIN" -version 2>&1 | head -1)" >&2
    echo "      Set JAVA_HOME to a JDK 25+ install, e.g. JAVA_HOME=\$(sdk home java 25-tem)." >&2
    exit 1
fi

# The listener is bound on the loopback address (vidocq.properties): TASKS_URL must name it, with a port.
PORT=$(printf '%s' "$TASKS_URL" | sed -nE 's#^http://(127\.0\.0\.1|localhost):([0-9]+)$#\2#p')
if [ -z "$PORT" ]; then
    echo "FAIL: TASKS_URL must be http://127.0.0.1:<port> or http://localhost:<port>, not $TASKS_URL." >&2
    exit 1
fi
DEVC="$DEVCONSOLE_PORT"
if ! [ "$DEVC" -gt 0 ] 2>/dev/null; then
    echo "FAIL: DEVCONSOLE_PORT must be a port number, not $DEVC." >&2
    exit 1
fi
for p in "$PORT" "$DEVC"; do
    case "$p" in
        8080 | 8888)
            echo "FAIL: this script never uses port $p (8080 and 8888 are left to other servers). Pick another one." >&2
            exit 1
            ;;
    esac
done
if [ "$PORT" = "$DEVC" ]; then
    echo "FAIL: the application and the dev console need two different ports (both are $PORT)." >&2
    exit 1
fi
for p in "$PORT" "$DEVC"; do
    HOLDER=$(lsof -ti "tcp:$p" -sTCP:LISTEN 2>/dev/null | head -1)
    if [ -n "$HOLDER" ]; then
        echo "FAIL: port $p is already in use by pid $HOLDER ($(ps -o comm= -p "$HOLDER" 2>/dev/null))." >&2
        echo "      Stop that process, or pick other ports:" >&2
        echo "      TASKS_URL=http://127.0.0.1:18096 DEVCONSOLE_PORT=18097 $0 --start" >&2
        exit 1
    fi
done

DIST=$(ls -d "$MODULE"/target/mcp-tasks-server-*/ 2>/dev/null | head -1)
DIST=${DIST%/}
LAUNCHER="$DIST/bin/mcp-tasks-server.sh"
if [ -z "$DIST" ] || [ ! -x "$LAUNCHER" ]; then
    echo "FAIL: no launcher under $MODULE/target/<dist>/bin/." >&2
    echo "      Run 'mvn package' on mcp-tasks-server (or 'mvn verify' from the repository root) first." >&2
    exit 1
fi

# The password the server uses: the one packaged in the application jar, else the one in the sources. Only the
# number of lines that hold it is ever printed.
PW=""
APP_JAR="$DIST/app/mcp-tasks-server.jar"
if command -v unzip >/dev/null 2>&1 && [ -f "$APP_JAR" ]; then
    PW=$(unzip -p "$APP_JAR" vidocq.properties 2>/dev/null | sed -n 's/^vidocq\.pool\.password=//p' | tr -d '\r')
fi
if [ -z "$PW" ]; then
    PW=$(sed -n 's/^vidocq\.pool\.password=//p' "$MODULE/src/main/resources/vidocq.properties" | tr -d '\r')
fi
if [ "${#PW}" -lt 8 ]; then
    echo "FAIL: vidocq.pool.password is missing or shorter than 8 characters: the redaction check needs one." >&2
    exit 1
fi

# --- Server ------------------------------------------------------------------------------------

WORK=$(mktemp -d "${TMPDIR:-/tmp}/test-tasks.XXXXXX")

# The database: a PostgreSQL container of this run, with the user, database and password of vidocq.properties, on a
# free loopback port. Ready once it accepts TCP connections: during its first start the image runs a temporary
# server on its Unix socket only, then restarts.
start_database() {
    local user db
    user=$(sed -n 's/^vidocq\.pool\.username=//p' "$MODULE/src/main/resources/vidocq.properties" | tr -d '\r')
    db=$(sed -n 's|^vidocq\.pool\.url=jdbc:postgresql://[^/]*/\([A-Za-z0-9_]*\).*|\1|p' \
        "$MODULE/src/main/resources/vidocq.properties" | tr -d '\r')
    echo "Starting PostgreSQL ($PG_IMAGE, container $PG_CONTAINER)..."
    if ! docker run -d --rm --name "$PG_CONTAINER" -p 127.0.0.1::5432 -e POSTGRES_USER="${user:-tasks}" \
            -e POSTGRES_DB="${db:-tasks}" -e POSTGRES_PASSWORD="$PW" "$PG_IMAGE" >/dev/null; then
        echo "FAIL: docker could not start $PG_IMAGE." >&2
        exit 1
    fi
    PG_PORT=$(docker port "$PG_CONTAINER" 5432/tcp | head -1 | sed 's/.*://')
    for _ in $(seq 1 60); do
        docker exec "$PG_CONTAINER" pg_isready -q -h 127.0.0.1 -U "${user:-tasks}" -d "${db:-tasks}" && return 0
        sleep 1
    done
    echo "FAIL: PostgreSQL did not accept connections within 60 s." >&2
    exit 1
}

stop_server() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "Stopping server (pid $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null
        for _ in $(seq 1 20); do
            kill -0 "$SERVER_PID" 2>/dev/null || break
            sleep 0.5
        done
        kill -9 "$SERVER_PID" 2>/dev/null
    fi
    SERVER_PID=""
}

cleanup() {
    stop_server
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1
    [ -n "$WORK" ] && rm -rf "$WORK"
}
trap cleanup EXIT
start_database
DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/$(sed -n 's|^vidocq\.pool\.url=jdbc:postgresql://[^/]*/\([A-Za-z0-9_]*\).*|\1|p' \
    "$MODULE/src/main/resources/vidocq.properties" | tr -d '\r')"

# The log of the running boot only: BOOT_FROM is its first line in $LOG.
BOOT_FROM=1
boot_log() {
    tail -n +"$BOOT_FROM" "$LOG"
}

# The last lines of the log, without any line that holds the password.
show_log() {
    local withheld
    withheld=$(grep -cF -- "$PW" "$LOG")
    echo "--- last lines of $LOG ($withheld line(s) holding the password withheld) ---" >&2
    grep -vF -- "$PW" "$LOG" | tail -n 60 >&2
    echo "---" >&2
}

# A line of this boot naming port 8080 or 8888 means a port was not overridden: stop the server at once.
guard_reserved_ports() {
    local hit
    hit=$(boot_log | grep -nE '(:|port )(8080|8888)([^0-9]|$)' | head -3)
    if [ -n "$hit" ]; then
        stop_server
        echo "FAIL: the server log names port 8080 or 8888; the server was stopped at once:" >&2
        printf '%s\n' "$hit" | grep -vF -- "$PW" >&2
        exit 1
    fi
}

mcp_probe() {
    curl -s -o /dev/null -m 3 -w '%{http_code}' -X POST "$MCP_URL" \
        -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
        -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test-tasks.sh","version":"1"}}}'
}

# Starts the launcher vidocq:package generates as a dev launch on the script's ports and database file, and
# waits for a real MCP initialize to answer 200.
#
# That launcher forwards its arguments to the application rather than to the JVM, so these settings travel as
# environment variables, which Vidocq reads as a config source of ordinal 300 — above the packaged
# vidocq.properties. A caller who needs JVM flags can still pass JAVA_TOOL_OPTIONS, but a -D there becomes a
# system property, ordinal 400, and would override these.
#
# It also runs `exec java`, taking whatever java comes first on PATH, and Vidocq's jars are class file version
# 69: an older java dies before main (Vidocq/vidocq#102). JAVA_HOME, when set, therefore goes on PATH ahead of
# it. The subshell enters the module, where the server's relative paths start.
start_server() {
    BOOT_FROM=$(($(wc -l <"$LOG") + 1))
    echo "Starting server via $LAUNCHER (application port $PORT, dev console port $DEVC, database on port $PG_PORT)..."
    #
    # The subshell execs the launcher, which execs java, so $! below is the JVM itself. Without that exec, $!
    # is the subshell: the checks that read the server's sockets see none, and stopping it leaves the JVM
    # holding both ports.
    (
        cd "$MODULE" || exit 1
        export VIDOCQ_LAUNCH_MODE=dev
        export VIDOCQ_CHAPPE_LISTENER_DEFAULT_PORT="$PORT"
        export VIDOCQ_DEVCONSOLE_PORT="$DEVC"
        export VIDOCQ_POOL_URL="$DB_URL"
        export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
        exec "$LAUNCHER"
    ) >>"$LOG" 2>&1 &
    SERVER_PID=$!

    echo -n "Waiting for an MCP server at $MCP_URL..."
    local status=000
    for _ in $(seq 1 60); do
        guard_reserved_ports
        status=$(mcp_probe)
        [ "$status" = "200" ] && break
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo
            echo "FAIL: the server exited before its MCP endpoint answered." >&2
            show_log
            exit 1
        fi
        echo -n "."
        sleep 1
    done
    echo
    guard_reserved_ports
    if [ "$status" != "200" ]; then
        echo "FAIL: $MCP_URL answered HTTP $status to an MCP initialize request, not 200." >&2
        show_log
        exit 1
    fi
    echo "Server is up (pid $SERVER_PID)."
}

# The TCP ports a process listens on, sorted, one per line.
listening_ports() {
    lsof -nP -a -p "$1" -iTCP -sTCP:LISTEN 2>/dev/null | awk 'NR > 1 { print $9 }' | sed -E 's/.*:([0-9]+)$/\1/' | sort -un
}

# --- Helpers -------------------------------------------------------------------------------------

PASS=0
FAIL=0

check() {
    local description="$1"
    local output="$2"
    local ok="$3"
    if [ "$ok" -eq 1 ]; then
        echo "PASS: $description"
        PASS=$((PASS + 1))
    else
        echo "FAIL: $description"
        echo "--- output ---"
        echo "$output"
        echo "--------------"
        FAIL=$((FAIL + 1))
    fi
}

# json EXPR: reads the first JSON document of stdin (the Inspector prints an error line after an isError result)
# and prints the JavaScript expression EXPR, where j is the document and inner(s) parses the JSON text s. A string
# prints as it is, anything else as JSON. Prints nothing when there is no document or EXPR fails.
JSON_JS='
const src = require("fs").readFileSync(0, "utf8");
let doc, found = false;
try { doc = JSON.parse(src); found = true; } catch (e) {
  const lines = src.split("\n");
  search: for (let i = 0; i < lines.length; i++) {
    if (!/^\s*[\[{]/.test(lines[i])) continue;
    for (let k = i; k < lines.length; k++) {
      if (k > i && !/^[\]}]/.test(lines[k])) continue;
      try { doc = JSON.parse(lines.slice(i, k + 1).join("\n")); found = true; break search; } catch (e2) {}
    }
  }
}
if (!found) process.exit(2);
let v;
try { v = new Function("j", "inner", "return (" + process.argv[1] + ");")(doc, s => JSON.parse(s)); } catch (e) { process.exit(3); }
if (v === undefined || v === null) process.exit(4);
process.stdout.write(typeof v === "string" ? v : JSON.stringify(v));
'
json() {
    node -e "$JSON_JS" "$1" 2>/dev/null
}

# rest METHOD PATH [BODY]: sets R_STATUS, R_BODY and R_LOCATION.
rest() {
    local method="$1" path="$2" data="${3-}"
    rm -f "$WORK/body" "$WORK/headers"
    if [ -n "$data" ]; then
        R_STATUS=$(curl -s -m 10 -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" \
            -H 'Accept: application/json' -H 'Content-Type: application/json' --data-raw "$data" "$TASKS_URL$path")
    else
        R_STATUS=$(curl -s -m 10 -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' -X "$method" \
            -H 'Accept: application/json' "$TASKS_URL$path")
    fi
    R_BODY=$(cat "$WORK/body" 2>/dev/null)
    R_LOCATION=$(tr -d '\r' <"$WORK/headers" 2>/dev/null | sed -nE 's/^[Ll]ocation: *(.*)$/\1/p' | tail -1)
}

inspector() {
    npx -y "$INSPECTOR_PKG" --cli --transport http --server-url "$MCP_URL" "$@" 2>&1
}

# tools/list in the MCP 2026-07-28 era, where this server sends the tool annotations. The Inspector CLI 2.6.0
# speaks 2025-03-26, whose tools/list carries none.
modern_tools_list() {
    curl -s -m 10 -X POST "$MCP_URL" \
        -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
        -H 'MCP-Protocol-Version: 2026-07-28' -H 'Mcp-Method: tools/list' \
        -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}' |
        sed -E 's/^data: ?//'
}

# ok_if COMMAND...: 1 when the command succeeds, 0 otherwise.
ok_if() {
    if "$@"; then echo 1; else echo 0; fi
}

is_true() {
    [ "$1" = "true" ]
}

# --- Start ---------------------------------------------------------------------------------------

: >"$LOG"
start_server

# 0. The boot: the listeners it logged, and the ports the JVM listens on.
listener=$(boot_log | grep -F "Chappe listener 'default' started on http://127.0.0.1:$PORT/")
check "the application listener started on http://127.0.0.1:$PORT/" "$(boot_log | grep -F 'Chappe listener')" \
    "$(ok_if [ -n "$listener" ])"

CONSOLE_URL=$(boot_log | sed -nE 's#.*Vidocq dev console: (http://127\.0\.0\.1:[0-9]+/)$#\1#p' | tail -1)
check "the dev console logged its URL on the configured port, http://127.0.0.1:$DEVC/" \
    "console URL record: ${CONSOLE_URL:-(none)}" "$(ok_if [ "$CONSOLE_URL" = "http://127.0.0.1:$DEVC/" ])"

# The banner's context line is fitted to 80 columns, and what it gives up first, once the application name is
# there, is the dev console: that segment is a promise made before the bind, while the console's own URL
# record — the check just above — says where it really listens. Launched through the generated launcher the
# name is present, so the line reads "Java ... | dev (...) | mcp-tasks-server <version>" and carrying the
# console too would need 96 columns. What is checked here is therefore the guarantee that holds in every
# shape: the line fits, and it still names the launch mode, which is what makes the console's absence from it
# legitimate rather than a console that failed to start.
banner=$(boot_log | grep -E "^ Java ")
check "the banner fits 80 columns and names the launch mode" "${banner:-(none)}" \
    "$(ok_if [ -n "$banner" ] && [ "${#banner}" -le 81 ] && printf '%s' "$banner" | grep -q "| dev (")"

ports=$(listening_ports "$SERVER_PID" | tr '\n' ' ')
expected=$(printf '%s\n%s\n' "$PORT" "$DEVC" | sort -un | tr '\n' ' ')
check "the server listens on ports $PORT and $DEVC and nothing else (no debugger)" \
    "listening: ${ports:-(none)}" "$(ok_if [ "$ports" = "$expected" ])"

# 1. The MCP surface.
out=$(inspector --method tools/list)
v=$(printf '%s' "$out" | json 'j.tools.map(t => t.name).sort().join(",")')
check "tools/list holds get_task, list_open_tasks, search_tasks and task_statistics" "$out" \
    "$(ok_if [ "$v" = "get_task,list_open_tasks,search_tasks,task_statistics" ])"

out=$(modern_tools_list)
v=$(printf '%s' "$out" | json 'j.result.tools.length === 4 && j.result.tools.every(t => t.annotations.readOnlyHint === true)')
check "tools/list (MCP 2026-07-28) marks the 4 tools readOnlyHint" "$out" "$(ok_if is_true "$v")"

out=$(inspector --method resources/templates/list)
v=$(printf '%s' "$out" | json 'j.resourceTemplates.some(t => t.uriTemplate === "task://{id}")')
check "resources/templates/list holds task://{id}" "$out" "$(ok_if is_true "$v")"

out=$(inspector --method resources/list)
v=$(printf '%s' "$out" | json 'j.resources.some(r => r.uri === "tasks://summary")')
check "resources/list holds tasks://summary" "$out" "$(ok_if is_true "$v")"

out=$(inspector --method prompts/list)
v=$(printf '%s' "$out" | json 'j.prompts.map(p => p.name).sort().join(",")')
check "prompts/list holds plan_my_day and review_project" "$out" "$(ok_if [ "$v" = "plan_my_day,review_project" ])"

# 2. The seeded database, most urgent first.
rest GET /tasks
v=$(printf '%s' "$R_BODY" | json 'j.length + "|" + j.find(t => t.status === "OPEN").title')
check "GET /tasks answers the 8 seeded tasks, the overdue one first" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|8|Review the dev console redaction rules" ])"

# 3. A write over REST.
NONCE="e2e$(date +%s)x$$"
rest POST /tasks "{\"title\":\"E2E task $NONCE\",\"project\":\"e2e\",\"priority\":\"HIGH\"}"
ID=$(printf '%s' "$R_BODY" | json 'j.id')
check "POST /tasks answers 201 with the new task and its Location" "$R_STATUS $R_LOCATION $R_BODY" \
    "$(ok_if [ "$R_STATUS" = "201" ] && [ -n "$ID" ] && [ "${R_LOCATION%/tasks/$ID}" != "$R_LOCATION" ])"
ID="${ID:-0}"

# 4. The REST write is visible to the MCP reads.
out=$(inspector --method tools/call --tool-name search_tasks --tool-arg "query=$NONCE")
v=$(printf '%s' "$out" | json "inner(j.content[0].text).tasks.some(t => t.id === $ID)")
check "tools/call search_tasks finds the task written over REST" "$out" "$(ok_if is_true "$v")"

out=$(inspector --method resources/read --uri "task://$ID")
v=$(printf '%s' "$out" | json 'inner(j.contents[0].text).history.some(e => e.type === "CREATED")')
check "resources/read task://$ID holds its CREATED event" "$out" "$(ok_if is_true "$v")"

# 5. Complete it over REST; the MCP reads follow.
rest POST "/tasks/$ID/complete"
v=$(printf '%s' "$R_BODY" | json 'j.status')
check "POST /tasks/$ID/complete answers the task DONE" "$R_STATUS $R_BODY" "$(ok_if [ "$R_STATUS|$v" = "200|DONE" ])"

out=$(inspector --method tools/call --tool-name list_open_tasks --tool-arg project=e2e)
v=$(printf '%s' "$out" | json "inner(j.content[0].text).tasks.every(t => t.id !== $ID)")
check "tools/call list_open_tasks (project=e2e) no longer holds it" "$out" "$(ok_if is_true "$v")"

out=$(inspector --method tools/call --tool-name task_statistics --tool-arg project=e2e)
v=$(printf '%s' "$out" | json 'inner(j.content[0].text).done')
check "tools/call task_statistics (project=e2e) counts 1 done task" "$out" "$(ok_if [ "$v" = "1" ])"

# 6. Update, then delete; the history outlives the task.
rest PUT "/tasks/$ID" "{\"title\":\"E2E task $NONCE renamed\",\"project\":\"e2e\",\"priority\":\"LOW\"}"
v=$(printf '%s' "$R_BODY" | json 'j.title')
check "PUT /tasks/$ID replaces its title" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|E2E task $NONCE renamed" ])"

rest GET "/tasks/$ID/history"
v=$(printf '%s' "$R_BODY" | json 'j.map(e => e.type).join(",")')
check "GET /tasks/$ID/history holds CREATED, COMPLETED and UPDATED" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|CREATED,COMPLETED,UPDATED" ])"

rest DELETE "/tasks/$ID"
status_delete="$R_STATUS"
rest GET "/tasks/$ID"
check "DELETE /tasks/$ID answers 204, then GET answers 404" "DELETE $status_delete, GET $R_STATUS $R_BODY" \
    "$(ok_if [ "$status_delete|$R_STATUS" = "204|404" ])"

out=$(inspector --method tools/call --tool-name get_task --tool-arg "id=$ID")
v=$(printf '%s' "$out" | json 'j.isError === true && j.content[0].text.includes("No task with id")')
check "tools/call get_task on the deleted id answers isError" "$out" "$(ok_if is_true "$v")"

# 7. Rollback: completing an existing task and an unknown one, in one transaction, completes neither.
rest GET "/tasks?status=OPEN"
CAR=$(printf '%s' "$R_BODY" | json 'j.find(t => t.title === "Book the car service").id')
CAR="${CAR:-0}"
rest POST /tasks/complete "{\"ids\":[$CAR,999999]}"
v=$(printf '%s' "$R_BODY" | json 'j.message')
check "POST /tasks/complete [$CAR, 999999] answers 404, nothing was completed" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "404|No task with id 999999 — nothing was completed" ])"

rest GET "/tasks/$CAR"
v=$(printf '%s' "$R_BODY" | json 'j.status + "|" + (j.completedAt == null)')
check "task $CAR (Book the car service) is still OPEN, with no completedAt" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|OPEN|true" ])"

rest GET "/tasks/$CAR/history"
v=$(printf '%s' "$R_BODY" | json 'j.map(e => e.type).join(",")')
check "the history of task $CAR holds no COMPLETED event" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|CREATED" ])"

# 8. Validation: a clear message, never a stack trace.
rest POST /tasks '{"title":""}'
v=$(printf '%s' "$R_BODY" | json 'j.error + "|" + (j.message.length > 0)')
check "POST /tasks with an empty title answers 400 with a message and no stack trace" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "400|bad_request|true" ] && ! printf '%s' "$R_BODY" | grep -qE '\.java:[0-9]')"

# priority is a TaskPriority enum argument (langchain4j-cdi#298): a value outside LOW/MEDIUM/HIGH is rejected by
# the MCP server itself, before the tool ever runs, naming the argument and the accepted values.
out=$(inspector --method tools/call --tool-name list_open_tasks --tool-arg priority=URGENT)
v=$(printf '%s' "$out" | json 'j.error && j.error.message.includes("priority") && j.error.message.includes("LOW, MEDIUM, HIGH")')
check "tools/call list_open_tasks (priority=URGENT) is rejected as an invalid argument naming priority and the accepted values" "$out" \
    "$(ok_if is_true "$v" && ! printf '%s' "$out" | grep -qE '\.java:[0-9]')"

# 9. A prompt built on the current tasks.
out=$(inspector --method prompts/get --prompt-name plan_my_day)
v=$(printf '%s' "$out" | json 'j.messages[0].content.text.includes("Merge the MRTR batch pull request")')
check "prompts/get plan_my_day mentions \"Merge the MRTR batch pull request\"" "$out" "$(ok_if is_true "$v")"

# 10. The dev console: the pool panel, live, and never the password.
SNAPSHOT="$WORK/snapshot.json"
status=$(curl -s -m 10 -o "$SNAPSHOT" -w '%{http_code}' "${CONSOLE_URL:-http://127.0.0.1:$DEVC/}api/snapshot")
v=$(json '(g => g.name + "|" + g.values.find(x => x.key === "active").max + "|" + (g.values.find(x => x.key === "borrows").value > 0))((p => p.sample.groups.length === 1 && p.sample.groups[0])(j.panels.find(p => p.id === "mansart-pool")))' <"$SNAPSHOT")
check "the dev console's mansart-pool panel shows one @Default pool, max 8 active, borrows counted" \
    "HTTP $status; panel summary: ${v:-(no such panel)}" "$(ok_if [ "$status|$v" = "200|@Default|8|true" ])"

v=$(json 'j.panels.find(p => p.id === "mansart-pool").lines.some(l => l[0] === "@Default password" && l[1] === "configured")' <"$SNAPSHOT")
pw_snapshot=$(grep -cF -- "$PW" "$SNAPSHOT")
check "the dev console shows the password as 'configured', and its snapshot never holds it" \
    "'@Default password' row reads configured: ${v:-false}; lines of the snapshot holding the password: $pw_snapshot" \
    "$(ok_if is_true "$v" && [ "$pw_snapshot" = "0" ])"

# 11. Persistence: a task written before a restart is still there after it, and nothing is migrated again.
rest POST /tasks "{\"title\":\"E2E kept $NONCE\",\"project\":\"e2e\"}"
KEPT=$(printf '%s' "$R_BODY" | json 'j.id')
KEPT="${KEPT:-0}"
stop_server
ports=$(lsof -nP -iTCP:"$PORT" -iTCP:"$DEVC" -sTCP:LISTEN 2>/dev/null | awk 'NR > 1')
check "stopping the server frees ports $PORT and $DEVC" "${ports:-(free)}" "$(ok_if [ -z "$ports" ])"

start_server
# Vidocq logs version=(none) whenever Flyway applies nothing: Flyway only reports a target version for the
# migrations it ran. Flyway's own line gives the version the database is at.
migrated=$(boot_log | grep -F 'Migration done: datasource=default applied=0')
at_v2=$(boot_log | grep -F 'Current version of schema "PUBLIC": 2')
check "the restart migrates nothing: applied=0, the schema already at version 2" \
    "$(boot_log | grep -E 'Migration|schema "PUBLIC"')" "$(ok_if [ -n "$migrated" ] && [ -n "$at_v2" ])"

rest GET "/tasks/$KEPT"
v=$(printf '%s' "$R_BODY" | json 'j.title')
check "GET /tasks/$KEPT still answers the task written before the restart" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|E2E kept $NONCE" ])"

rest GET "/tasks/$ID/history"
v=$(printf '%s' "$R_BODY" | json 'j[j.length - 1].type')
check "the history of the deleted task $ID survives the restart, ending with DELETED" "$R_STATUS $R_BODY" \
    "$(ok_if [ "$R_STATUS|$v" = "200|DELETED" ])"

# --- Stop ----------------------------------------------------------------------------------------

stop_server
pw_log=$(grep -cF -- "$PW" "$LOG")
check "the server log never holds the password" "lines of $LOG holding the password: $pw_log" \
    "$(ok_if [ "$pw_log" = "0" ])"

ports=$(lsof -nP -iTCP:"$PORT" -iTCP:"$DEVC" -sTCP:LISTEN 2>/dev/null | awk 'NR > 1')
check "after the stop, nothing listens on ports $PORT and $DEVC" "${ports:-(free)}" "$(ok_if [ -z "$ports" ])"

# --- Summary -------------------------------------------------------------------------------------

echo
echo "Summary: $PASS passed, $FAIL failed (of $((PASS + FAIL)))."
if [ "$FAIL" -ne 0 ]; then
    exit 1
fi
exit 0
