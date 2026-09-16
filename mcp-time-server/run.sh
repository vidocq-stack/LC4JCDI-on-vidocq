#!/bin/sh
# Start the Vidocq-hosted MCP time server on http://localhost:8080/mcp.
#
# Why this script exists instead of target/<dist>/bin/<app>.sh:
#
#   `vidocq:package` puts only the application jar in app/ and everything else in lib/, and the
#   generated launcher boots the trampoline straight from the boot layer. Vidocq then re-layers ONLY the
#   application jar into the Vauban child layer. `org.mcpjava.server.spi.McpServerSPILoader` resolves its
#   provider with `ServiceLoader.load(McpServerSPI.class, McpServerSPILoader.class.getClassLoader())` —
#   the class loader of mcp-server-api, which stays in the BOOT layer — so it cannot see the provider
#   (`dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI`) that lives in the CHILD layer. Every tool,
#   prompt and resource invocation then fails with "No McpServerSPI implementation found".
#   Reported upstream: https://github.com/mcp-java/java-mcp-annotations/issues/71
#
#   The fix is pure packaging: move langchain4j-cdi-mcp-server and mcp-server-api into app/ and boot
#   through the universal loader (-Dvidocq.app.path), so service and provider share one layer.
#
#   langchain4j-cdi-mcp-invoker-cdi41 is moved for the same reason: Vauban discovers build-compatible
#   extensions with ServiceLoader inside the application layer, and the synthetic McpInvokerProvider bean it
#   registers must implement the very McpInvokerProvider interface that the app-layer MCP server injects. Left
#   in lib/ (the boot layer) the extension is never seen and every MCP method silently keeps using reflection.
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
