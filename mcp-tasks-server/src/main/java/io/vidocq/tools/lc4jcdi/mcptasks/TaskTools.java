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
 * <p>The arguments are {@code String}, {@code Integer}, {@code Long} or {@code Boolean}, and an omitted one arrives
 * as {@code null}: this MCP server converts neither enums nor dates, and never applies
 * {@code ToolArg.defaultValue}. {@link TaskRules} parses the strings and applies the defaults.
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
     * @param limit the maximum number of tasks, or {@code null} for {@value TaskRules#DEFAULT_LIMIT}
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
                            description = "The minimum priority: LOW, MEDIUM or HIGH. MEDIUM keeps the MEDIUM and"
                                    + " HIGH tasks. Any priority when omitted.",
                            required = false)
                    String priority,
            @ToolArg(
                            name = "dueBefore",
                            description = "An ISO date, yyyy-MM-dd: only the tasks due on or before it, which leaves"
                                    + " out the tasks without a due date. Any due date when omitted.",
                            required = false)
                    String dueBefore,
            @ToolArg(
                            name = "limit",
                            description = "The maximum number of tasks, 1 to " + TaskRules.MAX_LIMIT + ". "
                                    + TaskRules.DEFAULT_LIMIT + " when omitted.",
                            required = false)
                    Integer limit) {
        try {
            return list(queries.openTasks(project, priority, dueBefore, limit));
        } catch (IllegalArgumentException e) {
            return McpResults.error(e.getMessage());
        }
    }

    /**
     * {@code search_tasks}: the tasks whose title or description contains the query, ignoring case.
     *
     * @param query at least 2 characters
     * @param includeDone {@code true} to include the done tasks; {@code null} means {@code false}
     * @param limit the maximum number of tasks, or {@code null} for {@value TaskRules#DEFAULT_LIMIT}
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
            @ToolArg(
                            name = "includeDone",
                            description = "true to search the done tasks too. false when omitted.",
                            required = false)
                    Boolean includeDone,
            @ToolArg(
                            name = "limit",
                            description = "The maximum number of tasks, 1 to " + TaskRules.MAX_LIMIT + ". "
                                    + TaskRules.DEFAULT_LIMIT + " when omitted.",
                            required = false)
                    Integer limit) {
        try {
            return list(queries.search(query, Boolean.TRUE.equals(includeDone), limit));
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
