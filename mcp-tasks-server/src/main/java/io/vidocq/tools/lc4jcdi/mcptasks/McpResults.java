package io.vidocq.tools.lc4jcdi.mcptasks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.mcpjava.server.content.Annotations;
import org.mcpjava.server.content.ContentBlock;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.tools.ToolResponse;

/**
 * The results of the MCP tools, built by this module: its own implementations of the {@code org.mcpjava}
 * {@link ToolResponse} and {@link TextContent} interfaces.
 *
 * <p>Why not the API's static factories ({@code of}, {@code ofText}, {@code ofError}, {@code builder} on
 * {@code ToolResponse}, {@code TextContent} or {@code PromptResponse}): each of them looks up an
 * {@code McpServerSPI} provider through {@code McpServerSPILoader}. With a {@code langchain4j-cdi-mcp-server}
 * build whose {@code module-info} does not declare {@code provides org.mcpjava.server.spi.McpServerSPI}, that
 * lookup fails with {@code No McpServerSPI implementation found} as soon as the {@code mcp-server-api} module and
 * the MCP server sit in different module layers, as in an IDE-style launch of Vidocq 0.4.0-SNAPSHOT (the
 * repository's {@code README.md}, "Known limitation"; {@code langchain4j-cdi-mcp/README.md} advises implementing
 * the API interface directly). The MCP server serializes any {@link ToolResponse} and any {@link TextContent} by
 * interface, whatever their class, so these records go on the wire exactly as the factories' objects would, with
 * any build of the MCP server and in every launch. The prompts and resources of this module return a
 * {@code String}, which needs no factory at all.
 *
 * <p>No result carries {@code structuredContent}: the MCP 2025-03-26 protocol has none, and every tool of this
 * module answers JSON text already ({@link TaskJson}).
 */
public final class McpResults {

    private McpResults() {}

    /**
     * A successful tool result: one text block holding JSON.
     *
     * @param json the JSON text, whose root is an object
     * @return the result, with {@code isError} false
     */
    public static ToolResponse json(String json) {
        return new Result(List.of(new Text(json)), Optional.empty(), false, Map.of());
    }

    /**
     * A failed tool result: one text block holding a message for the model, never a stack trace. The model sees
     * it and can correct its arguments, where a JSON-RPC error would end the call.
     *
     * @param message what was wrong, such as the accepted values of an argument
     * @return the result, with {@code isError} true
     */
    public static ToolResponse error(String message) {
        return new Result(List.of(new Text(message)), Optional.empty(), true, Map.of());
    }

    /**
     * A {@link ToolResponse} that does not depend on any {@code McpServerSPI}.
     *
     * @param content the content blocks
     * @param structuredContent the structured content, empty in this module
     * @param isError whether the tool failed
     * @param metadata the {@code _meta} entries, empty in this module
     */
    public record Result(
            List<ContentBlock> content, Optional<Object> structuredContent, boolean isError, Map<String, Object> metadata)
            implements ToolResponse {

        /** Copies the lists and maps, and rejects {@code null}s. */
        public Result {
            content = List.copyOf(content);
            structuredContent = Objects.requireNonNull(structuredContent, "structuredContent");
            metadata = Map.copyOf(metadata);
        }
    }

    /**
     * A {@link TextContent} that does not depend on any {@code McpServerSPI}: no annotations, no metadata.
     *
     * @param text the text
     */
    public record Text(String text) implements TextContent {

        /** Rejects a {@code null} text. */
        public Text {
            Objects.requireNonNull(text, "text");
        }

        /**
         * No annotations.
         *
         * @return empty
         */
        @Override
        public Optional<Annotations> annotations() {
            return Optional.empty();
        }

        /**
         * No metadata.
         *
         * @return an empty map
         */
        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }
}
