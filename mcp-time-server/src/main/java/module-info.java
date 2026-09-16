/**
 * The MCP time server example, hosted on the Vidocq runtime.
 *
 * <p>Open so that Vauban, Cassini and the MCP server's reflective tool invoker (the fallback path when the
 * optional CDI 4.1 invoker misses) can reach the beans in this module.
 */
open module io.vidocq.tools.lc4jcdi.mcptimeserver {
    requires java.logging;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.json;
    requires jakarta.json.bind;
    requires jakarta.ws.rs;

    // The MCP server under test; brings mcp.server.api transitively.
    requires dev.langchain4j.cdi.mcp.server;
    requires mcp.server.api;

    // The optional reflection-free invocation module. Required explicitly (this module's beans never reference
    // it directly) so it is resolved into the Vauban child layer and its BuildCompatibleExtension is
    // discoverable there.
    requires dev.langchain4j.cdi.mcp.invoker.cdi41;

    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.spi;
    requires io.vidocq.runtime.extensions.jakartaee.core.cassini;
    requires io.vidocq.cassini.api;
    requires io.vidocq.chappe.api;
    requires io.vidocq.vauban.core;

    // Vidocq.run instantiates the @VidocqMain trampoline reflectively.
    exports io.vidocq.tools.lc4jcdi.mcptimeserver;
}
