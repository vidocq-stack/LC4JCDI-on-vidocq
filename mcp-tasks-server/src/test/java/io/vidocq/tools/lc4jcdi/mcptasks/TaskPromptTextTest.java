package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link TaskPromptText}: the text of the MCP prompts, built from the read models without any database. */
class TaskPromptTextTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    /** The six open seed tasks, in the order given, as views as of {@link #TODAY}. */
    private static List<TaskView> open(long... ids) {
        List<Task> seed = SeedTasks.on(TODAY);
        List<TaskView> views = new ArrayList<>();
        for (long id : ids) {
            views.add(TaskRules.view(seed.get((int) id - 1), TODAY));
        }
        return views;
    }

    private static void assertBefore(String text, String first, String second) {
        int a = text.indexOf(first);
        int b = text.indexOf(second);
        assertTrue(a >= 0, "missing: " + first);
        assertTrue(b >= 0, "missing: " + second);
        assertTrue(a < b, first + " should come before " + second);
    }

    @Test
    void planMyDayListsTheOpenTasksWithTheDateAndTheHours() {
        String text = TaskPromptText.planMyDay(TODAY, 5, null, open(2, 3, 1, 4, 6, 5));
        assertTrue(text.contains("2026-09-19"), text);
        assertTrue(text.contains("5 hours"), text);
        for (String title : List.of(
                "Review the dev console redaction rules",
                "Merge the MRTR batch pull request",
                "Publish the Vidocq 0.4.0 release notes",
                "Write the mcp-tasks-server README",
                "Book the car service",
                "Answer the H2 file-lock question")) {
            assertTrue(text.contains(title), title);
        }
        assertTrue(text.contains("every project"), text);
    }

    @Test
    void planMyDayPutsTheOverdueTasksFirst() {
        // Given in another order, the overdue task (#2) still comes first; the others keep the order given.
        String text = TaskPromptText.planMyDay(TODAY, 6, null, open(3, 1, 2, 4));
        assertBefore(text, "Review the dev console redaction rules", "Merge the MRTR batch pull request");
        assertBefore(text, "Merge the MRTR batch pull request", "Publish the Vidocq 0.4.0 release notes");
        assertBefore(text, "Publish the Vidocq 0.4.0 release notes", "Write the mcp-tasks-server README");
        assertTrue(text.contains("overdue"), text);
    }

    @Test
    void planMyDayAsksForASchedule() {
        String text = TaskPromptText.planMyDay(TODAY, 6, "lc4jcdi", open(3, 4, 5));
        assertTrue(text.contains("project lc4jcdi"), text);
        assertTrue(text.contains("6 hours"), text);
        assertTrue(text.contains("break"), text);
        assertTrue(text.contains("defer"), text);
        assertTrue(text.contains("Do not invent"), text);
    }

    @Test
    void planMyDayKeepsAtMostThirtyTasks() {
        Task template = SeedTasks.on(TODAY).get(5);
        List<TaskView> many = IntStream.rangeClosed(1, 35)
                .mapToObj(i -> new TaskView(
                        100 + i,
                        "Chore number " + i + ".",
                        null,
                        "home",
                        TaskStatus.OPEN,
                        template.getPriority(),
                        null,
                        false,
                        template.getCreatedAt(),
                        template.getUpdatedAt(),
                        null))
                .toList();
        String text = TaskPromptText.planMyDay(TODAY, 6, null, many);
        assertTrue(text.contains("Chore number 30."), text);
        assertFalse(text.contains("Chore number 31."), text);
        assertTrue(text.contains("30 of the 35"), text);
    }

    @Test
    void planMyDaySaysWhenNothingIsOpen() {
        String text = TaskPromptText.planMyDay(TODAY, 6, "home", List.of());
        assertTrue(text.contains("no open task"), text);
        assertTrue(text.contains("project home"), text);
        assertFalse(text.contains("```"), text);
    }

    @Test
    void reviewProjectAsksForASummaryRisksAndThreeActions() {
        String text = TaskPromptText.reviewProject("lc4jcdi", TODAY, open(3, 4, 5, 8));
        assertTrue(text.contains("lc4jcdi"), text);
        assertTrue(text.contains("2026-09-19"), text);
        assertTrue(text.contains("Merge the MRTR batch pull request"), text);
        assertTrue(text.contains("Tag the langchain4j-cdi snapshot build"), text);
        assertTrue(text.contains("3 open and 1 done"), text);
        assertTrue(text.contains("risks"), text);
        assertTrue(text.contains("next three actions"), text);
    }

    @Test
    void reviewProjectSaysWhenTheProjectIsEmpty() {
        String text = TaskPromptText.reviewProject("garden", TODAY, List.of());
        assertTrue(text.contains("garden"), text);
        assertTrue(text.contains("no task"), text);
        assertFalse(text.contains("```"), text);
    }

    @Test
    void hoursDefaultsToSixAndStaysWithinADay() {
        assertEquals(6, TaskPromptText.hours(null));
        assertEquals(6, TaskPromptText.hours(" "));
        assertEquals(3, TaskPromptText.hours(" 3 "));
        assertEquals(12, TaskPromptText.hours("12"));
        for (String bad : List.of("0", "13", "-1", "two", "2.5")) {
            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class, () -> TaskPromptText.hours(bad), bad);
            assertTrue(e.getMessage().contains("hours"), e.getMessage());
            assertTrue(e.getMessage().contains("1 to 12"), e.getMessage());
        }
    }
}
