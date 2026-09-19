package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Every validation, parsing and ordering rule of the task tracker, shared by the REST API and the MCP tools.
 *
 * <p>Kept free of any CDI, REST or MCP annotation so it can be unit-tested with plain JUnit. Every rejection is an
 * {@link IllegalArgumentException} whose message names the field and what it accepts: the REST layer turns it
 * into a 400, the MCP tools into an {@code isError} result.
 */
public final class TaskRules {

    /** The project of a task created without one. */
    public static final String DEFAULT_PROJECT = "inbox";

    /** The priority of a task created without one. */
    public static final TaskPriority DEFAULT_PRIORITY = TaskPriority.MEDIUM;

    /** The number of tasks a list returns when the caller gives no limit. */
    public static final int DEFAULT_LIMIT = 20;

    /** The largest number of tasks a list returns. */
    public static final int MAX_LIMIT = 100;

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 2000;
    private static final Pattern PROJECT = Pattern.compile("[a-z0-9][a-z0-9-]{0,79}");

    private TaskRules() {}

    /**
     * Validates a title.
     *
     * @param raw the title as sent
     * @return the trimmed title
     * @throws IllegalArgumentException if it is blank or longer than 200 characters
     */
    public static String title(String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("title is required: 1 to " + TITLE_MAX + " characters");
        }
        if (t.length() > TITLE_MAX) {
            throw new IllegalArgumentException(
                    "title must have at most " + TITLE_MAX + " characters, got " + t.length());
        }
        return t;
    }

    /**
     * Validates a description.
     *
     * @param raw the description as sent, or {@code null}
     * @return the trimmed description, or {@code null} when it is absent or blank
     * @throws IllegalArgumentException if it is longer than 2000 characters
     */
    public static String description(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String d = raw.strip();
        if (d.length() > DESCRIPTION_MAX) {
            throw new IllegalArgumentException(
                    "description must have at most " + DESCRIPTION_MAX + " characters, got " + d.length());
        }
        return d;
    }

    /**
     * Validates and normalises a project name.
     *
     * @param raw the project as sent, or {@code null}
     * @return the trimmed, lower-case name, or {@value #DEFAULT_PROJECT} when it is absent or blank
     * @throws IllegalArgumentException if it is not 1 to 80 characters among {@code a-z}, {@code 0-9} and
     *     {@code -}, starting with a letter or a digit
     */
    public static String project(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PROJECT;
        }
        String p = raw.strip().toLowerCase(Locale.ROOT);
        if (!PROJECT.matcher(p).matches()) {
            throw new IllegalArgumentException("project '" + raw.strip()
                    + "' is invalid: use 1 to 80 characters among a-z, 0-9 and '-', starting with a letter or"
                    + " a digit");
        }
        return p;
    }

    /**
     * Parses a priority, ignoring case.
     *
     * @param raw the priority as sent, or {@code null}
     * @param dflt what an absent or blank priority means; may be {@code null}
     * @return the priority, or {@code dflt}
     * @throws IllegalArgumentException if it is not {@code LOW}, {@code MEDIUM} or {@code HIGH}
     */
    public static TaskPriority priority(String raw, TaskPriority dflt) {
        return parseEnum(raw, "priority", TaskPriority.class, dflt);
    }

    /**
     * Parses a status filter, ignoring case.
     *
     * @param raw the status as sent, or {@code null}
     * @return the status, or {@code null} (meaning any status) when it is absent or blank
     * @throws IllegalArgumentException if it is not {@code OPEN} or {@code DONE}
     */
    public static TaskStatus status(String raw) {
        return parseEnum(raw, "status", TaskStatus.class, null);
    }

    /**
     * Parses an ISO date.
     *
     * @param raw the date as sent, {@code yyyy-MM-dd}, or {@code null}
     * @param field the name of the field, for the error message
     * @return the date, or {@code null} when it is absent or blank
     * @throws IllegalArgumentException if it is not a real {@code yyyy-MM-dd} date
     */
    public static LocalDate date(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // ISO_LOCAL_DATE resolves strictly: 2026-02-30 is rejected, not moved to March.
            return LocalDate.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    field + " '" + raw.strip() + "' is not a date: expected yyyy-MM-dd, such as 2026-09-30");
        }
    }

    /**
     * The number of tasks a list returns.
     *
     * @param requested the limit as sent, or {@code null}
     * @return {@value #DEFAULT_LIMIT} when absent, otherwise {@code requested} clamped to 1..{@value #MAX_LIMIT}
     */
    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(requested, 1, MAX_LIMIT);
    }

    /**
     * Turns a search query into a case-insensitive SQL {@code LIKE} pattern for
     * {@link TaskRepository#searchText(String)}.
     *
     * <p>{@code %}, {@code _} and {@code \} are removed rather than escaped, so a query can neither widen the
     * search to everything nor end the pattern with an escape character.
     *
     * @param query the words to look for
     * @return {@code %query%}, lower-case
     * @throws IllegalArgumentException if fewer than 2 characters remain once trimmed and stripped of wildcards
     */
    public static String likePattern(String query) {
        String q = query == null
                ? ""
                : query.strip().toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "");
        if (q.length() < 2) {
            throw new IllegalArgumentException(
                    "query must have at least 2 characters, not counting the wildcards % and _");
        }
        return "%" + q + "%";
    }

    /**
     * Whether a task is overdue.
     *
     * @param task the task
     * @param today the current date
     * @return {@code true} when the task is open and its due date is before {@code today}
     */
    public static boolean overdue(Task task, LocalDate today) {
        return task.getStatus() == TaskStatus.OPEN
                && task.getDueDate() != null
                && task.getDueDate().isBefore(today);
    }

    /**
     * The order in which to work on tasks: open before done, then overdue first, then higher priority first,
     * then earlier due date first (no due date last), then by id.
     *
     * <p>A factory rather than a constant because "overdue" depends on the date.
     *
     * @param today the current date
     * @return the comparator
     */
    public static Comparator<Task> urgency(LocalDate today) {
        return Comparator.<Task, Boolean>comparing(t -> t.getStatus() != TaskStatus.OPEN)
                .thenComparing(t -> !overdue(t, today))
                .thenComparing(t -> -t.getPriority().rank())
                .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Maps a task to its read model.
     *
     * @param task the task
     * @param today the current date, for {@link TaskView#overdue()}
     * @return the view
     */
    public static TaskView view(Task task, LocalDate today) {
        return new TaskView(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getProject(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                overdue(task, today),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt());
    }

    /**
     * Validates every editable field of {@code input}, then, only if they are all valid, sets them on
     * {@code task}. An absent field takes its default: no description, project {@value #DEFAULT_PROJECT},
     * priority {@code MEDIUM}, no due date. The status and the timestamps are left alone.
     *
     * @param input the fields as sent
     * @param task the task to change
     * @throws IllegalArgumentException if {@code input} is {@code null} or a field is invalid; {@code task} is
     *     then unchanged
     */
    public static void apply(TaskInput input, Task task) {
        if (input == null) {
            throw new IllegalArgumentException("a task is required: {\"title\": ...}");
        }
        String title = title(input.title());
        String description = description(input.description());
        String project = project(input.project());
        TaskPriority priority = priority(input.priority(), DEFAULT_PRIORITY);
        LocalDate dueDate = date(input.dueDate(), "dueDate");
        task.setTitle(title);
        task.setDescription(description);
        task.setProject(project);
        task.setPriority(priority);
        task.setDueDate(dueDate);
    }

    /**
     * Names the editable fields that {@link #apply} would change, for the detail of an {@code UPDATED} event.
     *
     * @param before the task as stored
     * @param after the fields as sent; validated as {@link #apply} does
     * @return the changed fields in declaration order, such as {@code "title, dueDate"}, or {@code "no change"}
     * @throws IllegalArgumentException if a field of {@code after} is invalid
     */
    public static String changes(Task before, TaskInput after) {
        Task target = new Task();
        apply(after, target);
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(before.getTitle(), target.getTitle())) {
            changed.add("title");
        }
        if (!Objects.equals(before.getDescription(), target.getDescription())) {
            changed.add("description");
        }
        if (!Objects.equals(before.getProject(), target.getProject())) {
            changed.add("project");
        }
        if (before.getPriority() != target.getPriority()) {
            changed.add("priority");
        }
        if (!Objects.equals(before.getDueDate(), target.getDueDate())) {
            changed.add("dueDate");
        }
        return changed.isEmpty() ? "no change" : String.join(", ", changed);
    }

    private static <E extends Enum<E>> E parseEnum(String raw, String field, Class<E> type, E dflt) {
        if (raw == null || raw.isBlank()) {
            return dflt;
        }
        String name = raw.strip().toUpperCase(Locale.ROOT);
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        String accepted = Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        throw new IllegalArgumentException(field + " '" + raw.strip() + "' is invalid: use one of " + accepted);
    }
}
