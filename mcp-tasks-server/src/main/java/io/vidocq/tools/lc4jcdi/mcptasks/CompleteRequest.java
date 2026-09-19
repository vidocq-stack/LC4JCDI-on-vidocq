package io.vidocq.tools.lc4jcdi.mcptasks;

import java.util.List;

/**
 * The body of {@code POST /tasks/complete}: the tasks to complete together, all or nothing.
 *
 * @param ids the task ids, at least one, completed in this order
 */
public record CompleteRequest(List<Long> ids) {}
