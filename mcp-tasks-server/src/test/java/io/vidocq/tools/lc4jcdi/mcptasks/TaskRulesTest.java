package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain JUnit tests for {@link TaskRules}: validation, parsing and ordering, no container, no database. */
class TaskRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);

    private static IllegalArgumentException rejected(Runnable call) {
        return assertThrows(IllegalArgumentException.class, call::run);
    }

    // ---- title, description ----------------------------------------------------------------------------------

    @Test
    void titleIsTrimmed() {
        assertEquals("Buy milk", TaskRules.title("  Buy milk \t"));
    }

    @Test
    void titleIsRequired() {
        assertTrue(rejected(() -> TaskRules.title("   ")).getMessage().contains("title"));
        assertTrue(rejected(() -> TaskRules.title(null)).getMessage().contains("title"));
    }

    @Test
    void titleHasAtMost200Characters() {
        assertEquals(200, TaskRules.title("x".repeat(200)).length());
        IllegalArgumentException e = rejected(() -> TaskRules.title("x".repeat(201)));
        assertTrue(e.getMessage().contains("title"), e.getMessage());
        assertTrue(e.getMessage().contains("200"), e.getMessage());
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertNull(TaskRules.description("   "));
        assertNull(TaskRules.description(null));
        assertEquals("Some words.", TaskRules.description(" Some words. "));
    }

    @Test
    void descriptionHasAtMost2000Characters() {
        assertEquals(2000, TaskRules.description("d".repeat(2000)).length());
        assertTrue(rejected(() -> TaskRules.description("d".repeat(2001)))
                .getMessage()
                .contains("description"));
    }

    // ---- project ---------------------------------------------------------------------------------------------

    @Test
    void projectIsTrimmedAndLowerCased() {
        assertEquals("lc4jcdi", TaskRules.project("  LC4JCDI "));
        assertEquals("my-project-2", TaskRules.project("My-Project-2"));
    }

    @Test
    void projectDefaultsToInbox() {
        assertEquals("inbox", TaskRules.project(null));
        assertEquals("inbox", TaskRules.project("  "));
        assertEquals(TaskRules.DEFAULT_PROJECT, TaskRules.project(""));
    }

    @Test
    void projectRejectsOtherCharacters() {
        IllegalArgumentException e = rejected(() -> TaskRules.project("my project"));
        assertTrue(e.getMessage().contains("project"), e.getMessage());
        rejected(() -> TaskRules.project("-leading-dash"));
        rejected(() -> TaskRules.project("under_score"));
        rejected(() -> TaskRules.project("p".repeat(81)));
        assertEquals(80, TaskRules.project("p".repeat(80)).length());
    }

    // ---- priority, status ------------------------------------------------------------------------------------

    @Test
    void priorityIsCaseInsensitive() {
        assertEquals(TaskPriority.HIGH, TaskRules.priority("high", TaskPriority.MEDIUM));
        assertEquals(TaskPriority.LOW, TaskRules.priority(" Low ", TaskPriority.MEDIUM));
    }

    @Test
    void priorityFallsBackToTheDefault() {
        assertEquals(TaskPriority.MEDIUM, TaskRules.priority(null, TaskPriority.MEDIUM));
        assertEquals(TaskPriority.MEDIUM, TaskRules.priority(" ", TaskPriority.MEDIUM));
        assertNull(TaskRules.priority(null, null));
    }

    @Test
    void unknownPriorityListsTheAcceptedValues() {
        IllegalArgumentException e = rejected(() -> TaskRules.priority("URGENT", TaskPriority.MEDIUM));
        assertTrue(e.getMessage().contains("priority"), e.getMessage());
        assertTrue(e.getMessage().contains("URGENT"), e.getMessage());
        assertTrue(e.getMessage().contains("LOW, MEDIUM, HIGH"), e.getMessage());
    }

    @Test
    void statusIsOptionalAndCaseInsensitive() {
        assertNull(TaskRules.status(null));
        assertNull(TaskRules.status(""));
        assertEquals(TaskStatus.DONE, TaskRules.status("done"));
        IllegalArgumentException e = rejected(() -> TaskRules.status("closed"));
        assertTrue(e.getMessage().contains("OPEN, DONE"), e.getMessage());
    }

    // ---- date, limit, likePattern ----------------------------------------------------------------------------

    @Test
    void dateIsAnIsoDate() {
        assertEquals(LocalDate.of(2026, 2, 28), TaskRules.date("2026-02-28", "dueDate"));
        assertNull(TaskRules.date(null, "dueDate"));
        assertNull(TaskRules.date(" ", "dueDate"));
    }

    @Test
    void dateRejectsImpossibleDaysAndWords() {
        IllegalArgumentException e = rejected(() -> TaskRules.date("2026-02-30", "dueDate"));
        assertTrue(e.getMessage().contains("dueDate"), e.getMessage());
        assertTrue(e.getMessage().contains("yyyy-MM-dd"), e.getMessage());
        IllegalArgumentException word = rejected(() -> TaskRules.date("tomorrow", "dueBefore"));
        assertTrue(word.getMessage().contains("dueBefore"), word.getMessage());
    }

    @Test
    void limitDefaultsTo20AndIsClamped() {
        assertEquals(20, TaskRules.limit(null));
        assertEquals(1, TaskRules.limit(0));
        assertEquals(1, TaskRules.limit(-3));
        assertEquals(100, TaskRules.limit(500));
        assertEquals(7, TaskRules.limit(7));
    }

    @Test
    void likePatternIsLowerCasedAndWrapped() {
        assertEquals("%redaction%", TaskRules.likePattern("  Redaction "));
    }

    @Test
    void likePatternDropsWildcards() {
        assertEquals("%ab%", TaskRules.likePattern("a%_b"));
        assertEquals("%ab%", TaskRules.likePattern("a\\b"));
    }

    @Test
    void likePatternNeedsTwoCharacters() {
        assertTrue(rejected(() -> TaskRules.likePattern("a")).getMessage().contains("query"));
        rejected(() -> TaskRules.likePattern(null));
        rejected(() -> TaskRules.likePattern("%%_"));
    }

    // ---- overdue, urgency ------------------------------------------------------------------------------------

    @Test
    void overdueMeansOpenAndDueBeforeToday() {
        Task yesterday = task(TaskStatus.OPEN, TODAY.minusDays(1));
        Task today = task(TaskStatus.OPEN, TODAY);
        Task doneYesterday = task(TaskStatus.DONE, TODAY.minusDays(1));
        Task noDate = task(TaskStatus.OPEN, null);
        assertTrue(TaskRules.overdue(yesterday, TODAY));
        assertFalse(TaskRules.overdue(today, TODAY));
        assertFalse(TaskRules.overdue(doneYesterday, TODAY));
        assertFalse(TaskRules.overdue(noDate, TODAY));
    }

    @Test
    void urgencyOrdersTheOpenSeedTasks() {
        List<Long> ids = SeedTasks.on(TODAY).stream()
                .filter(t -> t.getStatus() == TaskStatus.OPEN)
                .sorted(TaskRules.urgency(TODAY))
                .map(Task::getId)
                .toList();
        // Overdue first (#2), then by priority (HIGH #3 today, #1 tomorrow; MEDIUM #4; LOW #6 dated, #5 undated).
        assertEquals(List.of(2L, 3L, 1L, 4L, 6L, 5L), ids);
    }

    @Test
    void urgencyPutsOpenTasksBeforeDoneTasks() {
        List<Long> ids = SeedTasks.on(TODAY).stream()
                .sorted(TaskRules.urgency(TODAY))
                .map(Task::getId)
                .toList();
        assertEquals(List.of(2L, 3L, 1L, 4L, 6L, 5L, 7L, 8L), ids);
    }

    // ---- view, apply, changes --------------------------------------------------------------------------------

    @Test
    void viewCopiesTheTaskAndComputesOverdue() {
        Task t = SeedTasks.on(TODAY).get(1);
        TaskView v = TaskRules.view(t, TODAY);
        assertEquals(2L, v.id());
        assertEquals("Review the dev console redaction rules", v.title());
        assertEquals(TaskStatus.OPEN, v.status());
        assertEquals(TaskPriority.MEDIUM, v.priority());
        assertEquals(TODAY.minusDays(2), v.dueDate());
        assertTrue(v.overdue());
        assertEquals(t.getCreatedAt(), v.createdAt());
    }

    @Test
    void applyValidatesEveryFieldBeforeChangingAny() {
        Task t = SeedTasks.on(TODAY).get(0);
        TaskInput bad = new TaskInput("New title", null, "vidocq", "HIGH", "not-a-date");
        rejected(() -> TaskRules.apply(bad, t));
        assertEquals("Publish the Vidocq 0.4.0 release notes", t.getTitle());
    }

    @Test
    void applyNormalisesAndDefaults() {
        Task t = new Task();
        TaskRules.apply(new TaskInput(" Water the plants ", "  ", null, null, null), t);
        assertEquals("Water the plants", t.getTitle());
        assertNull(t.getDescription());
        assertEquals("inbox", t.getProject());
        assertEquals(TaskPriority.MEDIUM, t.getPriority());
        assertNull(t.getDueDate());
    }

    @Test
    void changesNamesTheFieldsThatDiffer() {
        Task t = SeedTasks.on(TODAY).get(0);
        TaskInput same = new TaskInput(
                t.getTitle(), t.getDescription(), "VIDOCQ", "high", t.getDueDate().toString());
        assertEquals("no change", TaskRules.changes(t, same));
        TaskInput moved = new TaskInput("Publish the notes", t.getDescription(), "vidocq", "HIGH", "2026-10-01");
        assertEquals("title, dueDate", TaskRules.changes(t, moved));
    }

    private static Task task(TaskStatus status, LocalDate due) {
        return SeedTasks.task(99, "t", "p", status, TaskPriority.MEDIUM, due, TODAY, -1,
                status == TaskStatus.DONE ? 0 : null);
    }
}
