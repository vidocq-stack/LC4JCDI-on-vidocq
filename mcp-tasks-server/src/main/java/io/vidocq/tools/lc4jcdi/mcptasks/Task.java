package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A task, mapped by Mansart Data to the {@code tasks} table created by {@code V1__create_tasks.sql}.
 *
 * <p>Only the Jakarta Persistence annotations are used: Mansart reads them at compile time and generates the
 * mapping, there is no JPA provider. Column names are spelled out even where the snake_case convention would
 * derive the same name, so the mapping can be read against the SQL script.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 80)
    private String project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Creates an empty task; Mansart uses it to materialise rows. */
    public Task() {}

    /**
     * The generated identifier.
     *
     * @return the id, {@code null} until the task is first saved
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the identifier.
     *
     * @param id the id
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * The title.
     *
     * @return the title, 1 to 200 characters
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * The description.
     *
     * @return the description, or {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the description, or {@code null}
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * The project the task belongs to.
     *
     * @return the lower-case project name
     */
    public String getProject() {
        return project;
    }

    /**
     * Sets the project.
     *
     * @param project the lower-case project name
     */
    public void setProject(String project) {
        this.project = project;
    }

    /**
     * The status.
     *
     * @return {@link TaskStatus#OPEN} or {@link TaskStatus#DONE}
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * Sets the status.
     *
     * @param status the status
     */
    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    /**
     * The priority.
     *
     * @return the priority
     */
    public TaskPriority getPriority() {
        return priority;
    }

    /**
     * Sets the priority.
     *
     * @param priority the priority
     */
    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    /**
     * The due date.
     *
     * @return the due date, or {@code null} when the task has none
     */
    public LocalDate getDueDate() {
        return dueDate;
    }

    /**
     * Sets the due date.
     *
     * @param dueDate the due date, or {@code null}
     */
    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    /**
     * When the task was created.
     *
     * @return the creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation time.
     *
     * @param createdAt the creation time
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * When the task last changed.
     *
     * @return the time of the last change
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the time of the last change.
     *
     * @param updatedAt the time of the last change
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * When the task was marked done.
     *
     * @return the completion time, or {@code null} while the task is open
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * Sets the completion time.
     *
     * @param completedAt the completion time, or {@code null}
     */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
