package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.mcpjava.server.resources.Resource;
import org.mcpjava.server.resources.ResourceTemplate;
import org.mcpjava.server.resources.ResourceTemplateArg;

/**
 * The MCP resources: one task, {@code task://{id}}, and the counts of every project, {@code tasks://summary}.
 *
 * <p>Both answer JSON text, which the MCP server sends as the resource's text with the declared MIME type. A
 * resource has no {@code isError} result: reading a URI that names no task throws, and the MCP server answers the
 * {@code resources/read} with a JSON-RPC error carrying the message.
 */
@ApplicationScoped
public class TaskResources {

    @Inject
    TaskQueries queries;

    /** Creates the resources; the container injects the queries. */
    public TaskResources() {}

    /**
     * {@code task://{id}}: a task and its history.
     *
     * <p>The MCP server parses the {@code {id}} URI variable, a string on the wire, into this {@code long}
     * parameter, and rejects a non-numeric one before this method runs.
     *
     * @param id the id in the URI
     * @return the {@link TaskDetails} as JSON
     * @throws TaskNotFoundException if no task has this id
     */
    @ResourceTemplate(
            name = "task",
            description = "A task and its history, oldest event first, by id: task://3.",
            uriTemplate = "task://{id}",
            mimeType = "application/json")
    public String task(@ResourceTemplateArg(name = "id") long id) {
        return TaskJson.of(queries.details(id).orElseThrow(() -> new TaskNotFoundException(id)));
    }

    /**
     * {@code tasks://summary}: the counts of every project, as of today.
     *
     * @return the {@link TaskStats} as JSON
     */
    @Resource(
            name = "tasks-summary",
            description = "The counts of the tasks as of today: open, done, overdue, due today and in the next 7"
                    + " days, per priority and per project.",
            uri = "tasks://summary",
            mimeType = "application/json")
    public String summary() {
        return TaskJson.of(queries.stats(null));
    }
}
