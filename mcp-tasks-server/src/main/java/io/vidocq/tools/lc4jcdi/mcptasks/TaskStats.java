package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counts over a set of tasks, as of a given day.
 *
 * @param asOf the day the counts are computed for
 * @param project the project the counts are restricted to, or {@code null} for every project
 * @param total the number of tasks
 * @param open the number of open tasks
 * @param done the number of done tasks
 * @param overdue the number of open tasks due before {@code asOf}
 * @param dueToday the number of open tasks due on {@code asOf}
 * @param dueNext7Days the number of open tasks due in the 7 days starting with {@code asOf}
 * @param openByPriority the number of open tasks per priority, {@code HIGH} then {@code MEDIUM} then {@code LOW}
 * @param projects the counts per project, sorted by project name
 */
public record TaskStats(
        LocalDate asOf,
        String project,
        long total,
        long open,
        long done,
        long overdue,
        long dueToday,
        long dueNext7Days,
        Map<String, Long> openByPriority,
        List<ProjectStats> projects) {

    /**
     * Computes the counts.
     *
     * @param tasks the tasks to count
     * @param today the current date
     * @param projectOrNull a project name to restrict the counts to, or {@code null}
     * @return the counts
     */
    public static TaskStats of(List<Task> tasks, LocalDate today, String projectOrNull) {
        LocalDate weekEnd = today.plusDays(7);
        long total = 0;
        long open = 0;
        long done = 0;
        long overdue = 0;
        long dueToday = 0;
        long dueNext7Days = 0;
        Map<String, Long> openByPriority = new LinkedHashMap<>();
        openByPriority.put(TaskPriority.HIGH.name(), 0L);
        openByPriority.put(TaskPriority.MEDIUM.name(), 0L);
        openByPriority.put(TaskPriority.LOW.name(), 0L);
        Map<String, long[]> perProject = new TreeMap<>();

        for (Task t : tasks) {
            if (projectOrNull != null && !projectOrNull.equals(t.getProject())) {
                continue;
            }
            total++;
            long[] p = perProject.computeIfAbsent(t.getProject(), k -> new long[3]);
            if (t.getStatus() == TaskStatus.DONE) {
                done++;
                p[1]++;
                continue;
            }
            open++;
            p[0]++;
            openByPriority.merge(t.getPriority().name(), 1L, Long::sum);
            LocalDate due = t.getDueDate();
            if (TaskRules.overdue(t, today)) {
                overdue++;
                p[2]++;
            } else if (due != null && due.isBefore(weekEnd)) {
                dueNext7Days++;
                if (due.equals(today)) {
                    dueToday++;
                }
            }
        }

        List<ProjectStats> projects = perProject.entrySet().stream()
                .map(e -> new ProjectStats(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
        return new TaskStats(
                today,
                projectOrNull,
                total,
                open,
                done,
                overdue,
                dueToday,
                dueNext7Days,
                openByPriority,
                projects);
    }
}
