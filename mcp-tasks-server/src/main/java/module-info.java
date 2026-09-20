/**
 * The MCP tasks server example: a task tracker on an H2 file database, hosted on the Vidocq runtime.
 *
 * <p>Open, so that Vauban, Cassini, JSON-B, Flyway and the MCP server's reflective invoker can reach its
 * classes and resources. Being open, it needs no {@code opens} clause: {@code db.migration} is open too.
 */
open module io.vidocq.tools.lc4jcdi.mcptasks {
    requires java.logging;
    // Mansart's APT output (TaskRepositoryImpl, the metamodels) imports @Generated; SOURCE retention, so only
    // needed at compile time.
    requires static java.compiler;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.json;
    requires jakarta.json.bind;
    requires jakarta.ws.rs;
    requires jakarta.persistence;
    requires jakarta.data;
    requires jakarta.transaction;

    // The MCP server; brings mcp.server.api transitively.
    requires dev.langchain4j.cdi.mcp.server;
    requires mcp.server.api;


    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.spi;
    requires io.vidocq.runtime.extensions.jakartaee.core.cassini;
    // Cassini's APT output ($$CassiniAdapter) implements types of the Cassini API.
    requires io.vidocq.cassini.api;
    requires io.vidocq.chappe.api;
    requires io.vidocq.vauban.core;

    // Mansart: the pool (@Default DataSource), Jakarta Data repositories, JTA transactions.
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.pool;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.data;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.transactions;
    // The generated *RepositoryImpl classes use Mansart's RepositoryRuntime.
    requires io.vidocq.mansart.data.core;

    // Schema migrations: Flyway applies db/migration to the @Default datasource at boot.
    requires io.vidocq.runtime.extensions.essentials.migration;
    requires io.vidocq.runtime.extensions.essentials.migration.flyway;

    // Vidocq.run instantiates the @VidocqMain trampoline reflectively.
    exports io.vidocq.tools.lc4jcdi.mcptasks;
}
