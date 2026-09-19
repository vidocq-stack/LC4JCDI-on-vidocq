package io.vidocq.tools.lc4jcdi.mcptasks;

import java.time.Instant;

/**
 * The read model of one history entry.
 *
 * @param type what happened
 * @param at when it happened
 * @param detail a short detail, or {@code null}
 */
public record TaskEventView(TaskEventType type, Instant at, String detail) {

    /**
     * Maps an event row.
     *
     * @param event the event
     * @return its view
     */
    public static TaskEventView of(TaskEvent event) {
        return new TaskEventView(event.getType(), event.getOccurredAt(), event.getDetail());
    }
}
