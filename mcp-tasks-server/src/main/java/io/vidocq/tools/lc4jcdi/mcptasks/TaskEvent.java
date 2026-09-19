package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One entry of a task's history, mapped to the {@code task_events} table.
 *
 * <p>{@code TaskService} writes it in the same transaction as the change it records. There is no foreign key
 * to {@code tasks}: the {@link TaskEventType#DELETED} event outlives the task.
 */
@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskEventType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 500)
    private String detail;

    /** Creates an empty event; Mansart uses it to materialise rows. */
    public TaskEvent() {}

    /**
     * Creates an event, not saved yet.
     *
     * @param taskId the task the event is about
     * @param type what happened
     * @param at when it happened
     * @param detail a short human-readable detail, or {@code null}
     */
    public TaskEvent(Long taskId, TaskEventType type, Instant at, String detail) {
        this.taskId = taskId;
        this.type = type;
        this.occurredAt = at;
        this.detail = detail;
    }

    /**
     * The generated identifier, which also orders the events of a task.
     *
     * @return the id, {@code null} until saved
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
     * The task the event is about.
     *
     * @return the task id
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * Sets the task the event is about.
     *
     * @param taskId the task id
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * What happened.
     *
     * @return the event type
     */
    public TaskEventType getType() {
        return type;
    }

    /**
     * Sets what happened.
     *
     * @param type the event type
     */
    public void setType(TaskEventType type) {
        this.type = type;
    }

    /**
     * When it happened.
     *
     * @return the time of the event
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Sets when it happened.
     *
     * @param occurredAt the time of the event
     */
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * A short human-readable detail, such as the fields an update changed.
     *
     * @return the detail, or {@code null}
     */
    public String getDetail() {
        return detail;
    }

    /**
     * Sets the detail.
     *
     * @param detail the detail, or {@code null}
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }
}
