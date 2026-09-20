/**
 * The MCP time server example, hosted on the Vidocq runtime.
 *
 * <p>Open so that Vauban, Cassini and the MCP server's reflective tool invoker (the fallback path when the
 * optional CDI 4.1 invoker misses) can reach the beans in this module.
 */
open module io.vidocq.tools.lc4jcdi.mcptimeserver {
    requires io.vidocq.runtime.core;

    // The application compiles against the MCP server's annotations and types; mcp.server.api comes with it.
    requires dev.langchain4j.cdi.mcp.server;

    // Vidocq.run instantiates the @VidocqMain trampoline reflectively.
    exports io.vidocq.tools.lc4jcdi.mcptimeserver;
}
