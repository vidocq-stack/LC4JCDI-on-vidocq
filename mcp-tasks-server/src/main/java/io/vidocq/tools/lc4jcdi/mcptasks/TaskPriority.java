package io.vidocq.tools.lc4jcdi.mcptasks;

/** How much a task matters. Stored by name in the {@code priority} column; {@link #rank()} orders them. */
public enum TaskPriority {
    /** Can wait. */
    LOW(1),
    /** The default. */
    MEDIUM(2),
    /** Comes first among tasks that are not overdue. */
    HIGH(3);

    private final int rank;

    TaskPriority(int rank) {
        this.rank = rank;
    }

    /**
     * The order of the priorities, higher meaning more important.
     *
     * @return 1 for {@link #LOW}, 2 for {@link #MEDIUM}, 3 for {@link #HIGH}
     */
    public int rank() {
        return rank;
    }
}
