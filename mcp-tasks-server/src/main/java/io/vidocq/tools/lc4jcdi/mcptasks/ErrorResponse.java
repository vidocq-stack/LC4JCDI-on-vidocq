package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The JSON body of every error the REST API answers itself: a short code and a message for a person, never a
 * stack trace.
 *
 * @param error {@value #BAD_REQUEST} (HTTP 400) or {@value #NOT_FOUND} (HTTP 404)
 * @param message what was wrong, naming the field and the accepted values for a 400
 */
public record ErrorResponse(String error, String message) {

    /** The code of a 400 answer: a field is missing or invalid. */
    public static final String BAD_REQUEST = "bad_request";

    /** The code of a 404 answer: no task, or no project, has the given id or name. */
    public static final String NOT_FOUND = "not_found";

    /**
     * A 400 answer.
     *
     * @param message what was wrong
     * @return the response
     */
    static Response badRequest(String message) {
        return answer(Response.Status.BAD_REQUEST, new ErrorResponse(BAD_REQUEST, message));
    }

    /**
     * A 404 answer.
     *
     * @param message what was not found
     * @return the response
     */
    static Response notFound(String message) {
        return answer(Response.Status.NOT_FOUND, new ErrorResponse(NOT_FOUND, message));
    }

    private static Response answer(Response.Status status, ErrorResponse body) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
