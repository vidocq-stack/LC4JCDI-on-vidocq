package io.vidocq.tools.lc4jcdi.mcptasks;

/** What happened to a task. Each write of {@code TaskService} records one event per task it changes. */
public enum TaskEventType {
    /** The task was created. */
    CREATED,
    /** Its editable fields, or its project, changed. */
    UPDATED,
    /** It was marked done. */
    COMPLETED,
    /** It was marked open again. */
    REOPENED,
    /** It was deleted; the event outlives the task. */
    DELETED
}
