package io.vidocq.tools.lc4jcdi.mcptasks;

import io.vidocq.runtime.core.Vidocq;
import io.vidocq.runtime.spi.VidocqApp;
import io.vidocq.runtime.spi.VidocqMain;

/**
 * Entry point of the Vidocq-hosted MCP tasks server.
 *
 * <p>The class is a {@link VidocqMain} trampoline: {@code main} contains nothing but {@code Vidocq.run(...)},
 * because the runtime re-resolves the application into a child module layer defined by the Vauban class loader
 * (client-proxy weaving happens at class definition time) and only then calls {@link #run} inside that layer.
 *
 * <p>Before the container starts, the migration extension applies {@code db/migration} to the PostgreSQL database named
 * by {@code vidocq.pool.url} (a dev service container under {@code vidocq:dev}); the Mansart pool then serves it as the {@code @Default} {@code DataSource}. The
 * server listens on {@code http://127.0.0.1:18090} ({@code vidocq.properties}).
 */
@VidocqMain
public class McpTasksServerApp implements VidocqApp {

    /** Creates a new instance; invoked reflectively by the runtime inside the Vauban layer. */
    public McpTasksServerApp() {}

    /**
     * Boots the Vidocq runtime.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        Vidocq.run(McpTasksServerApp.class, args);
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
