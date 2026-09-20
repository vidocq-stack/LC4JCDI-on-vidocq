#!/bin/bash
# Opens the mcp-time-server example in the MCP Inspector web UI (@modelcontextprotocol/inspector, run via
# `npx -y`, pinned to the same 2.6.0 as test-mcp.sh), preconfigured for Streamable HTTP on MCP_URL.
#
# Usage:
#   ./run-inspector.sh [url]
#   MCP_URL=http://host:port/mcp ./run-inspector.sh
#
# Start the server first: mcp-time-server/run.sh, or the IDE. This script does not start it. It checks the
# endpoint once and warns if nothing answers, then starts the Inspector anyway, so you can start the server
# afterwards and click Connect. Ctrl+C stops the Inspector.
#
# Read by the Inspector itself:
#   CLIENT_PORT            port of the web UI (default 6274)
#   MCP_SANDBOX_PORT       port of the MCP Apps sandbox (default 6275)
#   MCP_AUTO_OPEN_ENABLED  set to false to keep the Inspector from opening a browser tab
#   MCP_APP_ORIGIN_PORT    port serving MCP Apps (default 6278); give a second Inspector instance another one
#
# The Inspector prints its URL with a fresh access token (MCP_INSPECTOR_API_TOKEN=...) and opens it. The UI
# shows the server card: switch it on to connect, then use the Tools, Prompts and Resources tabs.
set -u

INSPECTOR_PKG="@modelcontextprotocol/inspector@2.6.0"
MCP_URL="${MCP_URL:-http://localhost:8080/mcp}"

for arg in "$@"; do
    case "$arg" in
        http://* | https://*) MCP_URL="$arg" ;;
        *)
            echo "Unknown argument: $arg" >&2
            exit 1
            ;;
    esac
done

# --- Prerequisites -----------------------------------------------------------------------------

if ! command -v npx >/dev/null 2>&1 || ! command -v node >/dev/null 2>&1; then
    echo "FAIL: node or npx not found on PATH. Install Node.js 22.19 or newer and re-run." >&2
    exit 1
fi

# The Inspector 2.x declares "node": ">=22.19.0".
NODE_VERSION=$(node -p 'process.versions.node')
NODE_MAJOR=${NODE_VERSION%%.*}
NODE_MINOR=$(echo "$NODE_VERSION" | cut -d. -f2)
if [ "$NODE_MAJOR" -lt 22 ] || { [ "$NODE_MAJOR" -eq 22 ] && [ "$NODE_MINOR" -lt 19 ]; }; then
    echo "FAIL: the MCP Inspector 2.6.0 needs Node.js 22.19 or newer; found $NODE_VERSION." >&2
    exit 1
fi

# --- Pre-flight ----------------------------------------------------------------------------------
# Same probe as test-mcp.sh: a real MCP initialize request, because a Vidocq server whose bean index misses
# McpEndpoint still listens and answers 404. A failure only warns: the UI can connect later.

STATUS=$(curl -s -o /dev/null -m 3 -w '%{http_code}' -X POST "$MCP_URL" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
    -d '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"run-inspector.sh","version":"1"}}}')

case "$STATUS" in
    200)
        echo "MCP server found at $MCP_URL."
        ;;
    000)
        echo "WARNING: nothing is listening at $MCP_URL yet." >&2
        echo "         Start the server (mcp-time-server/run.sh, or the IDE), then click Connect in the UI." >&2
        ;;
    404)
        echo "WARNING: a server answers at $MCP_URL but exposes no MCP endpoint there (HTTP 404)." >&2
        echo "         It most likely runs from classes built without vidocq:generate." >&2
        echo "         See README.md, 'Running from an IDE'." >&2
        ;;
    *)
        echo "WARNING: $MCP_URL answered HTTP $STATUS to an MCP initialize request." >&2
        ;;
esac

# --- Inspector UI --------------------------------------------------------------------------------

echo "Starting the MCP Inspector UI ($INSPECTOR_PKG) for $MCP_URL on http://localhost:${CLIENT_PORT:-6274} ..."
exec npx -y "$INSPECTOR_PKG" --web --transport http --server-url "$MCP_URL"
