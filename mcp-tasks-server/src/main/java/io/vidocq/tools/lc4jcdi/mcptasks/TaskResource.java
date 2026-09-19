package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * The REST API of the tasks, served by Cassini: every write, and the simple reads.
 *
 * <p>Writes go through {@link TaskService}, whose methods are {@code @Transactional}: a task row and its history
 * event commit together, and {@code POST /tasks/complete} is all or nothing. This class is deliberately
 * <em>not</em> transactional. It catches what the service throws <em>after</em> the transaction has rolled back,
 * and turns it into an answer: an {@link IllegalArgumentException} (a missing or invalid field) into a 400, an
 * unknown id or project into a 404, each with an {@link ErrorResponse} body that carries the message only.
 * Catching inside the transaction would commit what it had written.
 *
 * <p>Reads go through {@link TaskQueries}, which the MCP tools share.
 *
 * <p>{@code @Consumes} sits on the methods that read a body, not on the class: {@code POST
 * /tasks/{id}/complete} and the other body-less calls then match whatever {@code Content-Type} a client sends,
 * {@code curl -X POST} included. A body sent with another {@code Content-Type}, or with none, is answered 415
 * by Cassini before any method runs.
 *
 * <p>A JSON body that is empty, not valid JSON or not a JSON object never reaches these methods either:
 * Cassini's JSON-B reader fails, and Cassini (Vidocq 0.4.0-SNAPSHOT) answers {@code 500 text/plain} with the
 * parser's message, such as {@code Unexpected character: 'b' (at line 1, column 2, offset 2)} or {@code Empty
 * input (at line 1, column 0)}, and no stack trace. It is not mapped to a 400 here: an
 * {@code ExceptionMapper<JsonbException>} would also catch the serialization failures of the answers, which are
 * server errors.
 */
@ApplicationScoped
@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
public class TaskResource {

    @Inject
    TaskService service;

    @Inject
    TaskQueries queries;

    /** Creates the resource; the container injects the services. */
    public TaskResource() {}

    /**
     * {@code GET /tasks}: lists the tasks, most urgent first.
     *
     * @param status {@code OPEN} or {@code DONE}, any case, or absent for both
     * @param project a project name, or absent for every project
     * @return 200 with the tasks, or 400 for an unknown status or an invalid project name
     */
    @GET
    public Response list(@QueryParam("status") String status, @QueryParam("project") String project) {
        try {
            return Response.ok(queries.list(status, project)).build();
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(e.getMessage());
        }
    }

    /**
     * {@code GET /tasks/{id}}.
     *
     * @param id the task id
     * @return 200 with the task, or 404
     */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") long id) {
        return ok(queries.find(id), id);
    }

    /**
     * {@code GET /tasks/{id}/history}: the events of a task, oldest first. They outlive the task, so a deleted
     * task still answers, its last event being {@code DELETED}.
     *
     * @param id the task id
     * @return 200 with the events, or 404 when no event was ever recorded for this id
     */
    @GET
    @Path("/{id}/history")
    public Response history(@PathParam("id") long id) {
        List<TaskEventView> events = queries.history(id);
        if (events.isEmpty()) {
            return ErrorResponse.notFound("No history for task id " + id);
        }
        return Response.ok(events).build();
    }

    /**
     * {@code POST /tasks}: creates an open task and its {@code CREATED} event, in one transaction.
     *
     * @param input the task
     * @param uriInfo the request URI, to build the {@code Location} header; {@code null} gives a relative
     *     {@code tasks/<id>}
     * @return 201 with the task and its {@code Location}, or 400 if a field is missing or invalid
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(TaskInput input, @Context UriInfo uriInfo) {
        TaskView created;
        try {
            created = service.create(input);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(e.getMessage());
        }
        return Response.created(location(uriInfo, created.id())).entity(created).build();
    }

    /**
     * {@code PUT /tasks/{id}}: replaces the editable fields of a task (title, description, project, priority,
     * due date) and records an {@code UPDATED} event, in one transaction. A field left out takes its default, so
     * an omitted description or due date clears it. The status is not changed.
     *
     * @param id the task id
     * @param input the new fields
     * @return 200 with the task, 400 if a field is missing or invalid, or 404
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") long id, TaskInput input) {
        try {
            return ok(service.update(id, input), id);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(e.getMessage());
        }
    }

    /**
     * {@code POST /tasks/{id}/complete}: marks a task done and records a {@code COMPLETED} event. Idempotent: a
     * task already done is answered as it is, with no new event.
     *
     * @param id the task id
     * @return 200 with the task, or 404
     */
    @POST
    @Path("/{id}/complete")
    public Response complete(@PathParam("id") long id) {
        return ok(service.complete(id), id);
    }

    /**
     * {@code POST /tasks/{id}/reopen}: marks a task open again and records a {@code REOPENED} event. Idempotent.
     *
     * @param id the task id
     * @return 200 with the task, or 404
     */
    @POST
    @Path("/{id}/reopen")
    public Response reopen(@PathParam("id") long id) {
        return ok(service.reopen(id), id);
    }

    /**
     * {@code POST /tasks/complete}: completes several tasks in <em>one</em> transaction, all or nothing. When an
     * id matches no task, the transaction rolls back what the earlier ids had written, and the answer is a 404
     * that says so.
     *
     * @param request {@code {"ids": [...]}}
     * @return 200 with the completed tasks in the given order, 400 without ids, or 404 naming the unknown id
     */
    @POST
    @Path("/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response completeAll(CompleteRequest request) {
        try {
            return Response.ok(service.completeAll(request == null ? null : request.ids()))
                    .build();
        } catch (TaskNotFoundException e) {
            return ErrorResponse.notFound(e.getMessage() + " — nothing was completed");
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(e.getMessage() + ": {\"ids\": [1, 2]}");
        }
    }

    /**
     * {@code DELETE /tasks/{id}}: deletes a task. Its history stays, ending with a {@code DELETED} event
     * written in the same transaction.
     *
     * @param id the task id
     * @return 204, or 404
     */
    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) {
        if (!service.delete(id)) {
            return notFound(id);
        }
        return Response.noContent().build();
    }

    /**
     * The {@code Location} of a new task: absolute, from the request URI, when the runtime injects
     * {@link UriInfo}; otherwise relative, {@code tasks/<id>}, which a JAX-RS runtime resolves against the
     * application's base URI.
     */
    static URI location(UriInfo uriInfo, long id) {
        if (uriInfo == null) {
            return URI.create("tasks/" + id);
        }
        return uriInfo.getAbsolutePathBuilder().path(Long.toString(id)).build();
    }

    private static Response ok(Optional<?> found, long id) {
        return found.map(entity -> Response.ok(entity).build()).orElseGet(() -> notFound(id));
    }

    private static Response notFound(long id) {
        return ErrorResponse.notFound(new TaskNotFoundException(id).getMessage());
    }
}
