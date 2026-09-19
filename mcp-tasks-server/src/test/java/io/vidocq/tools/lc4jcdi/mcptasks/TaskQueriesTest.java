package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The read side, {@link TaskQueries}, on the seeded database. */
class TaskQueriesTest {

    private TasksDatabase db;
    private TaskQueries queries;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    private static List<Long> ids(List<TaskView> views) {
        return views.stream().map(TaskView::id).toList();
    }

    @Test
    void listFiltersAndSortsByUrgency() {
        assertEquals(List.of(2L, 3L, 1L, 4L, 6L, 5L, 7L, 8L), ids(queries.list(null, null)));
        assertEquals(List.of(2L, 3L, 1L, 4L, 6L, 5L), ids(queries.list("open", null)));
        assertEquals(List.of(7L), ids(queries.list("DONE", "Home")));
        assertEquals(List.of(3L, 4L, 5L, 8L), ids(queries.list(null, "lc4jcdi")));
        assertTrue(queries.list(null, "nope").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queries.list("closed", null));
    }

    @Test
    void findAndDetails() {
        assertTrue(queries.find(2L).orElseThrow().overdue());
        assertTrue(queries.find(999L).isEmpty());
        TaskDetails d = queries.details(7L).orElseThrow();
        assertEquals(7L, d.task().id());
        assertEquals(
                List.of(TaskEventType.CREATED, TaskEventType.COMPLETED),
                d.history().stream().map(TaskEventView::type).toList());
        assertTrue(queries.details(999L).isEmpty());
    }

    @Test
    void historyOutlivesTheTask() {
        db.service().delete(5L);
        assertTrue(queries.find(5L).isEmpty());
        assertEquals(
                List.of(TaskEventType.CREATED, TaskEventType.DELETED),
                queries.history(5L).stream().map(TaskEventView::type).toList());
        assertTrue(queries.history(999L).isEmpty());
    }

    @Test
    void openTasksFilterByProjectMinimumPriorityAndDueDate() {
        assertEquals(List.of(2L, 3L, 1L, 4L, 6L, 5L), ids(queries.openTasks(null, null, null, null)));
        assertEquals(List.of(3L, 1L), ids(queries.openTasks(null, "high", null, null)));
        assertEquals(List.of(2L, 3L, 1L, 4L), ids(queries.openTasks(null, "MEDIUM", null, null)));
        assertEquals(List.of(3L, 4L, 5L), ids(queries.openTasks("lc4jcdi", null, null, null)));
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertEquals(List.of(2L, 3L, 1L), ids(queries.openTasks(null, null, tomorrow, null)));
        assertEquals(List.of(2L, 3L), ids(queries.openTasks(null, null, null, 2)));
        assertThrows(IllegalArgumentException.class, () -> queries.openTasks(null, "URGENT", null, null));
        assertThrows(IllegalArgumentException.class, () -> queries.openTasks(null, null, "tomorrow", null));
    }

    @Test
    void searchIsCaseInsensitiveAndSkipsDoneTasksByDefault() {
        assertEquals(List.of(2L), ids(queries.search("REDACTION", false, null)));
        assertEquals(List.of(), ids(queries.search("dns", false, null)));
        assertEquals(List.of(7L), ids(queries.search("dns", true, null)));
        assertEquals(1, queries.search("the", true, 1).size());
        assertThrows(IllegalArgumentException.class, () -> queries.search("x", false, null));
    }

    @Test
    void statsAndProjects() {
        TaskStats all = queries.stats(null);
        assertEquals(8, all.total());
        assertEquals(1, all.overdue());
        assertEquals(1, all.dueToday());
        assertEquals(3, all.dueNext7Days());
        assertEquals(4, queries.stats("LC4JCDI").total());
        assertEquals(
                List.of("home", "lc4jcdi", "vidocq"),
                queries.projects().stream().map(ProjectStats::project).toList());
    }
}
