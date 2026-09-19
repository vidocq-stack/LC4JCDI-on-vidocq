package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How {@link ProjectResource} maps the service's outcomes to HTTP, on the seeded in-memory database. */
class ProjectResourceTest {

    private TasksDatabase db;
    private ProjectResource resource;

    @BeforeEach
    void migrate() {
        db = new TasksDatabase();
        resource = new ProjectResource();
        resource.service = db.service();
        resource.queries = db.queries();
    }

    @AfterEach
    void shutdown() throws Exception {
        db.close();
    }

    private static ErrorResponse error(Response r, int status, String code) {
        assertEquals(status, r.getStatus());
        ErrorResponse e = assertInstanceOf(ErrorResponse.class, r.getEntity());
        assertEquals(code, e.error());
        return e;
    }

    @Test
    void listCountsEveryProjectByName() {
        List<ProjectStats> projects = resource.list();
        assertEquals(
                List.of(
                        new ProjectStats("home", 1, 1, 0),
                        new ProjectStats("lc4jcdi", 3, 1, 0),
                        new ProjectStats("vidocq", 2, 0, 1)),
                projects);
    }

    @Test
    void renameAnswersTheNumberOfTasksMoved() {
        Response renamed = resource.rename("home", new RenameRequest("House"));
        assertEquals(200, renamed.getStatus());
        assertEquals(Map.of("renamed", 2L), renamed.getEntity());
        assertEquals(2, db.tasks.countByProject("house"));
    }

    @Test
    void renameAnswers404ForAnUnknownProjectAnd400ForABadName() {
        ErrorResponse unknown = error(resource.rename("nope", new RenameRequest("other")), 404, "not_found");
        assertEquals("No task in project 'nope'", unknown.message());
        error(resource.rename("home", new RenameRequest("not valid")), 400, "bad_request");
        error(resource.rename("home", new RenameRequest("HOME")), 400, "bad_request");
        error(resource.rename("home", new RenameRequest(null)), 400, "bad_request");
        ErrorResponse noBody = error(resource.rename("home", null), 400, "bad_request");
        assertTrue(noBody.message().contains("newName"), noBody.message());
        assertEquals(2, db.tasks.countByProject("home"));
    }
}
