package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plain JUnit tests for {@link TaskStats#of}, on the eight seed tasks built in memory. */
class TaskStatsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    @Test
    void countsEverySeedFact() {
        TaskStats s = TaskStats.of(SeedTasks.on(TODAY), TODAY, null);
        assertEquals(TODAY, s.asOf());
        assertNull(s.project());
        assertEquals(8, s.total());
        assertEquals(6, s.open());
        assertEquals(2, s.done());
        assertEquals(1, s.overdue());
        assertEquals(1, s.dueToday());
        assertEquals(3, s.dueNext7Days());
    }

    @Test
    void countsOpenTasksByPriorityHighestFirst() {
        TaskStats s = TaskStats.of(SeedTasks.on(TODAY), TODAY, null);
        assertEquals(List.of("HIGH", "MEDIUM", "LOW"), List.copyOf(s.openByPriority().keySet()));
        assertEquals(Map.of("HIGH", 2L, "MEDIUM", 2L, "LOW", 2L), s.openByPriority());
    }

    @Test
    void countsPerProjectSortedByName() {
        TaskStats s = TaskStats.of(SeedTasks.on(TODAY), TODAY, null);
        assertEquals(
                List.of(
                        new ProjectStats("home", 1, 1, 0),
                        new ProjectStats("lc4jcdi", 3, 1, 0),
                        new ProjectStats("vidocq", 2, 0, 1)),
                s.projects());
    }

    @Test
    void filtersOnOneProject() {
        TaskStats s = TaskStats.of(SeedTasks.on(TODAY), TODAY, "lc4jcdi");
        assertEquals("lc4jcdi", s.project());
        assertEquals(4, s.total());
        assertEquals(3, s.open());
        assertEquals(1, s.done());
        assertEquals(0, s.overdue());
        assertEquals(1, s.dueToday());
        assertEquals(2, s.dueNext7Days());
        assertEquals(Map.of("HIGH", 1L, "MEDIUM", 1L, "LOW", 1L), s.openByPriority());
        assertEquals(List.of(new ProjectStats("lc4jcdi", 3, 1, 0)), s.projects());
    }

    @Test
    void anUnknownProjectHasZeroEverywhere() {
        TaskStats s = TaskStats.of(SeedTasks.on(TODAY), TODAY, "nope");
        assertEquals(0, s.total());
        assertEquals(Map.of("HIGH", 0L, "MEDIUM", 0L, "LOW", 0L), s.openByPriority());
        assertEquals(List.of(), s.projects());
    }
}
