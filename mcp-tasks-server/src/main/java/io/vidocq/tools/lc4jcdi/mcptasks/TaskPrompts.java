package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;

/**
 * The MCP prompts: ready-made requests that put the current tasks in front of the model.
 *
 * <p>Each prompt returns a {@code String}, which the MCP server sends as one {@code user} message; the text itself
 * is built by {@link TaskPromptText}. A prompt argument is a string on the wire, but the MCP server parses it into
 * the parameter's declared type: {@code hours} is an {@code int} whose {@code @PromptArg(defaultValue = ...)}
 * supplies {@value TaskPromptText#DEFAULT_HOURS} when it is omitted. {@code project} stays a {@code String} because
 * project names are free text. An invalid argument throws, and the MCP server answers the {@code prompts/get} with
 * a JSON-RPC error carrying the message.
 */
@ApplicationScoped
public class TaskPrompts {

    @Inject
    TaskQueries queries;

    /** The system clock, in the system zone, as {@link TaskQueries} uses it. */
    Clock clock = Clock.systemDefaultZone();

    /** Creates the prompts; the container injects the queries. */
    public TaskPrompts() {}

    /**
     * {@code plan_my_day}: asks the model to plan the day around the open tasks, overdue ones first.
     *
     * @param project a project name, or {@code null} for every project
     * @param hours the hours available, 1 to {@value TaskPromptText#MAX_HOURS}, {@value TaskPromptText#DEFAULT_HOURS}
     *     when omitted
     * @return the text of the user message
     * @throws IllegalArgumentException if the project name or the hours are invalid
     */
    @Prompt(
            name = "plan_my_day",
            description = "Plan today's work around the open tasks: overdue tasks first, the rest scheduled into the"
                    + " hours available, with breaks, and what does not fit deferred.")
    public String planMyDay(
            @PromptArg(
                            name = "project",
                            description = "Only plan the tasks of this project. Every project when omitted.",
                            required = false)
                    String project,
            @PromptArg(
                            name = "hours",
                            description = "The hours available today, 1 to " + TaskPromptText.MAX_HOURS + ".",
                            defaultValue = "" + TaskPromptText.DEFAULT_HOURS)
                    int hours) {
        int available = TaskPromptText.hours(hours);
        String p = project == null || project.isBlank() ? null : TaskRules.project(project);
        return TaskPromptText.planMyDay(LocalDate.now(clock), available, p, queries.list(TaskStatus.OPEN.name(), p));
    }

    /**
     * {@code review_project}: asks the model for a status summary, the risks and the next three actions of a
     * project.
     *
     * @param project the project name
     * @return the text of the user message
     * @throws IllegalArgumentException if the project is missing, invalid, or has no task; the message lists the
     *     projects that have tasks
     */
    @Prompt(
            name = "review_project",
            description = "Review one project: where it stands, its risks (overdue or high-priority tasks), and the"
                    + " next three actions.")
    public String reviewProject(
            @PromptArg(name = "project", description = "The project to review, such as 'lc4jcdi'.", required = true)
                    String project) {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("project is required: one of " + knownProjects());
        }
        String p = TaskRules.project(project);
        List<TaskView> tasks = queries.list(null, p);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("project '" + p + "' has no task: use one of " + knownProjects());
        }
        return TaskPromptText.reviewProject(p, LocalDate.now(clock), tasks);
    }

    private String knownProjects() {
        String names = queries.projects().stream().map(ProjectStats::project).collect(Collectors.joining(", "));
        return names.isEmpty() ? "(no project has a task yet)" : names;
    }
}
