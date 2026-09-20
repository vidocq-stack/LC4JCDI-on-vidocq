package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.mcpjava.server.tools.Tool;
import org.mcpjava.server.tools.ToolArg;
import org.mcpjava.server.tools.ToolResponse;

/**
 * The MCP tools: read-only queries on the tasks that the REST API writes.
 *
 * <p>Every tool answers JSON text whose root is an object ({@link TaskJson}), wrapped by {@link McpResults}, and
 * reports an invalid argument or an unknown id as an {@code isError} result carrying the message, never as an
 * exception: the model sees what was wrong and can try again.
 *
 * <p>Arguments use their natural type: {@link TaskPriority} for {@code priority}, and {@code int}/{@code boolean}
 * with an {@code @ToolArg(defaultValue = ...)} for {@code limit} and {@code includeDone}. The MCP server applies the
 * default and rejects a value of the wrong JSON type before this class ever runs; {@code id} stays a boxed
 * {@link Long} because a missing task id is business validation, not a type check, and {@code project} and
 * {@code dueBefore} stay {@code String} because the server does not convert free text or dates.
 * {@link TaskRules} still validates the fields it always has: the project name, the due date and the search query.
 *
 * <p>The MCP endpoint that hosts these tools lives in the {@code langchain4j-cdi-mcp-server} dependency jar, which
 * {@code vidocq:generate} indexes ({@code scanDependencies}); the bean is {@link ApplicationScoped} like every MCP
 * bean.
 */
@ApplicationScoped
public class TaskTools {

    @Inject
    TaskQueries queries;

    /** Creates the tools; the container injects the queries. */
    public TaskTools() {}

    /**
     * {@code list_open_tasks}: the open tasks, most urgent first.
     *
     * @param project a project name, or {@code null} for every project
     * @param priority the minimum priority, or {@code null}
     * @param dueBefore an ISO date, inclusive, or {@code null}
     * @param limit the maximum number of tasks, {@value TaskRules#DEFAULT_LIMIT} when omitted
     * @return {@code {"count": n, "tasks": [...]}}, or an error naming the invalid argument
     */
    @Tool(
            name = "list_open_tasks",
            description = "Lists the open tasks, most urgent first: overdue tasks, then higher priority, then earlier"
                    + " due date (tasks without one last). Answers {\"count\": n, \"tasks\": [...]}; each task has"
                    + " its id, title, description, project, priority, dueDate, overdue flag and timestamps.",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public ToolResponse listOpenTasks(
            @ToolArg(
                            name = "project",
                            description = "Only the tasks of this project, such as 'lc4jcdi'. Every project when"
                                    + " omitted.",
                            required = false)
                    String project,
            @ToolArg(
                            name = "priority",
                            description = "The minimum priority. MEDIUM keeps the MEDIUM and HIGH tasks. Any"
                                    + " priority when omitted.",
                            required = false)
                    TaskPriority priority,
            @ToolArg(
                            name = "dueBefore",
                            description = "An ISO date, yyyy-MM-dd: only the tasks due on or before it, which leaves"
                                    + " out the tasks without a due date. Any due date when omitted.",
                            required = false)
                    String dueBefore,
            @ToolArg(
                            name = "limit",
                            description = "The maximum number of tasks, 1 to " + TaskRules.MAX_LIMIT + ".",
                            defaultValue = "" + TaskRules.DEFAULT_LIMIT)
                    int limit) {
        try {
            return list(queries.openTasks(project, priority == null ? null : priority.name(), dueBefore, limit));
        } catch (IllegalArgumentException e) {
            return McpResults.error(e.getMessage());
        }
    }

    /**
     * {@code search_tasks}: the tasks whose title or description contains the query, ignoring case.
     *
     * @param query at least 2 characters
     * @param includeDone {@code true} to include the done tasks; {@code false} when omitted
     * @param limit the maximum number of tasks, {@value TaskRules#DEFAULT_LIMIT} when omitted
     * @return {@code {"count": n, "tasks": [...]}}, or an error when the query is too short
     */
    @Tool(
            name = "search_tasks",
            description = "Searches the titles and descriptions of the tasks for some words, ignoring case. Only the"
                    + " open tasks unless includeDone is true. Answers {\"count\": n, \"tasks\": [...]}, most"
                    + " urgent first.",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public ToolResponse searchTasks(
            @ToolArg(
                            name = "query",
                            description = "The words to look for, at least 2 characters. % and _ are not"
                                    + " wildcards: they are ignored.")
                    String query,
            @ToolArg(name = "includeDone", description = "true to search the done tasks too.", defaultValue = "false")
                    boolean includeDone,
            @ToolArg(
                            name = "limit",
                            description = "The maximum number of tasks, 1 to " + TaskRules.MAX_LIMIT + ".",
                            defaultValue = "" + TaskRules.DEFAULT_LIMIT)
                    int limit) {
        try {
            return list(queries.search(query, includeDone, limit));
        } catch (IllegalArgumentException e) {
            return McpResults.error(e.getMessage());
        }
    }

    /**
     * {@code task_statistics}: the counts of the tasks, as of today.
     *
     * @param project a project name, or {@code null} for every project
     * @return the {@link TaskStats}, or an error when the project name is invalid
     */
    @Tool(
            name = "task_statistics",
            description = "Counts the tasks as of today: total, open, done, overdue, due today, due in the next 7"
                    + " days, open tasks per priority, and open, done and overdue tasks per project.",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public ToolResponse taskStatistics(
            @ToolArg(
                            name = "project",
                            description = "Only count the tasks of this project. Every project when omitted.",
                            required = false)
                    String project) {
        try {
            return McpResults.json(TaskJson.of(queries.stats(project)));
        } catch (IllegalArgumentException e) {
            return McpResults.error(e.getMessage());
        }
    }

    /**
     * {@code get_task}: one task and its history.
     *
     * @param id the task id
     * @return the {@link TaskDetails}, or an error when the id is missing or matches no task
     */
    @Tool(
            name = "get_task",
            description = "Gets one task by id, with its history: when it was created, updated, completed or"
                    + " reopened, oldest first.",
            annotations = @Tool.Annotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public ToolResponse getTask(@ToolArg(name = "id", description = "The id of the task, such as 3.") Long id) {
        if (id == null) {
            return McpResults.error("id is required: the number of a task, such as 3");
        }
        return queries.details(id)
                .map(details -> McpResults.json(TaskJson.of(details)))
                .orElseGet(() -> McpResults.error(new TaskNotFoundException(id).getMessage()));
    }

    private static ToolResponse list(List<TaskView> tasks) {
        return McpResults.json(TaskJson.of(TaskList.of(tasks)));
    }
}
