package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The write side of the task tracker, used by the REST API.
 *
 * <p>Every public method is {@link Transactional} ({@code REQUIRED}): the Mansart transactions extension starts a
 * JTA transaction around the call, and the Mansart Data repositories enlist their connection in it, so a task row
 * and the {@link TaskEvent} that records its change commit together or not at all. A {@link RuntimeException}
 * thrown out of a method, such as {@link TaskNotFoundException} from {@link #completeAll}, rolls back everything
 * the method already wrote. Callers catch it <em>after</em> the rollback; catching it here would commit.
 *
 * <p>The MCP side never calls this class: it only reads, through {@link TaskQueries}.
 */
@ApplicationScoped
public class TaskService {

    @Inject
    TaskRepository tasks;

    @Inject
    TaskEventRepository events;

    /** The system clock, in the system zone; a field so that nothing else needs replacing in a test. */
    Clock clock = Clock.systemDefaultZone();

    /** Creates the service; the container injects the repositories. */
    public TaskService() {}

    /**
     * Creates an open task and its {@code CREATED} event.
     *
     * @param input the task; see {@link TaskInput} for the defaults
     * @return the new task
     * @throws IllegalArgumentException if a field is invalid; nothing is written
     */
    @Transactional
    public TaskView create(TaskInput input) {
        Task task = new Task();
        TaskRules.apply(input, task);
        Instant now = clock.instant();
        task.setStatus(TaskStatus.OPEN);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        Task saved = tasks.save(task);
        recordEvent(saved.getId(), TaskEventType.CREATED, now, "in " + saved.getProject());
        return view(saved);
    }

    /**
     * Replaces the editable fields of a task (title, description, project, priority, due date) and records an
     * {@code UPDATED} event naming what changed. A field left out takes its default, so an omitted description
     * or due date clears it. The status is not changed.
     *
     * @param id the task id
     * @param input the new fields
     * @return the updated task, or empty if no task has this id
     * @throws IllegalArgumentException if a field is invalid; nothing is written
     */
    @Transactional
    public Optional<TaskView> update(long id, TaskInput input) {
        Optional<Task> found = tasks.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Task task = found.get();
        String changes = TaskRules.changes(task, input);
        TaskRules.apply(input, task);
        Instant now = clock.instant();
        task.setUpdatedAt(now);
        Task saved = tasks.save(task);
        recordEvent(id, TaskEventType.UPDATED, now, changes);
        return Optional.of(view(saved));
    }

    /**
     * Marks a task done and records a {@code COMPLETED} event. A task that is already done is returned as it
     * is, with no new event.
     *
     * @param id the task id
     * @return the task, or empty if no task has this id
     */
    @Transactional
    public Optional<TaskView> complete(long id) {
        return tasks.findById(id).map(task -> view(completeTask(task)));
    }

    /**
     * Marks a task open again, clears its completion time and records a {@code REOPENED} event. A task that
     * is already open is returned as it is, with no new event.
     *
     * @param id the task id
     * @return the task, or empty if no task has this id
     */
    @Transactional
    public Optional<TaskView> reopen(long id) {
        return tasks.findById(id).map(task -> {
            if (task.getStatus() == TaskStatus.OPEN) {
                return view(task);
            }
            Instant now = clock.instant();
            task.setStatus(TaskStatus.OPEN);
            task.setCompletedAt(null);
            task.setUpdatedAt(now);
            Task saved = tasks.save(task);
            recordEvent(id, TaskEventType.REOPENED, now, null);
            return view(saved);
        });
    }

    /**
     * Deletes a task. Its history stays, ending with a {@code DELETED} event.
     *
     * @param id the task id
     * @return {@code true} if the task existed
     */
    @Transactional
    public boolean delete(long id) {
        Optional<Task> found = tasks.findById(id);
        if (found.isEmpty()) {
            return false;
        }
        recordEvent(id, TaskEventType.DELETED, clock.instant(), found.get().getTitle());
        tasks.deleteById(id);
        return true;
    }

    /**
     * Completes several tasks, all or nothing.
     *
     * <p>The ids are completed one after the other, in the given order, without checking them first. The first
     * id that matches no task throws {@link TaskNotFoundException}, and the transaction then rolls back what
     * the earlier ids already wrote: no task is completed, no event is recorded. The lack of a pre-check is
     * deliberate, so that the rollback can be observed.
     *
     * @param ids the task ids, at least one
     * @return the completed tasks, in the given order
     * @throws IllegalArgumentException if {@code ids} is {@code null} or empty
     * @throws TaskNotFoundException if an id matches no task
     */
    @Transactional
    public List<TaskView> completeAll(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must list at least one task id");
        }
        List<TaskView> completed = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("ids must not contain null");
            }
            Task task = tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
            completed.add(view(completeTask(task)));
        }
        return completed;
    }

    /**
     * Moves every task of a project to another project: one set-based JDQL {@code UPDATE}, then one
     * {@code UPDATED} event per moved task, in the same transaction.
     *
     * @param oldName the current project name
     * @param newName the new project name
     * @return the number of tasks moved
     * @throws IllegalArgumentException if a name is invalid or both names are the same project
     * @throws ProjectNotFoundException if no task belongs to {@code oldName}
     */
    @Transactional
    public long renameProject(String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("both the project and its new name are required");
        }
        String from = TaskRules.project(oldName);
        String target = TaskRules.project(newName);
        if (from.equals(target)) {
            throw new IllegalArgumentException("the new name of project '" + from + "' is the same name");
        }
        List<Long> ids =
                tasks.findByProjectOrderByIdAsc(from).stream().map(Task::getId).toList();
        if (ids.isEmpty()) {
            throw new ProjectNotFoundException(from);
        }
        Instant now = clock.instant();
        long moved = tasks.renameProject(from, target, now);
        String detail = "project: " + from + " → " + target;
        for (Long id : ids) {
            recordEvent(id, TaskEventType.UPDATED, now, detail);
        }
        return moved;
    }

    private Task completeTask(Task task) {
        if (task.getStatus() == TaskStatus.DONE) {
            return task;
        }
        Instant now = clock.instant();
        task.setStatus(TaskStatus.DONE);
        task.setCompletedAt(now);
        task.setUpdatedAt(now);
        Task saved = tasks.save(task);
        recordEvent(saved.getId(), TaskEventType.COMPLETED, now, null);
        return saved;
    }

    private void recordEvent(Long taskId, TaskEventType type, Instant at, String detail) {
        events.save(new TaskEvent(taskId, type, at, detail));
    }

    private TaskView view(Task task) {
        return TaskRules.view(task, LocalDate.now(clock));
    }
}
