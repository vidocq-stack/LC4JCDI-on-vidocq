package io.vidocq.tools.lc4jcdi.mcptimeserver;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Pure time logic used by the MCP tools, resource template and prompt.
 *
 * <p>Kept free of any CDI or MCP annotation so it can be unit-tested with plain JUnit, without a container.
 */
public final class TimeService {

    private TimeService() {}

    /**
     * Validates that {@code zone} is a known IANA time zone id.
     *
     * @param zone the IANA zone id to validate (e.g. {@code "Europe/Paris"})
     * @return the parsed {@link ZoneId}
     * @throws IllegalArgumentException if {@code zone} is blank or not a known IANA zone id
     */
    public static ZoneId validateZone(String zone) {
        if (zone == null || zone.isBlank()) {
            throw new IllegalArgumentException("Zone must not be blank");
        }
        Set<String> available = ZoneId.getAvailableZoneIds();
        if (!available.contains(zone)) {
            throw new IllegalArgumentException(
                    "Unknown IANA time zone '" + zone + "'. Expected an id such as 'Europe/Paris' or 'UTC'.");
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown IANA time zone '" + zone + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns the current date-time in the given IANA zone, defaulting to UTC.
     *
     * @param zone the IANA zone id, or {@code null}/blank for UTC
     * @return the current date-time, ISO-8601 formatted with zone offset and id
     */
    public static String currentTime(String zone) {
        ZoneId zoneId = (zone == null || zone.isBlank()) ? ZoneId.of("UTC") : validateZone(zone);
        return ZonedDateTime.now(zoneId).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    /**
     * Converts an ISO-8601 local date-time from one IANA zone to another.
     *
     * @param localDateTime the local date-time to convert, ISO-8601, e.g. {@code "2026-09-16T10:30:00"}
     * @param fromZone the IANA zone id the date-time is expressed in
     * @param toZone the IANA zone id to convert to
     * @return the converted date-time, ISO-8601 formatted with zone offset and id
     * @throws IllegalArgumentException if a zone is unknown or the date-time cannot be parsed
     */
    public static String convertTime(String localDateTime, String fromZone, String toZone) {
        ZoneId from = validateZone(fromZone);
        ZoneId to = validateZone(toZone);
        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(localDateTime);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid ISO-8601 local date-time '" + localDateTime + "': " + e.getMessage(), e);
        }
        ZonedDateTime source = parsed.atZone(from);
        ZonedDateTime target = source.withZoneSameInstant(to);
        return target.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
}
