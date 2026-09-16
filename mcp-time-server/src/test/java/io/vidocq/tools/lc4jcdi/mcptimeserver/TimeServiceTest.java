package io.vidocq.tools.lc4jcdi.mcptimeserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Plain JUnit tests for {@link TimeService}'s pure logic — no CDI container involved. */
class TimeServiceTest {

    @Test
    void validateZoneAcceptsKnownIanaZone() {
        assertEquals(ZoneId.of("Europe/Paris"), TimeService.validateZone("Europe/Paris"));
    }

    @Test
    void validateZoneRejectsUnknownZone() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> TimeService.validateZone("Not/AZone"));
        assertTrue(e.getMessage().contains("Not/AZone"));
    }

    @Test
    void validateZoneRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> TimeService.validateZone(""));
        assertThrows(IllegalArgumentException.class, () -> TimeService.validateZone(null));
    }

    @Test
    void currentTimeDefaultsToUtcWhenZoneIsNullOrBlank() {
        assertTrue(TimeService.currentTime(null).endsWith("Z[UTC]"));
        assertTrue(TimeService.currentTime("  ").endsWith("Z[UTC]"));
    }

    @Test
    void currentTimeUsesTheGivenZone() {
        assertTrue(TimeService.currentTime("Asia/Tokyo").endsWith("[Asia/Tokyo]"));
    }

    @Test
    void currentTimeRejectsUnknownZone() {
        assertThrows(IllegalArgumentException.class, () -> TimeService.currentTime("Not/AZone"));
    }

    @Test
    void convertTimeConvertsBetweenZones() {
        // No DST ambiguity: mid-January, America/New_York is EST (UTC-5), Europe/Paris is CET (UTC+1).
        String converted = TimeService.convertTime("2026-01-15T12:00:00", "America/New_York", "Europe/Paris");
        assertEquals("2026-01-15T18:00:00+01:00[Europe/Paris]", converted);
    }

    @Test
    void convertTimeRejectsUnknownSourceZone() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> TimeService.convertTime("2026-01-15T12:00:00", "Not/AZone", "Europe/Paris"));
        assertTrue(e.getMessage().contains("Not/AZone"));
    }

    @Test
    void convertTimeRejectsUnknownTargetZone() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> TimeService.convertTime("2026-01-15T12:00:00", "Europe/Paris", "Not/AZone"));
        assertTrue(e.getMessage().contains("Not/AZone"));
    }

    @Test
    void convertTimeRejectsUnparseableDateTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeService.convertTime("not-a-date-time", "Europe/Paris", "Asia/Tokyo"));
    }
}
