package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The read model of a task, returned by the REST API and the MCP tools.
 *
 * @param id the task id
 * @param title the title
 * @param description the description, or {@code null}
 * @param project the lower-case project name
 * @param status the status
 * @param priority the priority
 * @param dueDate the due date, or {@code null}
 * @param overdue {@code true} when the task is open and its due date is before today
 * @param createdAt when the task was created
 * @param updatedAt when it last changed
 * @param completedAt when it was marked done, or {@code null}
 */
public record TaskView(
        long id,
        String title,
        String description,
        String project,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {}
