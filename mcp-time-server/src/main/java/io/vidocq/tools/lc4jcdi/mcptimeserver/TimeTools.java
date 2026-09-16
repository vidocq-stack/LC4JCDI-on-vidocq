package io.vidocq.tools.lc4jcdi.mcptimeserver;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;

/**
 * MCP tools for reading and converting the current time across IANA time zones.
 *
 * <p>Discovered as CDI beans by {@code vidocq:generate}'s {@code scanDependencies} (the MCP endpoint that hosts
 * them lives in the {@code langchain4j-cdi-mcp-server} dependency jar), so it must be {@link ApplicationScoped}
 * like every other MCP bean.
 */
@ApplicationScoped
public class TimeTools {

    /** Creates a new instance; CDI requires a no-argument constructor. */
    public TimeTools() {}

    /**
     * Returns the current date-time in the given IANA zone, defaulting to UTC.
     *
     * <p>Marked read-only and non-idempotent: it never changes server state, but two calls a moment apart return
     * different values.
     *
     * @param zone the IANA time zone id, e.g. {@code "Europe/Paris"}; defaults to {@code "UTC"} when omitted
     * @return the current date-time, ISO-8601 formatted with zone offset and id
     */
    @Tool(
            name = "current_time",
            description = "Returns the current date-time, optionally in a given IANA time zone (defaults to UTC).",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = false))
    public String currentTime(
            @ToolArg(
                            name = "zone",
                            description = "IANA time zone id, e.g. 'Europe/Paris'. Defaults to UTC.",
                            required = false,
                            defaultValue = "UTC")
                    String zone) {
        return TimeService.currentTime(zone);
    }

    /**
     * Converts a local date-time from one IANA zone to another.
     *
     * @param localDateTime the local date-time to convert, ISO-8601, e.g. {@code "2026-09-16T10:30:00"}
     * @param fromZone the IANA zone id the date-time is expressed in
     * @param toZone the IANA zone id to convert to
     * @return a successful {@link ToolResponse} with the converted date-time, or an error response with a clear
     *     message (not a stack trace) when either zone is not a known IANA id or the date-time cannot be parsed
     */
    @Tool(
            name = "convert_time",
            description = "Converts an ISO-8601 local date-time from one IANA time zone to another.",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = true))
    public ToolResponse convertTime(
            @ToolArg(name = "localDateTime", description = "ISO-8601 local date-time, e.g. '2026-09-16T10:30:00'.")
                    String localDateTime,
            @ToolArg(name = "fromZone", description = "IANA time zone id the date-time is expressed in.")
                    String fromZone,
            @ToolArg(name = "toZone", description = "IANA time zone id to convert to.") String toZone) {
        try {
            return ToolResponse.ofText(TimeService.convertTime(localDateTime, fromZone, toZone));
        } catch (IllegalArgumentException e) {
            return ToolResponse.ofError(e.getMessage());
        }
    }
}
