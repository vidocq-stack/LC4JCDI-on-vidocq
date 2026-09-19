package io.vidocq.tools.lc4jcdi.mcptasks;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * Renders the read models as JSON text for the MCP tools, resources and prompts.
 *
 * <p>An MCP tool that returns an object which is not a {@code ToolResponse} gets its {@code toString()} on the
 * wire, and a resource or a prompt only ever carries text, so this module serializes its records itself, with
 * JSON-B (Yasson), as Cassini does for the REST API: dates as {@code 2026-09-30}, instants as
 * {@code 2026-09-19T08:30:00Z}, enums by name, and {@code null} values left out. The output is indented, for the
 * model and for a person reading an Inspector transcript.
 */
public final class TaskJson {

    /** Thread-safe, and costly to create: one for the whole application. */
    private static final Jsonb JSONB = JsonbBuilder.create(new JsonbConfig().withFormatting(true));

    private TaskJson() {}

    /**
     * Serializes a value.
     *
     * @param value a record of this module, or a list of them
     * @return its JSON text
     */
    public static String of(Object value) {
        return JSONB.toJson(value);
    }
}
