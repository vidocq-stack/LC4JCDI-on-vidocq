package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Flyway scripts and the Mansart-generated {@code TaskRepositoryImpl}/{@code TaskEventRepositoryImpl} against
 * H2, without a container.
 */
class TaskRepositoryTest {

    private TasksDatabase db;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    @Test
    void bothScriptsApplyAndSeedEightTasksAndTenEvents() {
        assertEquals(2, db.migrationsApplied);
        assertEquals(8, db.tasks.findAll().count());
        List<TaskEvent> all = db.events.findAll().toList();
        assertEquals(10, all.size());
        assertEquals(8, all.stream().filter(e -> e.getType() == TaskEventType.CREATED).count());
        assertEquals(2, all.stream().filter(e -> e.getType() == TaskEventType.COMPLETED).count());
    }

    @Test
    void seedDatesAreRelativeToToday() {
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(2), db.tasks.findById(2L).orElseThrow().getDueDate());
        assertEquals(today, db.tasks.findById(3L).orElseThrow().getDueDate());
        assertNull(db.tasks.findById(5L).orElseThrow().getDueDate());
        assertTrue(db.tasks.findById(7L).orElseThrow().getCompletedAt().isBefore(Instant.now()));
    }

    @Test
    void derivedQueriesFilterByStatusAndProject() {
        assertEquals(6, db.tasks.findByStatusOrderByDueDateAsc(TaskStatus.OPEN).size());
        assertEquals(4, db.tasks.countByProject("lc4jcdi"));
        assertEquals(
                List.of(3L, 4L, 5L, 8L),
                db.tasks.findByProjectOrderByIdAsc("lc4jcdi").stream()
                        .map(Task::getId)
                        .toList());
        List<Task> homeDone = db.tasks.findByProjectAndStatusOrderByDueDateAsc("home", TaskStatus.DONE);
        assertEquals(List.of(7L), homeDone.stream().map(Task::getId).toList());
    }

    @Test
    void jdqlSearchLooksInTitlesAndDescriptions() {
        assertEquals(
                List.of(2L),
                db.tasks.searchText("%redaction%").stream().map(Task::getId).toList());
        // "DNS" is only in task 7's description; tasks 5 and 8 have no description at all.
        assertEquals(
                List.of(7L), db.tasks.searchText("%dns%").stream().map(Task::getId).toList());
    }

    @Test
    void saveRoundTripsEnumsDatesAndInstants() {
        Instant created = Instant.parse("2026-09-19T08:30:15.123456Z");
        Task t = new Task();
        t.setTitle("Round trip");
        t.setProject("tests");
        t.setStatus(TaskStatus.DONE);
        t.setPriority(TaskPriority.HIGH);
        t.setDueDate(LocalDate.of(2026, 12, 31));
        t.setCreatedAt(created);
        t.setUpdatedAt(created);
        t.setCompletedAt(created.plusSeconds(60));
        Long id = db.tasks.save(t).getId();
        assertEquals(9L, id);

        Task back = db.tasks.findById(id).orElseThrow();
        assertEquals("Round trip", back.getTitle());
        assertNull(back.getDescription());
        assertEquals(TaskStatus.DONE, back.getStatus());
        assertEquals(TaskPriority.HIGH, back.getPriority());
        assertEquals(LocalDate.of(2026, 12, 31), back.getDueDate());
        assertEquals(created, back.getCreatedAt());
        assertEquals(created.plusSeconds(60), back.getCompletedAt());
    }

    @Test
    void jdqlUpdateRenamesAProject() {
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        assertEquals(2, db.tasks.renameProject("home", "house", now));
        assertEquals(0, db.tasks.countByProject("home"));
        List<Task> moved = db.tasks.findByProjectOrderByIdAsc("house");
        assertEquals(List.of(6L, 7L), moved.stream().map(Task::getId).toList());
        assertEquals(now, moved.get(0).getUpdatedAt());
    }

    @Test
    void eventsOfATaskComeOldestFirst() {
        assertEquals(
                List.of(TaskEventType.CREATED, TaskEventType.COMPLETED),
                db.events.findByTaskIdOrderByIdAsc(7L).stream()
                        .map(TaskEvent::getType)
                        .toList());
        assertEquals(List.of(), db.events.findByTaskIdOrderByIdAsc(999L));
    }
}
