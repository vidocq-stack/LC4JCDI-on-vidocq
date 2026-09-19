package io.vidocq.tools.lc4jcdi.mcptasks;

/**
 * No task belongs to the given project.
 *
 * <p>Distinct from the {@link IllegalArgumentException} of an invalid project name, so that the REST layer can
 * answer 404 for an unknown project and 400 for a malformed one.
 */
public class ProjectNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String project;

    /**
     * Creates the exception.
     *
     * @param project the project name that matched no task
     */
    public ProjectNotFoundException(String project) {
        super("No task in project '" + project + "'");
        this.project = project;
    }

    /**
     * The project name that matched no task.
     *
     * @return the project name
     */
    public String project() {
        return project;
    }
}
