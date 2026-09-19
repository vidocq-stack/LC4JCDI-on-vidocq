# mcp-tasks-server

A task tracker hosted on Vidocq: a REST API writes the tasks to an H2 database, and a langchain4j-cdi
[MCP server](../README.md) lets an assistant read them. Each task has a title, a description, a project, a status
(open or done), a priority, a due date and timestamps, and every change leaves an event in its history. The code
lives under `src/main/java/io/vidocq/tools/lc4jcdi/mcptasks/`.

| Piece | What it does here |
|---|---|
| **Mansart pool** | The `@Default` `DataSource`: a connection pool on an H2 database *file*, configured by `vidocq.pool.*` in `vidocq.properties`. |
| **Mansart Data** | `TaskRepository` and `TaskEventRepository`, Jakarta Data interfaces whose implementations Mansart generates at compile time: derived queries (`findByStatusOrderByDueDateAsc`), a JDQL `LOWER … LIKE` search, and a set-based JDQL `UPDATE` that renames a project. |
| **Mansart transactions** | `TaskService`, whose public methods are `@Transactional`: a task row and its history event commit together, and completing several tasks at once is all or nothing. |
| **Flyway** | Creates the two tables and seeds eight tasks at boot (`src/main/resources/db/migration`). |
| **Cassini REST** | `TaskResource` (`/tasks`) and `ProjectResource` (`/projects`): every write, and the simple reads. |
| **MCP** | `TaskTools` (4 read-only tools), `TaskResources` (`task://{id}`, `tasks://summary`) and `TaskPrompts` (`plan_my_day`, `review_project`): reads only. |
| **Dev console** | A page on its own port, in a dev launch only, whose *Mansart pools* tab shows this pool live. |

```
curl ──> Cassini /tasks, /projects ──> TaskService (@Transactional) ──┐
                                   └─> TaskQueries ───────────────────┤
MCP client ──> /mcp: tools, resources, prompts ──> TaskQueries ───────┤
                                                                      v
                                     TaskRepository, TaskEventRepository (Mansart Data)
                                                                      │
                                                                      v
                                     Mansart pool (@Default DataSource) ──> H2 file target/h2/tasks.mv.db
```

`TaskRules` holds every validation and ordering rule and `TaskPromptText` builds the prompt texts. Neither has a CDI
or MCP annotation, so both are covered by plain JUnit tests.

## Ports

Everything this module opens stays in 18090-18099, on the loopback address:

| Port | What | Where it is set |
|---|---|---|
| 18090 | the application: REST at `/tasks` and `/projects`, MCP at `/mcp` | `vidocq.chappe.listener.default.port` in `vidocq.properties` |
| 18091 | the debugger (JDWP) of `vidocq:dev` | `vidocq.dev.debugPort` in `pom.xml` |
| 18092 | the dev console, in a dev launch only | `vidocq.devconsole.port` in `vidocq.properties` |
| 18093, 18094 | the application and the dev console of `../test-tasks.sh` | `TASKS_URL`, `DEVCONSOLE_PORT` |

Not 8080 and 8888, the defaults of the listener and of the console, so this module runs next to
`mcp-time-server` or another server on 8080. The listener is bound on `127.0.0.1` only, because the REST API writes
to the database without authentication. H2 opens no port: the URL has no `AUTO_SERVER`, and no H2 console or TCP
server is started.

## The data

The database is the file `mcp-tasks-server/target/h2/tasks.mv.db`. `vidocq:dev` and `run.sh` both start the JVM in
the module directory, so `./target/h2/tasks` in `vidocq.pool.url` names the same file in both launches. It
survives a `vidocq:dev` reload and a restart, and `mvn clean` deletes it. To start again from the seed data:

```bash
rm -rf mcp-tasks-server/target/h2
```

The seed's dates are relative to the day the database is created: on that day, "Review the dev console redaction
rules" is two days overdue and "Merge the MRTR batch pull request" is due today. H2 stores the user and password
of `vidocq.properties` when it creates the database, so after changing them, delete `target/h2` as well.

## Build and run

From the repository root, on JDK 25:

```bash
mvn -nsu -B clean verify -pl mcp-tasks-server -am
```

