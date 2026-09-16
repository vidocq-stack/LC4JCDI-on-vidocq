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
cd mcp-time-server && ./run.sh &        # http://localhost:8080/mcp
cd .. && ./test-mcp.sh --start
```

See the [root README](../README.md) for prerequisites, the `1.4.0-SNAPSHOT` dependency note, and the Vidocq
host-level workarounds this module relies on (`-Avauban.validation=false` and `run.sh`'s jar re-layering).

## Try a tool by hand

```bash
npx -y @modelcontextprotocol/inspector --cli --transport http --server-url http://localhost:8080/mcp \
    --method tools/call --tool-name current_time --tool-arg zone=Asia/Tokyo
```
