package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * The REST API of the projects, served by Cassini. A project is not a table: it is the name the tasks share.
 *
 * <p>As in {@link TaskResource}, this class is not transactional: {@link TaskService#renameProject} is, and the
 * exceptions it throws are turned into a 400 or a 404 after its transaction has rolled back.
 */
@ApplicationScoped
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    TaskService service;

    @Inject
    TaskQueries queries;

    /** Creates the resource; the container injects the services. */
    public ProjectResource() {}

    /**
     * {@code GET /projects}: the task counts of each project.
     *
     * @return one entry per project that has at least one task, sorted by name
     */
    @GET
    public List<ProjectStats> list() {
        return queries.projects();
    }

    /**
     * {@code POST /projects/{name}/rename}: moves every task of a project to another project, in one transaction
     * — a set-based JDQL {@code UPDATE}, then one {@code UPDATED} event per moved task.
     *
     * @param name the current project name
     * @param request {@code {"newName": "..."}}
     * @return 200 with {@code {"renamed": n}}, 400 for a missing or invalid name, or 404 when no task belongs to
     *     the project
     */
    @POST
    @Path("/{name}/rename")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response rename(@PathParam("name") String name, RenameRequest request) {
        if (request == null) {
            return ErrorResponse.badRequest("a body is required: {\"newName\": \"...\"}");
        }
        try {
            long moved = service.renameProject(name, request.newName());
            return Response.ok(Map.of("renamed", moved)).build();
        } catch (ProjectNotFoundException e) {
            return ErrorResponse.notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(e.getMessage());
        }
    }
}
