package io.vidocq.tools.lc4jcdi.mcptimeserver;

import io.vidocq.runtime.core.Vidocq;
import io.vidocq.runtime.spi.VidocqApp;
import io.vidocq.runtime.spi.VidocqMain;

/**
 * Entry point of the Vidocq-hosted MCP time server.
 *
 * <p>The class is a {@link VidocqMain} trampoline: {@code main} contains nothing but {@code Vidocq.run(...)},
 * because the runtime re-resolves the application into a child module layer defined by the Vauban class loader
 * (client-proxy weaving happens at class definition time) and only then calls {@link #run} inside that layer.
 *
 * <p>The MCP endpoint is served at {@code http://localhost:8080/mcp} by {@code McpEndpoint}, which lives in the
 * {@code langchain4j-cdi-mcp-server} jar and is discovered through {@code vidocq:generate}'s
 * {@code scanDependencies}.
 */
@VidocqMain
public class McpTimeServerApp implements VidocqApp {
    /**
     * Boots the Vidocq runtime.
     *
     * @param args command-line arguments
     */
    @SuppressWarnings("UnnecessaryModifier") // NEED for vidocq plugin
    public static void main(String[] args) {
        Vidocq.run(McpTimeServerApp.class, args);
    }

    /**
     * Blocks until the runtime is asked to exit; a server application has nothing else to do.
     *
     * @param args command-line arguments
     * @return the process exit code
     * @throws Exception if the runtime fails while waiting
     */
    @Override
    public int run(String... args) throws Exception {
        Vidocq.waitForExit();
        return 0;
    }
}
