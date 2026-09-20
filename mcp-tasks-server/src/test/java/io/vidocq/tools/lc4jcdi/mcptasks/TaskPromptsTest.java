package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The MCP prompts, called directly on the seeded in-memory database. */
class TaskPromptsTest {

    private TasksDatabase db;
    private TaskPrompts prompts;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        prompts = new TaskPrompts();
        prompts.queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    @Test
    void planMyDayUsesTheOpenTasksAndSixHoursByDefault() {
        String text = prompts.planMyDay(null, TaskPromptText.DEFAULT_HOURS);
        assertTrue(text.contains("Merge the MRTR batch pull request"), text);
        assertTrue(text.contains("Book the car service"), text);
        assertFalse(text.contains("Renew the domain name"), text);
        assertTrue(text.contains("6 hours"), text);
    }

    @Test
    void planMyDayCanFocusOnOneProject() {
        String text = prompts.planMyDay("home", 2);
        assertTrue(text.contains("Book the car service"), text);
        assertFalse(text.contains("Merge the MRTR batch pull request"), text);
        assertTrue(text.contains("2 hours"), text);
    }

    @Test
    void planMyDayRejectsBadArguments() {
        // A non-numeric "hours" is bound by the MCP server itself now: only the range reaches this prompt.
        assertThrows(IllegalArgumentException.class, () -> prompts.planMyDay(null, 40));
        assertThrows(IllegalArgumentException.class, () -> prompts.planMyDay("Not A Project", TaskPromptText.DEFAULT_HOURS));
    }

    @Test
    void reviewProjectCoversOpenAndDoneTasks() {
        String text = prompts.reviewProject("LC4JCDI");
        assertTrue(text.contains("Merge the MRTR batch pull request"), text);
        assertTrue(text.contains("Tag the langchain4j-cdi snapshot build"), text);
        assertFalse(text.contains("Book the car service"), text);
    }

    @Test
    void reviewProjectNeedsAKnownProject() {
        IllegalArgumentException missing =
                assertThrows(IllegalArgumentException.class, () -> prompts.reviewProject(" "));
        assertTrue(missing.getMessage().contains("project is required"), missing.getMessage());
        IllegalArgumentException unknown =
                assertThrows(IllegalArgumentException.class, () -> prompts.reviewProject("garden"));
        assertTrue(unknown.getMessage().contains("home, lc4jcdi, vidocq"), unknown.getMessage());
    }
}
