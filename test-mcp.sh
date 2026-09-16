#!/bin/bash
# End-to-end check of the mcp-time-server example against a real MCP client: the MCP Inspector CLI
# (@modelcontextprotocol/inspector, run via `npx -y`, currently 2.6.0).
#
# Usage:
#   ./test-mcp.sh [url] [--start]
#   MCP_URL=http://host:port/mcp ./test-mcp.sh --start
#
# --start builds nothing: it starts the already-built server via mcp-time-server/run.sh on the port of
# MCP_URL, waits until a real MCP initialize request succeeds, runs every check below, and always stops the
# server on exit (trap), pass or fail. It refuses to start if that port is already taken.
# Without --start, it tests whatever already serves MCP_URL (run.sh, an IDE, ...), after a pre-flight
# request that explains a missing or broken endpoint once instead of failing every check.
#
# Exits non-zero if any check fails; always prints a PASS/FAIL summary.
set -u

INSPECTOR_PKG="@modelcontextprotocol/inspector@2.6.0"
MCP_URL="${MCP_URL:-http://localhost:8080/mcp}"
START=0
SERVER_PID=""

for arg in "$@"; do
    case "$arg" in
        --start) START=1 ;;
        http://* | https://*) MCP_URL="$arg" ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

BASE=$(cd "$(dirname "$0")" && pwd)

# --- Prerequisites -----------------------------------------------------------------------------

if ! command -v npx >/dev/null 2>&1; then
    echo "FAIL: npx not found on PATH. Install Node.js (for the MCP Inspector CLI) and re-run." >&2
    exit 1
fi

# --- MCP probe -----------------------------------------------------------------------------------
#
# Any HTTP answer is not enough to call the server ready: a Vidocq server whose Vauban bean index does not
# know McpEndpoint still listens, and answers 404 to everything. So readiness and the pre-flight both send a
# real MCP request (a 2025-03-26 initialize) and require HTTP 200.

mcp_probe() {
    curl -s -o /dev/null -m 3 -w '%{http_code}' -X POST "$MCP_URL" \
        -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
        -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test-mcp.sh","version":"1"}}}'
}

explain_probe_failure() {
    case "$1" in
        000)
            echo "FAIL: nothing is listening at $MCP_URL." >&2
            echo "      Start the server (mcp-time-server/run.sh), or let this script do it with --start." >&2
            ;;
        404)
            echo "FAIL: a server answers at $MCP_URL but exposes no MCP endpoint there (HTTP 404)." >&2
            echo "      Most likely it runs from classes compiled by an IDE. IDE builds skip the vidocq:generate" >&2
            echo "      goal, so the Vauban bean index never learns about McpEndpoint and no route is registered." >&2
            echo "      Fix: run 'mvn process-classes' then restart it, or in IntelliJ enable" >&2
            echo "      Settings > Build, Execution, Deployment > Build Tools > Maven > Runner >" >&2
            echo "      'Delegate IDE build/run actions to Maven'. See README.md, 'Running from an IDE'." >&2
            ;;
        *)
            echo "FAIL: $MCP_URL answered HTTP $1 to an MCP initialize request." >&2
            ;;
    esac
}

# --- Optionally start the server ---------------------------------------------------------------

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
}

if [ "$START" -eq 1 ]; then
    # The JDK only matters when this script starts the server itself; testing a server you started
    # elsewhere (an IDE, another terminal) needs nothing but npx and curl.
    JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
    JAVA_BIN="${JAVA_BIN:-java}"
    if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
        echo "FAIL: no java executable found (checked \"$JAVA_BIN\"). Set JAVA_HOME to a JDK 25+ install." >&2
        exit 1
    fi
    JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | head -1 | sed -nE 's/.*version "([0-9]+).*/\1/p')
    if ! [ "${JAVA_MAJOR:-0}" -ge 25 ] 2>/dev/null; then
        echo "FAIL: JDK 25 or newer is required to run this server (Vidocq 0.3.0 ships Java 25 class files)." >&2
        echo "      Found: $("$JAVA_BIN" -version 2>&1 | head -1)" >&2
        echo "      Set JAVA_HOME to a JDK 25+ install, e.g. JAVA_HOME=\$(sdk home java 25-tem)." >&2
        exit 1
    fi

    # The server listens on the port of MCP_URL, so --start can run next to a server already on 8080.
    PORT=$(printf '%s' "$MCP_URL" | sed -nE 's#^[a-z]+://[^/:]+:([0-9]+).*#\1#p')
    PORT="${PORT:-80}"
    if command -v lsof >/dev/null 2>&1 && lsof -ti "tcp:$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        HOLDER=$(lsof -ti "tcp:$PORT" -sTCP:LISTEN | head -1)
        echo "FAIL: port $PORT is already in use by pid $HOLDER ($(ps -o comm= -p "$HOLDER" 2>/dev/null))." >&2
        echo "      --start would not be testing its own server. Either stop that process, drop --start to" >&2
        echo "      test the server already running, or start on another port:" >&2
        echo "      MCP_URL=http://localhost:8081/mcp $0 --start" >&2
        exit 1
    fi

    trap stop_server EXIT
    RUN_SH="$BASE/mcp-time-server/run.sh"
    if [ ! -x "$RUN_SH" ]; then
        echo "FAIL: $RUN_SH not found or not executable." >&2
        exit 1
    fi
    echo "Starting server via $RUN_SH on port $PORT ..."
    JAVA_OPTS="-Dvidocq.chappe.listener.default.port=$PORT ${JAVA_OPTS:-}" \
        "$RUN_SH" >"$BASE/mcp-time-server-run.log" 2>&1 &
    SERVER_PID=$!

    echo -n "Waiting for an MCP server at $MCP_URL..."
    STATUS=000
    for _ in $(seq 1 60); do
        STATUS=$(mcp_probe)
        [ "$STATUS" = "200" ] && break
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo
            echo "FAIL: server process exited before the endpoint came up. Log:" >&2
            cat "$BASE/mcp-time-server-run.log" >&2
            exit 1
        fi
        echo -n "."
        sleep 1
    done
    echo
    if [ "$STATUS" != "200" ]; then
        explain_probe_failure "$STATUS"
        echo "      Server log:" >&2
        cat "$BASE/mcp-time-server-run.log" >&2
        exit 1
    fi
    echo "Server is up."
