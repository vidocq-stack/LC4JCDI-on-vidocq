package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** The eight tasks of {@code V2__seed_tasks.sql}, built in memory for a given day, with ids 1 to 8. */
final class SeedTasks {

    private SeedTasks() {}

    static List<Task> on(LocalDate today) {
        return List.of(
                task(1, "Publish the Vidocq 0.4.0 release notes", "vidocq", TaskStatus.OPEN, TaskPriority.HIGH,
                        today.plusDays(1), today, -6, null),
                task(2, "Review the dev console redaction rules", "vidocq", TaskStatus.OPEN, TaskPriority.MEDIUM,
                        today.minusDays(2), today, -7, null),
                task(3, "Merge the MRTR batch pull request", "lc4jcdi", TaskStatus.OPEN, TaskPriority.HIGH,
                        today, today, -4, null),
                task(4, "Write the mcp-tasks-server README", "lc4jcdi", TaskStatus.OPEN, TaskPriority.MEDIUM,
                        today.plusDays(5), today, -1, null),
                task(5, "Answer the H2 file-lock question", "lc4jcdi", TaskStatus.OPEN, TaskPriority.LOW,
                        null, today, -2, null),
                task(6, "Book the car service", "home", TaskStatus.OPEN, TaskPriority.LOW,
                        today.plusDays(10), today, -3, null),
                task(7, "Renew the domain name", "home", TaskStatus.DONE, TaskPriority.HIGH,
                        today.minusDays(5), today, -10, -1),
                task(8, "Tag the langchain4j-cdi snapshot build", "lc4jcdi", TaskStatus.DONE, TaskPriority.MEDIUM,
                        null, today, -8, -3));
    }

    static Task task(
            long id,
            String title,
            String project,
            TaskStatus status,
            TaskPriority priority,
            LocalDate due,
            LocalDate today,
            int createdDaysAgo,
            Integer completedDaysAgo) {
        Task t = new Task();
        t.setId(id);
        t.setTitle(title);
        t.setProject(project);
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(due);
        Instant midnight = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        t.setCreatedAt(midnight.plusSeconds(86_400L * createdDaysAgo));
        t.setUpdatedAt(t.getCreatedAt());
        if (completedDaysAgo != null) {
            t.setCompletedAt(midnight.plusSeconds(86_400L * completedDaysAgo));
            t.setUpdatedAt(t.getCompletedAt());
        }
        return t;
    }
}
