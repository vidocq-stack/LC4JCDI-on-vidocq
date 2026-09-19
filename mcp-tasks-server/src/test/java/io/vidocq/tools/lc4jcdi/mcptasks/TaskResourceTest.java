package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * How {@link TaskResource} maps the service's outcomes to HTTP: status codes, bodies and the {@code Location}
 * header. There is no container and no HTTP here: the resource is called directly, on the seeded in-memory
 * database, and each repository call autocommits. The rollback of {@code POST /tasks/complete} is checked end to
 * end against the running application.
 */
class TaskResourceTest {

    private TasksDatabase db;
    private TaskResource resource;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        resource = new TaskResource();
        resource.service = db.service();
        resource.queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    private static List<?> list(Response r) {
        return assertInstanceOf(List.class, r.getEntity());
    }

    private static TaskView task(Response r) {
        return assertInstanceOf(TaskView.class, r.getEntity());
    }

    private static ErrorResponse error(Response r, int status, String code) {
        assertEquals(status, r.getStatus());
        ErrorResponse e = assertInstanceOf(ErrorResponse.class, r.getEntity());
        assertEquals(code, e.error());
        return e;
    }

    @Test
    void listIsInUrgencyOrderAndRejectsAnUnknownStatus() {
        Response all = resource.list(null, null);
        assertEquals(200, all.getStatus());
        assertEquals(8, list(all).size());
        assertEquals(2L, ((TaskView) list(all).getFirst()).id());
        assertEquals(1, list(resource.list("done", "home")).size());

        ErrorResponse e = error(resource.list("closed", null), 400, "bad_request");
        assertTrue(e.message().contains("OPEN, DONE"), e.message());
        error(resource.list(null, "Not A Project"), 400, "bad_request");
    }

    @Test
    void getAnswersTheTaskOr404() {
        Response found = resource.get(2L);
        assertEquals(200, found.getStatus());
        assertTrue(task(found).overdue());
        assertEquals("No task with id 999", error(resource.get(999L), 404, "not_found").message());
    }

    @Test
    void historyOutlivesTheTaskAnd404sWhenThereIsNone() {
        assertEquals(204, resource.delete(4L).getStatus());
        Response history = resource.history(4L);
        assertEquals(200, history.getStatus());
        List<?> events = list(history);
        assertEquals(TaskEventType.DELETED, ((TaskEventView) events.getLast()).type());
        error(resource.history(999L), 404, "not_found");
    }

    @Test
    void createAnswers201WithALocationAndTheTask() {
        Response created =
                resource.create(new TaskInput("Water the plants", null, "home", "low", "2026-10-01"), null);
        assertEquals(201, created.getStatus());
        assertEquals(9L, task(created).id());
        assertEquals(URI.create("tasks/9"), created.getLocation());
    }

    @Test
    void createRejectsAnInvalidOrMissingBodyWith400() {
        ErrorResponse blank =
                error(resource.create(new TaskInput("", null, null, null, null), null), 400, "bad_request");
        assertTrue(blank.message().startsWith("title"), blank.message());
        ErrorResponse priority = error(
                resource.create(new TaskInput("ok", null, null, "URGENT", null), null), 400, "bad_request");
        assertTrue(priority.message().contains("LOW, MEDIUM, HIGH"), priority.message());
        error(resource.create(null, null), 400, "bad_request");
        assertEquals(8, db.tasks.findAll().count());
    }

    @Test
    void updateAnswers200Or400Or404() {
        Response updated = resource.update(1L, new TaskInput("Publish the notes", null, "vidocq", "HIGH", null));
        assertEquals(200, updated.getStatus());
        assertEquals("Publish the notes", task(updated).title());
        error(resource.update(1L, new TaskInput("ok", null, null, null, "tomorrow")), 400, "bad_request");
        error(resource.update(999L, new TaskInput("x", null, null, null, null)), 404, "not_found");
    }

    @Test
    void completeAndReopenAnswerTheTaskOr404() {
        Response done = resource.complete(6L);
        assertEquals(200, done.getStatus());
        assertEquals(TaskStatus.DONE, task(done).status());
        Response open = resource.reopen(6L);
        assertEquals(200, open.getStatus());
        assertEquals(TaskStatus.OPEN, task(open).status());
        assertNull(task(open).completedAt());
        error(resource.complete(999L), 404, "not_found");
        error(resource.reopen(999L), 404, "not_found");
    }

    @Test
    void completeAllAnswersTheTasksInOrder() {
        Response done = resource.completeAll(new CompleteRequest(List.of(6L, 1L)));
        assertEquals(200, done.getStatus());
        assertEquals(
                List.of(6L, 1L),
                list(done).stream().map(v -> ((TaskView) v).id()).toList());
    }

    @Test
    void completeAllAnswers404NamingTheUnknownIdAnd400WithoutIds() {
        ErrorResponse missing =
                error(resource.completeAll(new CompleteRequest(List.of(6L, 999_999L))), 404, "not_found");
        assertEquals("No task with id 999999 — nothing was completed", missing.message());
        error(resource.completeAll(new CompleteRequest(List.of())), 400, "bad_request");
        error(resource.completeAll(new CompleteRequest(null)), 400, "bad_request");
        error(resource.completeAll(new CompleteRequest(Arrays.asList(6L, null))), 400, "bad_request");
        error(resource.completeAll(null), 400, "bad_request");
    }

    @Test
    void deleteAnswers204Then404() {
        Response deleted = resource.delete(4L);
        assertEquals(204, deleted.getStatus());
        assertNull(deleted.getEntity());
        error(resource.delete(4L), 404, "not_found");
        error(resource.get(4L), 404, "not_found");
    }

    @Test
    void errorBodiesCarryTheMessageOnly() {
        ErrorResponse e =
                error(resource.create(new TaskInput(" ", null, null, null, null), null), 400, "bad_request");
        assertFalse(e.message().contains("Exception"), e.message());
        assertFalse(e.message().contains(".java:"), e.message());
    }
}
