#!/bin/sh
# Start the Vidocq-hosted MCP time server on http://localhost:8080/mcp.
#
# Why this script exists instead of target/<dist>/bin/<app>.sh (README.md, "Workarounds", 3):
#
#   `vidocq:package` puts only the application jar in app/ and everything else in lib/, and the
#   generated launcher starts McpTimeServerApp from one flat module path (lib and app). Vidocq
#   0.4.0-SNAPSHOT then moves the application module and the explicit modules it detects as part of the
#   application (dev.langchain4j.cdi.mcp.server, dev.langchain4j.cdi.mcp.invoker.cdi41) into the Vauban
#   child layer, but keeps the automatic module mcp-server-api in the BOOT layer.
#   `org.mcpjava.server.spi.McpServerSPILoader` resolves its provider with
#   `ServiceLoader.load(McpServerSPI.class, McpServerSPI.class.getClassLoader())`, the class loader of
#   mcp-server-api. From there it cannot see the provider (`dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI`)
#   in the CHILD layer, and the copy of the MCP server that also stays in the boot layer declares that
#   provider only in META-INF/services, which ServiceLoader ignores for a named module. Every call whose
#   result is built through the org.mcpjava factories (ToolResponse, PromptResponse, TextContent) then
#   fails with "No McpServerSPI implementation found": ./test-mcp.sh passes 4 of its 7 checks, because
#   convert_time and plan_meeting fail while current_time and the time://zone/{zone} resource work.
#   Reported upstream: https://github.com/mcp-java/java-mcp-annotations/issues/71
#
#   The fix is pure packaging: move langchain4j-cdi-mcp-server and mcp-server-api into app/ and boot
#   through the universal loader (-Dvidocq.app.path), so service and provider share one layer (7 of 7).
#
#   langchain4j-cdi-mcp-invoker-cdi41 must move with them. Its module requires both, and this script keeps
#   lib/ on the module path with --add-modules ALL-MODULE-PATH. Left in lib/, the invoker stops the JVM
#   before main with "java.lang.module.FindException: Module dev.langchain4j.cdi.mcp.server not found,
#   required by dev.langchain4j.cdi.mcp.invoker.cdi41". In app/, the synthetic McpInvokerProvider bean it
#   registers implements the interface of the MCP server in the same layer.
#
# Usage:  ./run.sh [&]     (JAVA_HOME must point at a JDK 25+)
#         JAVA_OPTS="-Dvidocq.chappe.listener.default.port=8081" ./run.sh     (another port)
set -e
BASE=$(cd "$(dirname "$0")" && pwd)
DIST=$(ls -d "$BASE"/target/mcp-time-server-*/ 2>/dev/null | head -1)
if [ -z "$DIST" ]; then
    echo "No distribution found. Run 'mvn package' from the repository root first." >&2
    exit 1
fi
DIST=${DIST%/}
for jar in langchain4j-cdi-mcp-server mcp-server-api langchain4j-cdi-mcp-invoker-cdi41; do
    f=$(ls "$DIST"/lib/"$jar"-*.jar 2>/dev/null | head -1)
    [ -n "$f" ] && mv "$f" "$DIST/app/"
done
exec "${JAVA_HOME:-/usr}/bin/java" \
    --module-path "$DIST/lib" \
    --add-modules ALL-MODULE-PATH \
    -Dvidocq.app.path="$DIST/app" \
    ${JAVA_OPTS:-} \
    -m io.vidocq.runtime.core/io.vidocq.runtime.core.Vidocq "$@"
