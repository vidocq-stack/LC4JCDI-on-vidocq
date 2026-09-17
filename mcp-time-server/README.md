# mcp-time-server

A langchain4j-cdi [MCP server](../README.md) hosted on Vidocq, demonstrating four kinds of MCP feature over one
small domain: IANA time zones. Beans live under
`src/main/java/io/vidocq/tools/lc4jcdi/mcptimeserver/`.

| Kind | Name | Bean / method |
|---|---|---|
| Tool | `current_time` | `TimeTools.currentTime(zone)` — current date-time, optional IANA `zone` (defaults to UTC). Marked `readOnlyHint=true, idempotentHint=false`. |
| Tool | `convert_time` | `TimeTools.convertTime(localDateTime, fromZone, toZone)` — converts an ISO-8601 local date-time between IANA zones. Returns a clean `ToolResponse.ofError(...)` (not a stack trace) for an unknown zone. |
| Resource template | `time://zone/{zone}` | `TimeResource.timeInZone(zone)` — the current time in that zone, as a resource. |
| Prompt | `plan_meeting` | `MeetingPrompts.planMeeting(zones, durationMinutes)` — asks an assistant to propose a meeting slot fitting everyone's working hours. |

`TimeService` holds the pure time logic (zone validation, conversion) with no CDI/MCP annotations, so it is
covered by plain JUnit tests in `src/test/java` — no container needed.

## Build, run, test

From the repository root:

```bash
export JAVA_HOME=/path/to/jdk-25
mvn -B clean verify -pl mcp-time-server -am
./test-mcp.sh --start                   # starts run.sh, runs every check, stops the server
(cd mcp-time-server && ./run.sh)        # or run the server yourself: http://localhost:8080/mcp
```

See the [root README](../README.md) for prerequisites, the snapshot dependencies (langchain4j-cdi
`1.4.0-SNAPSHOT`, Vidocq `0.4.0-SNAPSHOT`), and the Vidocq host-level workarounds this module relies on
(`-Avauban.validation=false` and `run.sh`'s jar re-layering).

## Running from an IDE

- **IntelliJ IDEA:** run or debug the shared `McpTimeServerApp` configuration (`.run/McpTimeServerApp.run.xml` at
  the repository root). It runs on JDK 25 (`temurin-25`), builds the module, runs `vidocq:generate` as a *Before
  launch* step, then starts the server.
- **Eclipse:** import the Maven project and use `McpTimeServerApp.launch` in this directory. The root POM turns on
  JDT annotation processing (`m2e.apt.activation=jdt_apt`). Nobody has tried this in Eclipse yet.

Launched from an IDE, `convert_time` and `plan_meeting` currently fail with `No McpServerSPI implementation found`:
`./test-mcp.sh` passes 4 of its 7 checks, where `run.sh` passes all 7. The fix is upstream: a `provides` clause for
`org.mcpjava.server.spi.McpServerSPI` in the `module-info.java` of langchain4j-cdi's MCP server. The
[root README](../README.md), "Running from an IDE", explains why, and covers the other limits: Debug and HotSwap,
multi-module applications, and builds without a launch.

## Try a tool by hand

```bash
npx -y @modelcontextprotocol/inspector --cli --transport http --server-url http://localhost:8080/mcp \
    --method tools/call --tool-name current_time --tool-arg zone=Asia/Tokyo
```
