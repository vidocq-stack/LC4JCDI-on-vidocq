package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.tools.ToolResponse;

/**
 * The MCP tools, called directly on the seeded in-memory database: every result is JSON text whose root is an
 * object, and every rejection is an {@code isError} result rather than an exception. The MCP wire format itself is
 * checked against the running application.
 */
class TaskToolsTest {

    private TasksDatabase db;
    private TaskTools tools;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        tools = new TaskTools();
        tools.queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    private static String text(ToolResponse r) {
        assertEquals(1, r.content().size());
        return assertInstanceOf(TextContent.class, r.content().getFirst()).text();
    }

    private static JsonObject ok(ToolResponse r) {
        assertFalse(r.isError(), () -> text(r));
        return TaskJsonTest.parse(text(r));
    }

    private static String error(ToolResponse r) {
        assertTrue(r.isError(), () -> text(r));
        return text(r);
    }

    private static List<Long> ids(JsonObject list) {
        assertEquals(list.getInt("count"), list.getJsonArray("tasks").size());
        return list.getJsonArray("tasks").stream()
                .map(JsonValue::asJsonObject)
                .map(t -> t.getJsonNumber("id").longValue())
                .toList();
    }

    @Test
    void listOpenTasksIsInUrgencyOrderAndFilters() {
        assertEquals(
                List.of(2L, 3L, 1L, 4L, 6L, 5L),
                ids(ok(tools.listOpenTasks(null, null, null, TaskRules.DEFAULT_LIMIT))));
        assertEquals(
                List.of(3L, 1L),
                ids(ok(tools.listOpenTasks(null, TaskPriority.HIGH, null, TaskRules.DEFAULT_LIMIT))));
        assertEquals(
                List.of(3L, 4L, 5L),
                ids(ok(tools.listOpenTasks("LC4JCDI", null, null, TaskRules.DEFAULT_LIMIT))));
        assertEquals(List.of(2L, 3L), ids(ok(tools.listOpenTasks(null, null, null, 2))));
        assertEquals(List.of(), ids(ok(tools.listOpenTasks("nope", null, null, TaskRules.DEFAULT_LIMIT))));
    }

    @Test
    void listOpenTasksRejectsBadArgumentsAsToolErrors() {
        // Priority is bound by the MCP server itself now: only TaskRules-validated fields reach this tool.
        String date = error(tools.listOpenTasks(null, null, "tomorrow", TaskRules.DEFAULT_LIMIT));
        assertTrue(date.contains("dueBefore") && date.contains("yyyy-MM-dd"), date);
        String project = error(tools.listOpenTasks("Not A Project", null, null, TaskRules.DEFAULT_LIMIT));
        assertTrue(project.contains("project"), project);
    }

    @Test
    void searchTasksSkipsDoneTasksUnlessAsked() {
        assertEquals(List.of(2L), ids(ok(tools.searchTasks("REDACTION", false, TaskRules.DEFAULT_LIMIT))));
        assertEquals(List.of(), ids(ok(tools.searchTasks("dns", false, TaskRules.DEFAULT_LIMIT))));
        assertEquals(List.of(7L), ids(ok(tools.searchTasks("dns", true, TaskRules.DEFAULT_LIMIT))));
        assertEquals(1, ok(tools.searchTasks("the", true, 1)).getInt("count"));
        String tooShort = error(tools.searchTasks("x", false, TaskRules.DEFAULT_LIMIT));
        assertTrue(tooShort.contains("at least 2 characters"), tooShort);
        error(tools.searchTasks(null, false, TaskRules.DEFAULT_LIMIT));
    }

    @Test
    void taskStatisticsCountsAllOrOneProject() {
        JsonObject all = ok(tools.taskStatistics(null));
        assertEquals(8, all.getInt("total"));
        assertEquals(6, all.getInt("open"));
        assertEquals(1, all.getInt("overdue"));
        assertEquals(3, all.getJsonArray("projects").size());
        JsonObject one = ok(tools.taskStatistics("lc4jcdi"));
        assertEquals("lc4jcdi", one.getString("project"));
        assertEquals(4, one.getInt("total"));
        assertEquals(1, one.getInt("done"));
        error(tools.taskStatistics("Not A Project"));
    }

    @Test
    void getTaskGivesTheTaskAndItsHistory() {
        JsonObject details = ok(tools.getTask(7L));
        assertEquals("Renew the domain name", details.getJsonObject("task").getString("title"));
        assertEquals(
                List.of("CREATED", "COMPLETED"),
                details.getJsonArray("history").stream()
                        .map(e -> e.asJsonObject().getString("type"))
                        .toList());
    }

    @Test
    void getTaskReportsAnUnknownOrMissingIdAsAToolError() {
        assertEquals("No task with id 999999", error(tools.getTask(999999L)));
        String missing = error(tools.getTask(null));
        assertTrue(missing.contains("id"), missing);
    }
}
