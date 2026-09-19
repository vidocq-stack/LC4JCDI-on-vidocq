package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The text of the MCP prompts: what the model is told about the tasks, and what it is asked to do with them.
 *
 * <p>Pure, with no CDI and no MCP annotation, so that the wording is unit-tested. {@link TaskPrompts} reads the
 * tasks and passes them here. The tasks go into the text as a JSON block ({@link TaskJson}), so that the model sees
 * exactly the fields the tools answer.
 */
public final class TaskPromptText {

    /** The hours a day's plan fills when the caller gives none. */
    public static final int DEFAULT_HOURS = 6;

    /** The most hours a day's plan fills. */
    public static final int MAX_HOURS = 12;

    /** The most tasks {@link #planMyDay} puts in front of the model, the most urgent ones. */
    public static final int MAX_TASKS = 30;

    private TaskPromptText() {}

    /**
     * Parses the hours available for a day's plan. Prompt arguments are always strings.
     *
     * @param raw a whole number of hours, or {@code null}
     * @return {@value #DEFAULT_HOURS} when absent or blank, otherwise the number
     * @throws IllegalArgumentException if it is not a whole number from 1 to {@value #MAX_HOURS}
     */
    public static int hours(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_HOURS;
        }
        int hours;
        try {
            hours = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "hours '" + raw.strip() + "' is not a whole number: use 1 to " + MAX_HOURS);
        }
        if (hours < 1 || hours > MAX_HOURS) {
            throw new IllegalArgumentException("hours must be 1 to " + MAX_HOURS + ", got " + hours);
        }
        return hours;
    }

    /**
     * The {@code plan_my_day} prompt: asks the model to fit the open tasks into the hours available today.
     *
     * @param today the current date
     * @param hours the hours available, as {@link #hours(String)} returns them
     * @param project the project the plan is restricted to, or {@code null} for every project
     * @param open the open tasks, most urgent first; the overdue ones are moved to the front whatever the order
     *     given, and only the first {@value #MAX_TASKS} are kept
     * @return the text of one user message
     */
    public static String planMyDay(LocalDate today, int hours, String project, List<TaskView> open) {
        String scope = project == null ? "every project" : "project " + project;
        if (open.isEmpty()) {
            return """
                    Today is %s. I have %d hours for my tasks today, and there is no open task in %s.

                    Tell me so in one sentence. Do not invent tasks to fill the day.
                    """
                    .formatted(day(today), hours, scope);
        }
        List<TaskView> ordered = open.stream()
                .sorted(Comparator.comparing(t -> !t.overdue()))
                .limit(MAX_TASKS)
                .toList();
        String which = ordered.size() == open.size()
                ? "my %d open tasks".formatted(open.size())
                : "%d of the %d open tasks, the most urgent ones".formatted(ordered.size(), open.size());
        return """
                Today is %s. I have %d hours to work on my tasks today, in %s.

                Here are %s, most urgent first, as JSON. A task with "overdue": true is past its due date.

                ```json
                %s
                ```

                Plan my day:
                1. Start with the overdue tasks.
                2. Fit the other tasks into a schedule of %d hours, in the order above unless you say why, with a \
                short break at least every 90 minutes. Give each block a start time, an end time, and the id and \
                title of its task.
                3. Estimate how long each task takes from its title and description, and say which estimates are \
                guesses.
                4. When a task does not fit, defer it: list the deferred tasks and say why each one waits.
                5. Do not invent tasks, and do not change a priority or a due date: schedule only the tasks above.
                """
                .formatted(day(today), hours, scope, which, TaskJson.of(ordered), hours);
    }

    /**
     * The {@code review_project} prompt: asks the model for a status summary, the risks and the next actions of
     * one project.
     *
     * @param project the project name
     * @param today the current date
     * @param tasks every task of the project, open and done, most urgent first
     * @return the text of one user message
     */
    public static String reviewProject(String project, LocalDate today, List<TaskView> tasks) {
        if (tasks.isEmpty()) {
            return """
                    Today is %s. Project %s has no task.

                    Tell me so in one sentence. Do not invent tasks.
                    """
                    .formatted(day(today), project);
        }
        long open = tasks.stream().filter(t -> t.status() == TaskStatus.OPEN).count();
        return """
                Today is %s. Review project %s: it has %d tasks, %d open and %d done. Here they are as JSON, most \
                urgent first. A task with "overdue": true is open and past its due date.

                ```json
                %s
                ```

                Write a short review of the project:
                1. Summarise where it stands in two or three sentences.
                2. List the risks: the overdue tasks, and the HIGH priority tasks that are due soon or have no due \
                date. Name each one by id and title.
                3. Give the next three actions, most important first, each tied to the id of a task.
                Use only the tasks above: do not invent tasks.
                """
                .formatted(day(today), project, tasks.size(), open, tasks.size() - open, TaskJson.of(tasks));
    }

    /** {@code 2026-09-19 (Saturday)}: the model plans better knowing the day of the week. */
    private static String day(LocalDate today) {
        return today + " (" + today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ")";
    }
}
