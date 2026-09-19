package io.vidocq.tools.lc4jcdi.mcptasks;

/**
 * No task has the given id.
 *
 * <p>A {@link RuntimeException}, so a {@code @Transactional} method that throws it rolls back.
 */
public class TaskNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long id;

    /**
     * Creates the exception.
     *
     * @param id the id that matched no task
     */
    public TaskNotFoundException(long id) {
        super("No task with id " + id);
        this.id = id;
    }

    /**
     * The id that matched no task.
     *
     * @return the id
     */
    public long id() {
        return id;
    }
}
