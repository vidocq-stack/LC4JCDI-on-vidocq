package io.vidocq.tools.lc4jcdi.mcptasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mcpjava.server.content.TextContent;
import org.mcpjava.server.tools.ToolResponse;

/**
 * {@link McpResults}: the tool results this module builds itself, without any {@code org.mcpjava} static factory.
 */
class McpResultsTest {

    /**
     * A call to one of the {@code org.mcpjava} static factories that look up an {@code McpServerSPI} provider. Split
     * so that this test's own source does not match it.
     */
    private static final Pattern FACTORY_CALL = Pattern.compile("(ToolResponse\\s*\\.\\s*(of\\w*|builder)"
            + "|PromptResponse\\s*\\.\\s*of"
            + "|TextContent\\s*\\.\\s*(of|builder)"
            + "|Icon\\s*\\.\\s*(of|builder))"
            + "\\s*\\(");

    @Test
    void jsonIsOneTextBlockAndNotAnError() {
        ToolResponse r = McpResults.json("{\"count\": 0}");
        assertFalse(r.isError());
        assertEquals(1, r.content().size());
        TextContent text = assertInstanceOf(TextContent.class, r.content().getFirst());
        assertEquals("{\"count\": 0}", text.text());
        assertTrue(text.annotations().isEmpty());
        assertTrue(text.metadata().isEmpty());
        assertTrue(r.structuredContent().isEmpty());
        assertTrue(r.metadata().isEmpty());
    }

    @Test
    void errorSetsIsErrorAndCarriesTheMessage() {
        ToolResponse r = McpResults.error("No task with id 42");
        assertTrue(r.isError());
        assertEquals(1, r.content().size());
        assertEquals(
                "No task with id 42",
                assertInstanceOf(TextContent.class, r.content().getFirst()).text());
        assertTrue(r.structuredContent().isEmpty());
    }

    @Test
    void noMainSourceCallsAnOrgMcpjavaFactory() throws IOException {
        Path main = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(main), "run from the module directory: " + main.toAbsolutePath());
        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files.filter(f -> f.toString().endsWith(".java"))
                    .flatMap(McpResultsTest::factoryCalls)
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static Stream<String> factoryCalls(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size())
                    .filter(i -> FACTORY_CALL.matcher(lines.get(i)).find())
                    .mapToObj(i -> file + ":" + (i + 1) + ": " + lines.get(i).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