fi

# --- Pre-flight ----------------------------------------------------------------------------------
# One clear diagnosis instead of the same transport error repeated by every check below.

STATUS=$(mcp_probe)
if [ "$STATUS" != "200" ]; then
    explain_probe_failure "$STATUS"
    exit 1
fi

# --- Checks --------------------------------------------------------------------------------------

PASS=0
FAIL=0

inspector() {
    npx -y "$INSPECTOR_PKG" --cli --transport http --server-url "$MCP_URL" "$@" 2>&1
}

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

# 1. tools/list contains both tools.
out=$(inspector --method tools/list)
if echo "$out" | grep -q '"name": "current_time"' && echo "$out" | grep -q '"name": "convert_time"'; then
    ok=1
else
    ok=0
fi
check "tools/list contains current_time and convert_time" "$out" "$ok"

# 2. tools/call current_time with zone=Europe/Paris returns a date-time.
out=$(inspector --method tools/call --tool-name current_time --tool-arg zone=Europe/Paris)
if echo "$out" | grep -Eq '[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}' && echo "$out" | grep -q 'Europe/Paris'; then
    ok=1
else
    ok=0
fi
check "tools/call current_time (zone=Europe/Paris) returns a date-time in that zone" "$out" "$ok"

# 3. tools/call convert_time returns the expected converted value for a fixed input.
#    2026-01-15T12:00:00 America/New_York (EST, UTC-5) -> Europe/Paris (CET, UTC+1): +6h = 18:00.
out=$(inspector --method tools/call --tool-name convert_time \
    --tool-arg localDateTime=2026-01-15T12:00:00 --tool-arg fromZone=America/New_York --tool-arg toZone=Europe/Paris)
if echo "$out" | grep -qF '2026-01-15T18:00:00+01:00[Europe/Paris]'; then
    ok=1
else
    ok=0
fi
check "tools/call convert_time (America/New_York -> Europe/Paris) returns the expected value" "$out" "$ok"

# 4. tools/call convert_time with an unknown zone reports a tool error, not a stack trace.
#    The Inspector CLI exits non-zero when a tool returns isError:true, so this check looks at content only.
out=$(inspector --method tools/call --tool-name convert_time \
    --tool-arg localDateTime=2026-01-15T12:00:00 --tool-arg fromZone=Not/AZone --tool-arg toZone=Europe/Paris)
if echo "$out" | grep -q '"isError": true' && echo "$out" | grep -q "Unknown IANA time zone 'Not/AZone'" \
    && ! echo "$out" | grep -qi 'stacktrace\|\.java:[0-9]'; then
    ok=1
else
    ok=0
fi
check "tools/call convert_time (unknown zone) reports a clear tool error" "$out" "$ok"

# 5. resources/templates/list contains the time:// template.
out=$(inspector --method resources/templates/list)
if echo "$out" | grep -qF 'time://zone/{zone}'; then
    ok=1
else
    ok=0
fi
check "resources/templates/list contains time://zone/{zone}" "$out" "$ok"

# 6. resources/read of time://zone/Asia/Tokyo returns content.
out=$(inspector --method resources/read --uri time://zone/Asia/Tokyo)
if echo "$out" | grep -Eq '[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}' && echo "$out" | grep -q 'Asia/Tokyo'; then
    ok=1
else
    ok=0
fi
check "resources/read time://zone/Asia/Tokyo returns the current time there" "$out" "$ok"

# 7. prompts/get plan_meeting returns a message mentioning the zones.
out=$(inspector --method prompts/get --prompt-name plan_meeting \
    --prompt-args zones=Europe/Paris,Asia/Tokyo durationMinutes=30)
if echo "$out" | grep -q 'Europe/Paris' && echo "$out" | grep -q 'Asia/Tokyo'; then
    ok=1
else
    ok=0
fi
check "prompts/get plan_meeting mentions the requested zones" "$out" "$ok"

# --- Summary -------------------------------------------------------------------------------------

echo
echo "Summary: $PASS passed, $FAIL failed (of $((PASS + FAIL)))."
if [ "$FAIL" -ne 0 ]; then
    exit 1
fi
exit 0
