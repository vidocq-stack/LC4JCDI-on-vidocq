package io.vidocq.tools.lc4jcdi.mcptasks;

import java.util.List;

/**
 * A task together with its history, oldest event first.
 *
 * @param task the task
 * @param history its events, oldest first
 */
public record TaskDetails(TaskView task, List<TaskEventView> history) {}
