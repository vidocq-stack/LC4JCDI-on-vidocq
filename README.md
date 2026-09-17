# LangChain4j CDI on Vidocq

Example applications that host [langchain4j-cdi](https://github.com/langchain4j/langchain4j-cdi) on the
[Vidocq](https://repo1.maven.org/maven2/io/vidocq/) runtime suite — **Vauban** (CDI 4.1 Lite, build-time bean
index, no reflection-based discovery), **Cassini** (Jakarta RESTful Web Services 4.0) and **Chappe** (HTTP).

This repository will grow to host several langchain4j-cdi examples on Vidocq over time. Today it has one:

| Module | What it shows |
|---|---|
| [`mcp-time-server`](mcp-time-server) | langchain4j-cdi's **MCP server** (tools, a resource template, a prompt) running on Vidocq, including reflection-free method invocation via `langchain4j-cdi-mcp-invoker-cdi41` |

## Prerequisites

- **JDK 25.** Vidocq 0.4.0-SNAPSHOT ships Java 25 class files (class file major version 69); its Maven plugin and
  runtime both refuse to run on an older JDK. Point `JAVA_HOME` at a JDK 25 install for every Maven and `java`
  command below, e.g. `JAVA_HOME=$(sdk home java 25-tem)`.
- **Maven** (plain `mvn` — this repository has no Maven wrapper and does not want one).
- **Node.js** (for `npx`, used only by `test-mcp.sh` to run the MCP Inspector CLI).

## Build

```bash
export JAVA_HOME=/path/to/jdk-25
mvn -B clean verify
```

This compiles every module, runs the unit tests (plain JUnit, no container), and produces a runnable
distribution for `mcp-time-server` under `mcp-time-server/target/mcp-time-server-<version>/`.

On an older JDK, the build fails fast with a clear sentence instead of the compiler's opaque
`release version 25 not supported`, thanks to a `maven-enforcer-plugin` `requireJavaVersion` rule in the root
POM:

```
[ERROR] Vidocq 0.4.0-SNAPSHOT ships Java 25 class files (class file major version 69) and its Maven plugin and
runtime both refuse to start on an older JDK. Building this repository requires JDK 25+: point JAVA_HOME at a JDK
25 install (for example "sdk use java 25-tem") and re-run.
```

## Run

```bash
cd mcp-time-server
JAVA_HOME=/path/to/jdk-25 ./run.sh
```

The MCP endpoint is served at `http://localhost:8080/mcp`. Do not use the generated
`target/<dist>/bin/mcp-time-server.sh` launcher directly — see "Workarounds" below for why `run.sh` exists.
Another port: `JAVA_OPTS="-Dvidocq.chappe.listener.default.port=8081" ./run.sh`.

## Running from an IDE

Launching `McpTimeServerApp` straight from IntelliJ (or any IDE) works, **provided the IDE build runs Maven's
lifecycle**. Otherwise the server starts, listens on 8080, and answers **404 to everything, `/mcp` included**.

Why: Vauban discovers beans at build time. The `vidocq:generate` goal (bound to `process-classes`) writes the
bean index (`META-INF/vauban-beans.list`) and client proxies into `target/classes`, and it scans the
`langchain4j-cdi-mcp-server` dependency so that `McpEndpoint` is part of that index. An IDE's own compiler
rebuilds `target/classes` without running any Maven goal, which wipes the index: Cassini then has no resource to
route, hence the 404.

What the IDE build already does, and what it cannot: the Vauban **annotation processor** runs inside the IDE's
compiler and indexes this module's own beans. Only `vidocq:generate` indexes beans that live in a *dependency*
jar — and `McpEndpoint` is one. Fix, from most to least comfortable:

- **IntelliJ, keeping its own fast build:** in the *Maven* tool window, open *mcp-time-server → Plugins → vidocq*,
  right-click **`vidocq:generate`** and choose **Execute After Build** (and **Execute After Rebuild**). IntelliJ then
  compiles as usual and runs only that goal afterwards. This works because `scanDependencies` is configured at
  plugin level in `mcp-time-server/pom.xml`: a goal run on its own uses Maven's `default-cli` execution, which
  would not inherit a configuration placed on the `generate` execution, and would silently index nothing.
- **IntelliJ, delegating everything:** *Settings → Build, Execution, Deployment → Build Tools → Maven → Runner →
  Delegate IDE build/run actions to Maven*, then rebuild. Slower, but no hook to maintain.
- **Any IDE:** run `mvn process-classes` before each launch, and make sure the IDE does not rebuild the module
  afterwards.

Whichever you pick, after a rebuild `mcp-time-server/target/classes/META-INF/vauban-beans.list` must exist and
contain `McpEndpoint`. The gap is tracked upstream in
[Vidocq/vidocq#83](https://codefloe.com/Vidocq/vidocq/issues/83), which also proposes letting the annotation
processor index dependency modules so that no Maven goal is needed at all.

Launched this way — every jar on one flat module path — the class-loader workaround described below is not
needed: the MCP API and its provider share the boot layer. Use JDK 25 or newer for the run configuration.

## Test end to end

```bash
./test-mcp.sh --start
```

Drives the running server with a real MCP client — the
[MCP Inspector CLI](https://www.npmjs.com/package/@modelcontextprotocol/inspector) (`npx -y
@modelcontextprotocol/inspector`, currently 2.6.0) — over `tools/list`, `tools/call`, `resources/templates/list`,
`resources/read` and `prompts/get`, asserting on response content (not just exit codes). `--start` builds
nothing; it starts the already-built server via `mcp-time-server/run.sh`, waits for the endpoint to answer, runs
every check, and always stops the server on exit, pass or fail. `--start` listens on the port of `MCP_URL` and
refuses to start if that port is already taken — so it can run next to a server you already have on 8080:

```bash
MCP_URL=http://localhost:8081/mcp ./test-mcp.sh --start
```

Without `--start`, it tests a server you started yourself (from `run.sh` or an IDE). A pre-flight MCP request
runs first, and reports a missing or broken endpoint once — with its likely cause — rather than failing every
check:

```bash
MCP_URL=http://localhost:8080/mcp ./test-mcp.sh
```

## The snapshot dependencies

`langchain4j-cdi-mcp-server` and `langchain4j-cdi-mcp-invoker-cdi41` are pinned, in one place
(`lc4jcdi.version` in the root POM), to `dev.langchain4j.cdi.mcp:*:1.4.0-SNAPSHOT` — **not** the `1.4.0` release.
The release predates the MCP `2026-07-28` protocol support (tool annotations, resource templates, MRTR) these
examples exercise, even though its version string sorts higher than the snapshot's. Move `lc4jcdi.version` to a
release coordinate once langchain4j-cdi publishes one that contains the MCP `2026-07-28` work.

Vidocq is pinned the same way (`vidocq.version`) to `0.4.0-SNAPSHOT`: its runtime, annotation processors and
`vidocq-runtime-maven-plugin`. Launched from a flat module path, as an IDE launches it, 0.4.0 moves
`langchain4j-cdi-mcp-invoker-cdi41` into the Vauban application layer together with the MCP server; 0.3.0 moved
only the MCP server, and every MCP invocation then failed with a `ClassCastException` on `McpInvokerProvider`
(see "Running from an IDE"). Move `vidocq.version` to `0.4.0` once Vidocq releases it.

Both snapshots are resolved from `https://central.sonatype.com/repository/maven-snapshots/`, declared in the root
POM with snapshots enabled and releases disabled — once under `<repositories>` for the dependencies and once
under `<pluginRepositories>`, because Maven resolves `vidocq-runtime-maven-plugin` from plugin repositories only.
No `settings.xml` change is needed.

## Workarounds

This module relies on three host-level workarounds, first needed on Vidocq 0.3.0 and kept on 0.4.0-SNAPSHOT.
None touch MCP server production code.

1. **`-Avauban.validation=false`** on the compiler (`mcp-time-server/pom.xml`). Vauban's build-time bean index
   is built from the packages a *scanned dependency* exports; the cross-module index that actually resolves
   beans living inside `langchain4j-cdi-mcp-server.jar` (the tool/prompt/resource registries and their
   `transport`-package collaborators) is only produced later, by `vidocq:generate` (`process-classes`).
   Bean-resolution validation is therefore deferred to the runtime container, where it succeeds — Vauban's own
   build warning suggests this exact flag. On 0.4.0-SNAPSHOT the module also compiles without it, because none of
   its beans injects a bean from the MCP server jar.
2. ~~Three `--add-exports` compiler flags~~ — **not needed.** An earlier measurement against langchain4j-cdi's
   MCP server found that its module descriptor exported only `…server.registry`, so no module-path consumer
   could compile against `…server.api`, `…server.protocol` or `…server.transport` without `--add-exports`.
   `1.4.0-SNAPSHOT` now exports all three packages, so this module needs none of them.
3. **`run.sh` moves `langchain4j-cdi-mcp-server`, `mcp-server-api` and `langchain4j-cdi-mcp-invoker-cdi41` from
   `lib/` into `app/` before starting the server.** This one is required. `vidocq:package` puts only the
   application jar in `app/`, and the generated `bin/mcp-time-server.sh` starts it from one flat module path
   (`lib` and `app`). Vidocq then moves the application module, and the explicit modules it detects as part of
   the application (`dev.langchain4j.cdi.mcp.server` and `dev.langchain4j.cdi.mcp.invoker.cdi41`), into the Vauban
   child layer, but keeps automatic modules such as `mcp-server-api` in the **boot** layer.
   `org.mcpjava.server.spi.McpServerSPILoader` resolves its provider with
   `ServiceLoader.load(McpServerSPI.class, McpServerSPI.class.getClassLoader())` — the class loader of
   `mcp-server-api`, in the boot layer. From there it cannot see the provider
   (`dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI`) of the **child** layer, and it skips the boot-layer copy
   of `dev.langchain4j.cdi.mcp.server`: a named module that declares its provider only in `META-INF/services`,
   which `ServiceLoader` ignores for named modules. Every call whose result is built through the `org.mcpjava`
   factories (`ToolResponse`, `PromptResponse`, `TextContent`) then fails with `No McpServerSPI implementation
   found`: with the generated launcher on 0.4.0-SNAPSHOT, `./test-mcp.sh` passes 4 checks of 7, and both
   `convert_time` checks and the `plan_meeting` check fail. Reported upstream as
   [mcp-java/java-mcp-annotations#71](https://github.com/mcp-java/java-mcp-annotations/issues/71). The fix is
   pure packaging: move the three jars into `app/` and boot through the universal loader
   (`-Dvidocq.app.path=app`), so the service interface and its provider share one layer. `langchain4j-cdi-mcp-invoker-cdi41`
   is moved for the analogous reason: Vauban discovers its build-compatible extension via `ServiceLoader` inside
   the application layer, and the synthetic bean it registers must implement the `McpInvokerProvider` interface
   that the app-layer MCP server injects — left in `lib/` (the boot layer), the extension is invisible and every
   MCP method silently falls back to reflection.

## Examples

### `mcp-time-server`

Four small, self-contained examples of the MCP server's feature surface, all about IANA time zones — see
[`mcp-time-server/README.md`](mcp-time-server/README.md).

## Pending owner decisions

- **License.** No `LICENSE` file is included; that choice is left to the repository owner.
- **CI.** No CI workflow is configured yet.