`-nsu` (no snapshot updates) matters while the dev console comes from a local Vidocq install: a newer remote
snapshot of the pool extension, published without the console's panel, would otherwise replace the local one
(see [the snapshot dependencies](../README.md#the-snapshot-dependencies)).

Run the distribution:

```bash
cd mcp-tasks-server && ./run.sh
```

The server listens on `http://127.0.0.1:18090`. `run.sh` also sets where Flyway reads the scripts ("Why the code
looks like this" below).

Or run it in dev mode, which recompiles and reloads on every change:

```bash
cd mcp-tasks-server && VIDOCQ_MIGRATION_LOCATIONS=filesystem:src/main/resources/db/migration mvn -nsu process-classes vidocq:dev -Dvidocq.chappe.listener.default.port=18090 -Dvidocq.dev.debugPort=18091 -Dvidocq.devconsole.port=18092
```

The three ports are already this module's defaults; the command passes them anyway, so that it shows which ports
the launch opens. Before using the server, check the log: `Debug agent (JDWP) on port 18091`,
`Vidocq dev console: http://127.0.0.1:18092/` and `Chappe listener 'default' started on http://127.0.0.1:18090/`.

## The REST API

All JSON, on `http://127.0.0.1:18090`. An invalid field is a 400 and an unknown id a 404, each with a body
`{"error": "bad_request" | "not_found", "message": "..."}` and never a stack trace.

```bash
# Every task, most urgent first: overdue, then higher priority, then earlier due date (none last).
curl -s http://127.0.0.1:18090/tasks
# Filtered by status (OPEN or DONE) and project.
curl -s 'http://127.0.0.1:18090/tasks?status=OPEN&project=lc4jcdi'

# Create: 201, the task, and its Location. On a fresh database, the first task created gets id 9.
curl -si -X POST http://127.0.0.1:18090/tasks -H 'Content-Type: application/json' \
    -d '{"title":"Try the dev console","project":"vidocq","priority":"HIGH","dueDate":"2026-12-31"}'

# Update: a full replace of title, description, project, priority and dueDate. A field left out is cleared.
curl -s -X PUT http://127.0.0.1:18090/tasks/9 -H 'Content-Type: application/json' \
    -d '{"title":"Try the dev console pool panel","project":"vidocq","priority":"MEDIUM"}'

# Complete, then reopen. Both are idempotent: a second call changes nothing and writes no event.
curl -s -X POST http://127.0.0.1:18090/tasks/9/complete
curl -s -X POST http://127.0.0.1:18090/tasks/9/reopen

# The history of a task, oldest first.
curl -s http://127.0.0.1:18090/tasks/9/history

# Complete several tasks in ONE transaction, all or nothing. Task 6 exists, 999999 does not: the answer is a 404,
# "No task with id 999999 — nothing was completed", and task 6 is still OPEN, with no COMPLETED event.
curl -s -X POST http://127.0.0.1:18090/tasks/complete -H 'Content-Type: application/json' -d '{"ids":[6,999999]}'
curl -s http://127.0.0.1:18090/tasks/6
curl -s http://127.0.0.1:18090/tasks/6/history

# Delete: 204. The history stays, ending with a DELETED event.
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE http://127.0.0.1:18090/tasks/9
curl -s http://127.0.0.1:18090/tasks/9/history

# The counts per project, and a rename: one JDQL UPDATE plus one UPDATED event per moved task, in one transaction.
curl -s http://127.0.0.1:18090/projects
curl -s -X POST http://127.0.0.1:18090/projects/home/rename -H 'Content-Type: application/json' \
    -d '{"newName":"personal"}'
```

A body that is not JSON, or not a JSON object, never reaches these methods: Cassini answers `500 text/plain` with
the parser's message, and `415` for a body sent without `Content-Type: application/json`.

## MCP

The MCP endpoint is `http://127.0.0.1:18090/mcp`. It only reads: every tool is marked `readOnlyHint`,
`idempotentHint` and `openWorldHint=false`, and answers JSON text whose root is an object.

| Kind | Name | Arguments | Answers |
|---|---|---|---|
| Tool | `list_open_tasks` | `project`, `priority` (the minimum: `LOW`, `MEDIUM` or `HIGH`), `dueBefore` (`yyyy-MM-dd`, inclusive), `limit` (1 to 100, 20 by default); all optional | `{"count": n, "tasks": [...]}`, most urgent first |
| Tool | `search_tasks` | `query` (at least 2 characters), `includeDone` (false by default), `limit` | the tasks whose title or description holds the query, ignoring case |
| Tool | `task_statistics` | `project`, optional | total, open, done, overdue, due today, due in the next 7 days, open tasks per priority, counts per project |
| Tool | `get_task` | `id` | the task and its history; `isError` for an unknown id |
| Resource template | `task://{id}` | | the task and its history; a JSON-RPC error for an unknown id |
| Resource | `tasks://summary` | | the statistics of every project |
| Prompt | `plan_my_day` | `project`, `hours` (6 by default); both optional | asks the model to plan the day: overdue tasks first, breaks, what does not fit deferred with a reason |
| Prompt | `review_project` | `project` | asks for a status summary, the risks, and the next three actions |

Try each of them with the [MCP Inspector CLI](https://www.npmjs.com/package/@modelcontextprotocol/inspector):

```bash
inspect() { npx -y @modelcontextprotocol/inspector@2.6.0 --cli --transport http --server-url http://127.0.0.1:18090/mcp "$@"; }

inspect --method tools/list
inspect --method tools/call --tool-name list_open_tasks --tool-arg priority=MEDIUM --tool-arg limit=5
inspect --method tools/call --tool-name search_tasks --tool-arg query=release --tool-arg includeDone=true
inspect --method tools/call --tool-name task_statistics --tool-arg project=lc4jcdi
inspect --method tools/call --tool-name get_task --tool-arg id=3
inspect --method resources/templates/list
inspect --method resources/read --uri task://3
inspect --method resources/list
inspect --method resources/read --uri tasks://summary
inspect --method prompts/list
inspect --method prompts/get --prompt-name plan_my_day --prompt-args hours=4
inspect --method prompts/get --prompt-name review_project --prompt-args project=lc4jcdi
```

The Inspector CLI speaks MCP 2025-03-26, where `tools/list` carries no tool annotations: this server sends them
in the 2026-07-28 era only.

To connect Claude Code (not tried here):

```bash
claude mcp add --transport http tasks http://127.0.0.1:18090/mcp
```

A scenario that shows both sides: add an overdue task over REST, then ask the assistant to plan your day, through
the `plan_my_day` prompt or in plain words. The task you just wrote is among the overdue ones, which the prompt asks
the model to take first.

```bash
curl -s -X POST http://127.0.0.1:18090/tasks -H 'Content-Type: application/json' \
    -d "{\"title\":\"Send the invoice\",\"project\":\"home\",\"priority\":\"HIGH\",\"dueDate\":\"$(date -v-1d +%F 2>/dev/null || date -d yesterday +%F)\"}"
```

## The dev console

In a dev launch, the console serves `http://127.0.0.1:18092/`: under `vidocq:dev`, or with `run.sh` and
`JAVA_OPTS="-Dvidocq.launch.mode=dev"`. It is off in any other launch of `run.sh`.

```bash
cd mcp-tasks-server && JAVA_OPTS="-Dvidocq.launch.mode=dev" ./run.sh
```

Its *Mansart pools* tab has one card, `@Default`:

- live values: the connections `active` and `idle` out of 8, `waiting`, the `borrows` and `timeouts` so far, the
  `leaks` (on, since `leakDetectionThreshold` is `PT30S`), and the mean borrow time; charts of the connections and
  of the borrows per second;
- the boot facts: the URL without credentials, the user, `min idle 1 (boot only), max 8`, the timeouts, the checks
  `validation PERIODIC PT1S, leaks after PT30S`, and `@Default password configured`. The password itself never
  appears, neither on the page nor in `/api/snapshot`.

The counters show the transactions at work. Measured on `/api/snapshot`: `POST /tasks` writes two rows through two
repositories and adds **one** borrow; `POST /tasks/complete` with two ids reads each task, updates it and writes its
event, and adds one borrow too: the transaction keeps one connection from its first statement to its commit. Each
`GET` adds one. To watch the chart move:

```bash
for i in $(seq 1 100); do curl -s -o /dev/null http://127.0.0.1:18090/tasks; sleep 0.1; done
```

Under `vidocq:dev`, look for the console's URL record, `Vidocq dev console: http://127.0.0.1:18092/`, rather than
the banner. The banner's context line holds 80 columns, and next to this module's name there is room for neither
`devconsole :18092` nor the debugger's address: it reads `Java 25.0.3+9-LTS | dev (profile dev) | i.v.t.l.mcptasks
0.1.0-SNAPSHOT` (measured), and the debugger has a log line of its own, `Vidocq debugger: attach to *:18091`. A
`run.sh` dev launch shows the segment: `Java 25.0.3+9-LTS | dev (vidocq.launch.mode) | devconsole :18092`.

## Tests

```bash
mvn -nsu -B verify -pl mcp-tasks-server -am     # from the repository root
./test-tasks.sh --start                          # from the repository root, after a build
```

- **Unit tests** (97), plain JUnit on the class path, without the Vidocq runtime: `TaskRules` and `TaskStats`; the
  repositories, `TaskService` and `TaskQueries` against Flyway and a standalone Mansart on an in-memory H2; the
  REST resources and the MCP beans called directly on the seeded database; `McpResults`, `TaskJson` and
  `TaskPromptText`.
- **`../test-tasks.sh --start`** boots the packaged server through `run.sh`, as a dev launch on ports 18093 and
  18094 and a database file of its own, and checks it from the outside with `curl` and the MCP Inspector CLI: the
  ports it listens on and nothing else, the MCP surface, a REST write read back over MCP, the all-or-nothing
  rollback, validation errors without stack traces, a prompt, the dev console's pool panel and its password
  redaction, and the data surviving a restart. It deletes its database file and stops the server on exit, pass or
  fail. `TASKS_URL` and `DEVCONSOLE_PORT` choose other ports; it never uses 8080 or 8888.

## Running from an IDE

Run or debug the shared `McpTasksServerApp` configuration: `.run/McpTasksServerApp.run.xml` at the repository root
for IntelliJ IDEA, `McpTasksServerApp.launch` in this directory for Eclipse. Both start the JVM in this directory,
where `./target/h2/tasks` is, and pass `--add-modules ALL-MODULE-PATH`.

That option was measured from a terminal, with the IDE-style launch of the
[root README](../README.md#running-from-an-ide) (`java -p target/classes:<runtime jars> -m ...`, after
`mvn package`). Without it, the boot stops in Flyway with
`NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper`: nothing the application requires reads
Jackson, so the JVM does not resolve it. `mansart-transactions-jdbc` is in the same case: `mansart-data-cdi` only
has a `requires static` on it, which does not resolve it either. With `--add-modules ALL-MODULE-PATH`, the
application boots, Flyway finds its scripts on the class path (no `VIDOCQ_MIGRATION_LOCATIONS` is needed in that
launch), every MCP call answers, and the bulk completion rolls back.
Nobody has run either configuration inside an IDE yet, and an IDE may split the jars between the module path and
the class path differently.

Unlike `mcp-time-server`, the *Before launch* `vidocq:generate` step cannot repair this module's bean index after an
incremental IDE build: see the last point of "Why the code looks like this".

## Why the code looks like this

- **`mansart-transactions-jdbc` is a dependency** (`pom.xml`). `mansart-data-cdi` enlists a repository's connection
  in the current JTA transaction only when that artifact is on the module path. It declares it optional, and none of
  the Mansart extensions brings it. Measured under `vidocq:dev`: without it, `POST /tasks/complete {"ids":[6,999999]}`
  still answers 404, but task 6 is DONE and its `COMPLETED` event is committed; with it, task 6 stays OPEN and has no
  such event.
- **The tools return `McpResults`, the prompts and resources a `String`.** `McpResults` implements the `org.mcpjava`
  `ToolResponse` and `TextContent` interfaces with records. The API's static factories (`ToolResponse.ofText`,
  `ofError`, `builder`, `TextContent.of`, `PromptResponse.of`) look up an `McpServerSPI` provider, and that lookup
  fails with `No McpServerSPI implementation found` in an IDE-style launch (root README, "Known limitation"). The
  MCP server serializes any `ToolResponse` by its interface, so these records go on the wire as the factories'
  objects would, in every launch.
- **Tool arguments are `String`, `Integer`, `Long` or `Boolean`, with the defaults applied in Java.** The MCP server
  converts no enum and no date, and never applies `@ToolArg(defaultValue)`: an omitted argument arrives as `null`.
  The Inspector CLI sends `limit=5` as a JSON number and `includeDone=true` as a JSON boolean, which bind; a client
  that sends `"5"` as a string gets `-32603 Internal error`.
- **Flyway reads the scripts from the source tree under `vidocq:dev` and `run.sh`.** Its default,
  `classpath:db/migration`, finds nothing in those launches ("No migrations found", `applied=0 version=(none)` on a
  fresh database): the application module lives in Vauban's child layer, while Flyway scans with the class loader of
  the migration extension, in the boot layer. The location has to be `filesystem:src/main/resources/db/migration`,
  but in Vidocq 0.4.0-SNAPSHOT the key `vidocq.migration.locations`, in `vidocq.properties` or as a `-D`, fails the
  boot with a `StringIndexOutOfBoundsException` in `MigrationExtension`. The environment variable
  `VIDOCQ_MIGRATION_LOCATIONS` sets the same key without that failure: `run.sh` exports it, and the `vidocq:dev`
  command above passes it.
- **After a restart, the log reads `Migration done: datasource=default applied=0 version=(none)`.** That is not the
  failure above: Flyway reports no target version when it applies nothing. Its own line just before, `Current version
  of schema "PUBLIC": 2`, gives the version.
- **`vidocq:generate` no longer rescans this module's classes.** The Mansart processors (`mansart-data-cdi`,
  `mansart-transactions-cdi`) are build-compatible extensions, and they leave `META-INF/vauban-bce-processed` in
  `target/classes`. After that, `vidocq:generate` still indexes the MCP server jar, but does not rebuild the bean index
  of this module, so it cannot repair an index that an incremental IDE build cut down. *Rebuild Project*, or a Maven
  build, can.
