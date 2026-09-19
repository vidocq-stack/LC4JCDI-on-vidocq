package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The write logic of {@link TaskService}: which rows and which events each method writes. There is no container
 * here, so {@code @Transactional} does nothing and each repository call autocommits; the rollback of
 * {@link TaskService#completeAll} is checked end to end against the running application.
 */
class TaskServiceTest {

    private TasksDatabase db;
    private TaskService service;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        service = db.service();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    private List<TaskEventType> history(long id) {
        return db.events.findByTaskIdOrderByIdAsc(id).stream()
                .map(TaskEvent::getType)
                .toList();
    }

    @Test
    void createWritesAnOpenTaskAndACreatedEvent() {
        TaskView v = service.create(new TaskInput(" Water the plants ", null, "Home", "low", "2026-10-01"));
        assertEquals(9L, v.id());
        assertEquals("Water the plants", v.title());
        assertEquals("home", v.project());
        assertEquals(TaskStatus.OPEN, v.status());
        assertEquals(TaskPriority.LOW, v.priority());
        assertEquals(LocalDate.of(2026, 10, 1), v.dueDate());
        assertNotNull(v.createdAt());
        assertEquals(v.createdAt(), v.updatedAt());
        assertNull(v.completedAt());
        assertEquals(List.of(TaskEventType.CREATED), history(9L));
    }

    @Test
    void createRejectsBadInputBeforeWritingAnything() {
        assertThrows(
                IllegalArgumentException.class, () -> service.create(new TaskInput("", null, null, null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.create(new TaskInput("ok", null, null, "URGENT", null)));
        assertEquals(8, db.tasks.findAll().count());
        assertEquals(10, db.events.findAll().count());
    }

    @Test
    void updateReplacesTheEditableFieldsAndRecordsWhatChanged() {
        TaskView v = service.update(1L, new TaskInput("Publish the notes", null, "vidocq", "HIGH", null))
                .orElseThrow();
        assertEquals("Publish the notes", v.title());
        assertNull(v.description());
        assertNull(v.dueDate());
        assertEquals(TaskStatus.OPEN, v.status());
        TaskEvent last = db.events.findByTaskIdOrderByIdAsc(1L).getLast();
        assertEquals(TaskEventType.UPDATED, last.getType());
        assertEquals("title, description, dueDate", last.getDetail());
        assertTrue(service.update(999L, new TaskInput("x", null, null, null, null)).isEmpty());
    }

    @Test
    void completeAndReopenAreIdempotent() {
        TaskView done = service.complete(6L).orElseThrow();
        assertEquals(TaskStatus.DONE, done.status());
        assertNotNull(done.completedAt());
        service.complete(6L);
        assertEquals(List.of(TaskEventType.CREATED, TaskEventType.COMPLETED), history(6L));

        TaskView open = service.reopen(6L).orElseThrow();
        assertEquals(TaskStatus.OPEN, open.status());
        assertNull(open.completedAt());
        service.reopen(6L);
        assertEquals(
                List.of(TaskEventType.CREATED, TaskEventType.COMPLETED, TaskEventType.REOPENED), history(6L));
        assertTrue(service.complete(999L).isEmpty());
        assertTrue(service.reopen(999L).isEmpty());
    }

    @Test
    void deleteKeepsTheHistory() {
        assertTrue(service.delete(4L));
        assertTrue(db.tasks.findById(4L).isEmpty());
        assertEquals(List.of(TaskEventType.CREATED, TaskEventType.DELETED), history(4L));
        assertFalse(service.delete(4L));
    }

    @Test
    void completeAllCompletesEveryIdInOrder() {
        List<TaskView> done = service.completeAll(List.of(6L, 1L));
        assertEquals(List.of(6L, 1L), done.stream().map(TaskView::id).toList());
        assertTrue(done.stream().allMatch(v -> v.status() == TaskStatus.DONE));
    }

    @Test
    void completeAllStopsAtTheFirstUnknownId() {
        TaskNotFoundException e =
                assertThrows(TaskNotFoundException.class, () -> service.completeAll(List.of(6L, 999_999L)));
        assertEquals(999_999L, e.id());
        assertThrows(IllegalArgumentException.class, () -> service.completeAll(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.completeAll(null));
    }

    @Test
    void renameProjectMovesEveryTaskAndRecordsOneEventEach() {
        assertEquals(2, service.renameProject("home", "House"));
        assertEquals(2, db.tasks.countByProject("house"));
        TaskEvent last = db.events.findByTaskIdOrderByIdAsc(7L).getLast();
        assertEquals(TaskEventType.UPDATED, last.getType());
        assertEquals("project: home → house", last.getDetail());
        assertEquals(TaskEventType.UPDATED, db.events.findByTaskIdOrderByIdAsc(6L).getLast().getType());
    }

    @Test
    void renameProjectRejectsUnknownAndInvalidNames() {
        assertThrows(ProjectNotFoundException.class, () -> service.renameProject("nope", "other"));
        assertThrows(IllegalArgumentException.class, () -> service.renameProject("home", "not valid"));
        assertThrows(IllegalArgumentException.class, () -> service.renameProject("home", "HOME"));
        assertEquals(2, db.tasks.countByProject("home"));
    }
}
