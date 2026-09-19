package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link TaskJson}: how the MCP tools, resources and prompts render the read models as JSON text. */
class TaskJsonTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    private static final TaskView VIEW = new TaskView(
            3L,
            "Merge the MRTR batch pull request",
            null,
            "lc4jcdi",
            TaskStatus.OPEN,
            TaskPriority.HIGH,
            LocalDate.of(2026, 9, 30),
            false,
            Instant.parse("2026-09-15T08:30:00Z"),
            Instant.parse("2026-09-16T10:00:00.250Z"),
            null);

    static JsonObject parse(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    @Test
    void datesAndInstantsAreIsoAndEnumsAreNames() {
        JsonObject o = parse(TaskJson.of(VIEW));
        assertEquals(3, o.getJsonNumber("id").longValue());
        assertEquals("2026-09-30", o.getString("dueDate"));
        assertEquals("2026-09-15T08:30:00Z", o.getString("createdAt"));
        assertEquals("2026-09-16T10:00:00.250Z", o.getString("updatedAt"));
        assertEquals("OPEN", o.getString("status"));
        assertEquals("HIGH", o.getString("priority"));
        assertFalse(o.getBoolean("overdue"));
    }

    @Test
    void absentValuesAreLeftOut() {
        JsonObject o = parse(TaskJson.of(VIEW));
        assertFalse(o.containsKey("description"));
        assertFalse(o.containsKey("completedAt"));
    }

    @Test
    void theOutputIsIndentedForAReader() {
        assertTrue(TaskJson.of(VIEW).contains("\n"));
    }

    @Test
    void aTaskListIsAnObjectWithItsCount() {
        JsonObject o = parse(TaskJson.of(TaskList.of(List.of(VIEW, VIEW))));
        assertEquals(2, o.getInt("count"));
        assertEquals(2, o.getJsonArray("tasks").size());
        assertEquals("lc4jcdi", o.getJsonArray("tasks").getJsonObject(0).getString("project"));
        assertEquals(0, parse(TaskJson.of(TaskList.of(List.of()))).getInt("count"));
    }

    @Test
    void statsKeepThePriorityOrder() {
        JsonObject o = parse(TaskJson.of(TaskStats.of(SeedTasks.on(TODAY), TODAY, null)));
        assertEquals("2026-09-19", o.getString("asOf"));
        assertEquals(8, o.getInt("total"));
        assertEquals(List.of("HIGH", "MEDIUM", "LOW"), List.copyOf(o.getJsonObject("openByPriority").keySet()));
        assertEquals("home", o.getJsonArray("projects").getJsonObject(0).getString("project"));
    }

    @Test
    void detailsCarryTheHistory() {
        TaskDetails details = new TaskDetails(
                VIEW,
                List.of(new TaskEventView(TaskEventType.CREATED, Instant.parse("2026-09-15T08:30:00Z"), "seeded")));
        JsonObject o = parse(TaskJson.of(details));
        assertEquals(3, o.getJsonObject("task").getInt("id"));
        assertEquals("CREATED", o.getJsonArray("history").getJsonObject(0).getString("type"));
        assertEquals("2026-09-15T08:30:00Z", o.getJsonArray("history").getJsonObject(0).getString("at"));
    }
}
