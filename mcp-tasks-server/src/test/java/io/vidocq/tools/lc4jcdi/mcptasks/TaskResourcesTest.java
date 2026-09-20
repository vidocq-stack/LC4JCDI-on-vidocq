package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MCP resources, called directly on the seeded in-memory database. A resource either answers JSON text or
 * throws, which the MCP server turns into a JSON-RPC error.
 */
class TaskResourcesTest {

    private TasksDatabase db;
    private TaskResources resources;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        resources = new TaskResources();
        resources.queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    @Test
    void aTaskIsReadWithItsHistory() {
        JsonObject details = TaskJsonTest.parse(resources.task(7));
        assertEquals(7, details.getJsonObject("task").getInt("id"));
        assertEquals(2, details.getJsonArray("history").size());
    }

    @Test
    void anUnknownIdThrows() {
        // A non-numeric {id} is bound by the MCP server itself now, before this resource ever runs.
        assertEquals(
                "No task with id 999999",
                assertThrows(TaskNotFoundException.class, () -> resources.task(999999))
                        .getMessage());
    }

    @Test
    void theSummaryCountsEveryProject() {
        JsonObject stats = TaskJsonTest.parse(resources.summary());
        assertEquals(8, stats.getInt("total"));
        assertEquals(6, stats.getInt("open"));
        assertEquals(3, stats.getJsonArray("projects").size());
    }
}
