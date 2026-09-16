package io.vidocq.tools.lc4jcdi.mcptimeserver;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;

/**
 * MCP resource template exposing the current time in a given IANA zone as a readable resource, e.g.
 * {@code time://zone/Asia/Tokyo}.
 */
@ApplicationScoped
public class TimeResource {

    /** Creates a new instance; CDI requires a no-argument constructor. */
    public TimeResource() {}

    /**
     * Returns the current date-time in the zone named by the {@code {zone}} template variable.
     *
     * @param zone the IANA time zone id taken from the resource URI, e.g. {@code "Asia/Tokyo"}
     * @return the current date-time in that zone, ISO-8601 formatted with zone offset and id
     */
    @ResourceTemplate(
            name = "time-in-zone",
            description = "The current date-time in a given IANA time zone.",
            uriTemplate = "time://zone/{zone}",
            mimeType = "text/plain")
    public String timeInZone(@ResourceTemplateArg(name = "zone") String zone) {
        return TimeService.currentTime(zone);
    }
}
