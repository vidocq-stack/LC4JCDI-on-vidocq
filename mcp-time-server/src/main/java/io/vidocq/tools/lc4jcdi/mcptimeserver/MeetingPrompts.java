package io.vidocq.tools.lc4jcdi.mcptimeserver;

import jakarta.enterprise.context.ApplicationScoped;
import org.mcpjava.server.Role;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.prompts.Prompt;
import org.mcpjava.server.prompts.PromptArg;
import org.mcpjava.server.prompts.PromptResponse;

/** MCP prompt that asks an assistant to propose a meeting slot across several time zones. */
@ApplicationScoped
public class MeetingPrompts {

    /** Creates a new instance; CDI requires a no-argument constructor. */
    public MeetingPrompts() {}

    /**
     * Builds a prompt asking an assistant to propose a meeting slot that fits everyone's working hours.
     *
     * @param zones a comma-separated list of IANA time zone ids, e.g. {@code "Europe/Paris,Asia/Tokyo,America/New_York"}
     * @param durationMinutes the desired meeting duration, in minutes
     * @return a single user-role prompt message
     */
    @Prompt(name = "plan_meeting", description = "Propose a meeting slot that fits everyone's working hours.")
    public PromptResponse planMeeting(
            @PromptArg(name = "zones", description = "Comma-separated IANA time zone ids.", required = true)
                    String zones,
            @PromptArg(name = "durationMinutes", description = "Meeting duration, in minutes.", required = true)
                    String durationMinutes) {
        String text =
                """
                Propose a meeting slot lasting %s minutes that fits the working hours (typically 09:00-18:00 \
                local time, on a weekday) of attendees in every one of the following time zones: %s.

                For each candidate slot, show the local start time in each of these zones, and explain any \
                zone where the slot falls outside typical working hours. Prefer a slot that works for everyone; \
                if none exists, pick the option with the smallest inconvenience and say so.
                """
                        .formatted(durationMinutes, zones);
        return PromptResponse.of(Role.USER, TextContent.of(text));
    }
}
