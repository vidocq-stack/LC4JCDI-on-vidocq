package io.vidocq.tools.lc4jcdi.mcptasks;

import java.util.List;

/**
 * A list of tasks as the MCP tools answer it: a JSON object rather than a bare array, so that every tool result
 * has an object at its root, and the model reads the count without counting.
 *
 * @param count the number of tasks in {@code tasks}
 * @param tasks the tasks, most urgent first
 */
public record TaskList(int count, List<TaskView> tasks) {

    /**
     * Wraps a list of tasks.
     *
     * @param tasks the tasks
     * @return the list and its size
     */
    public static TaskList of(List<TaskView> tasks) {
        return new TaskList(tasks.size(), List.copyOf(tasks));
    }
}
