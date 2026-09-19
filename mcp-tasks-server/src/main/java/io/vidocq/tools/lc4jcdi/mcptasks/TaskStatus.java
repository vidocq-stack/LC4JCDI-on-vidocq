package io.vidocq.tools.lc4jcdi.mcptasks;

/** Whether a task still has to be done. Stored by name in the {@code status} column. */
public enum TaskStatus {
    /** Still to do. */
    OPEN,
    /** Done; the task keeps its completion time. */
    DONE
}
