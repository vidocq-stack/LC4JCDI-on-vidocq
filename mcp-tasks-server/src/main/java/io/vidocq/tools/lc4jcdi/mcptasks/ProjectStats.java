package io.vidocq.tools.lc4jcdi.mcptasks;

/**
 * Task counts for one project.
 *
 * @param project the project name
 * @param open the number of open tasks
 * @param done the number of done tasks
 * @param overdue the number of open tasks whose due date is before today
 */
public record ProjectStats(String project, long open, long done, long overdue) {}
