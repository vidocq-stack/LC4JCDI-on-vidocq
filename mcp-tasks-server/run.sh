#!/bin/sh
# Start the Vidocq-hosted MCP tasks server on http://127.0.0.1:18090 (REST at /tasks, MCP at /mcp).
#
# The JVM starts in this module's directory, whatever the caller's: vidocq.pool.url names the H2 database file
# relative to it (./target/h2/tasks), as vidocq:dev does, so both launches share one database.
#
# Why this script exists instead of target/<dist>/bin/<app>.sh (see mcp-time-server/run.sh for the whole story
# and ../README.md, "Workarounds"): `vidocq:package` puts only the application jar in app/, and a flat launch
# keeps the automatic module mcp-server-api in the BOOT layer, where org.mcpjava's McpServerSPILoader cannot see
# the MCP server's provider in the CHILD layer. Moving langchain4j-cdi-mcp-server, mcp-server-api and
# langchain4j-cdi-mcp-invoker-cdi41 into app/ and booting through the universal loader (-Dvidocq.app.path) puts
# service and provider in one layer. The invoker must move with them: its module requires both, and left in lib/
# it stops the JVM before main with "Module dev.langchain4j.cdi.mcp.server not found".
#
# Usage:  ./run.sh [&]     (JAVA_HOME must point at a JDK 25+)
#         JAVA_OPTS="-Dvidocq.chappe.listener.default.port=18093" ./run.sh     (another port)
set -e
BASE=$(cd "$(dirname "$0")" && pwd)
DIST=$(ls -d "$BASE"/target/mcp-tasks-server-*/ 2>/dev/null | head -1)
if [ -z "$DIST" ]; then
    echo "No distribution found. Run 'mvn package' on this module (or from the repository root) first." >&2
    exit 1
fi
DIST=${DIST%/}
for jar in langchain4j-cdi-mcp-server mcp-server-api langchain4j-cdi-mcp-invoker-cdi41; do
    f=$(ls "$DIST"/lib/"$jar"-*.jar 2>/dev/null | head -1)
    [ -n "$f" ] && mv "$f" "$DIST/app/"
done
# Flyway reads the scripts from the source tree: classpath:db/migration is invisible from the boot layer, and
# the property key vidocq.migration.locations fails the boot in Vidocq 0.4.0-SNAPSHOT (vidocq.properties says
# why). The configuration resolves this environment variable for that key instead.
export VIDOCQ_MIGRATION_LOCATIONS="${VIDOCQ_MIGRATION_LOCATIONS:-filesystem:$BASE/src/main/resources/db/migration}"
cd "$BASE"
exec "${JAVA_HOME:-/usr}/bin/java" \
    --module-path "$DIST/lib" \
    --add-modules ALL-MODULE-PATH \
    -Dvidocq.app.path="$DIST/app" \
    ${JAVA_OPTS:-} \
    -m io.vidocq.runtime.core/io.vidocq.runtime.core.Vidocq "$@"
