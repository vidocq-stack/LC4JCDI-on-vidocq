package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The read side of the task tracker, shared by the REST API and the MCP tools, resources and prompts.
 *
 * <p>Not transactional: each repository call runs on its own connection in autocommit mode. Every argument
 * arrives as a {@code String} (or a boxed number) and is validated by {@link TaskRules}, which throws an
 * {@link IllegalArgumentException} naming the field and its accepted values.
 */
@ApplicationScoped
public class TaskQueries {

    @Inject
    TaskRepository tasks;

    @Inject
    TaskEventRepository events;

    /** The system clock, in the system zone; a field so that nothing else needs replacing in a test. */
    Clock clock = Clock.systemDefaultZone();

    /** Creates the query service; the container injects the repositories. */
    public TaskQueries() {}

    /**
     * Lists tasks, most urgent first (see {@link TaskRules#urgency(LocalDate)}).
     *
     * @param status {@code OPEN} or {@code DONE}, any case, or {@code null} for both
     * @param project a project name, or {@code null} for every project
     * @return the tasks
     * @throws IllegalArgumentException if the status or the project name is invalid
     */
    public List<TaskView> list(String status, String project) {
        TaskStatus s = TaskRules.status(status);
        String p = isBlank(project) ? null : TaskRules.project(project);
        List<Task> found;
        if (p != null && s != null) {
            found = tasks.findByProjectAndStatusOrderByDueDateAsc(p, s);
        } else if (s != null) {
            found = tasks.findByStatusOrderByDueDateAsc(s);
        } else if (p != null) {
            found = tasks.findByProjectOrderByIdAsc(p);
        } else {
            found = tasks.findAll().toList();
        }
        return views(found, Integer.MAX_VALUE);
    }

    /**
     * Finds a task.
     *
     * @param id the task id
     * @return the task, or empty
     */
    public Optional<TaskView> find(long id) {
        LocalDate today = today();
        return tasks.findById(id).map(t -> TaskRules.view(t, today));
    }

    /**
     * Finds a task with its history.
     *
     * @param id the task id
     * @return the task and its events, oldest first, or empty if no task has this id
     */
    public Optional<TaskDetails> details(long id) {
        return find(id).map(task -> new TaskDetails(task, history(id)));
    }

    /**
     * The history of a task. It outlives the task: after a delete it still lists every event, ending with
     * {@code DELETED}.
     *
     * @param id the task id
     * @return the events, oldest first; empty when no event was ever recorded for this id
     */
    public List<TaskEventView> history(long id) {
        return events.findByTaskIdOrderByIdAsc(id).stream()
                .map(TaskEventView::of)
                .toList();
    }

    /**
     * Lists the open tasks, most urgent first.
     *
     * @param project a project name, or {@code null} for every project
     * @param priority the <em>minimum</em> priority, {@code LOW}, {@code MEDIUM} or {@code HIGH}, or {@code null}
     * @param dueBefore an ISO date: only tasks due on or before it, or {@code null} for any due date (a task
     *     with no due date is then excluded as soon as the filter is set)
     * @param limit the maximum number of tasks, default {@value TaskRules#DEFAULT_LIMIT}, at most
     *     {@value TaskRules#MAX_LIMIT}
     * @return the tasks
     * @throws IllegalArgumentException if an argument is invalid
     */
    public List<TaskView> openTasks(String project, String priority, String dueBefore, Integer limit) {
        String p = isBlank(project) ? null : TaskRules.project(project);
        TaskPriority min = TaskRules.priority(priority, null);
        LocalDate before = TaskRules.date(dueBefore, "dueBefore");
        int max = TaskRules.limit(limit);
        List<Task> open = p == null
                ? tasks.findByStatusOrderByDueDateAsc(TaskStatus.OPEN)
                : tasks.findByProjectAndStatusOrderByDueDateAsc(p, TaskStatus.OPEN);
        List<Task> kept = open.stream()
                .filter(t -> min == null || t.getPriority().rank() >= min.rank())
                .filter(t -> before == null || (t.getDueDate() != null && !t.getDueDate().isAfter(before)))
                .toList();
        return views(kept, max);
    }

    /**
     * Searches titles and descriptions, ignoring case.
     *
     * @param query at least 2 characters
     * @param includeDone {@code true} to include done tasks
     * @param limit the maximum number of tasks, as in {@link #openTasks}
     * @return the matching tasks, most urgent first
     * @throws IllegalArgumentException if the query is too short
     */
    public List<TaskView> search(String query, boolean includeDone, Integer limit) {
        String pattern = TaskRules.likePattern(query);
        int max = TaskRules.limit(limit);
        List<Task> found = tasks.searchText(pattern).stream()
                .filter(t -> includeDone || t.getStatus() == TaskStatus.OPEN)
                .toList();
        return views(found, max);
    }

    /**
     * Counts the tasks, as of today.
     *
     * @param project a project name, or {@code null} for every project
     * @return the counts
     * @throws IllegalArgumentException if the project name is invalid
     */
    public TaskStats stats(String project) {
        String p = isBlank(project) ? null : TaskRules.project(project);
        return TaskStats.of(tasks.findAll().toList(), today(), p);
    }

    /**
     * Counts the tasks of each project.
     *
     * @return one entry per project that has at least one task, sorted by name
     */
    public List<ProjectStats> projects() {
        return stats(null).projects();
    }

    private List<TaskView> views(List<Task> found, int max) {
        LocalDate today = today();
        return found.stream()
                .sorted(TaskRules.urgency(today))
                .limit(max)
                .map(t -> TaskRules.view(t, today))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
