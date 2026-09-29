# LangChain4j CDI on Vidocq

Example applications that host [langchain4j-cdi](https://github.com/langchain4j/langchain4j-cdi) on the
[Vidocq](https://repo1.maven.org/maven2/io/vidocq/) runtime suite — **Vauban** (CDI 4.1 Lite, build-time bean
index, no reflection-based discovery), **Cassini** (Jakarta RESTful Web Services 4.0) and **Chappe** (HTTP).

This repository will grow to host several langchain4j-cdi examples on Vidocq over time. Today it has two:

| Module | What it shows |
|---|---|
| [`mcp-time-server`](mcp-time-server) | langchain4j-cdi's **MCP server** (tools, a resource template, a prompt) running on Vidocq, including reflection-free method invocation via `langchain4j-cdi-mcp-invoker-cdi41` |
| [`mcp-tasks-server`](mcp-tasks-server) | A task tracker on **PostgreSQL** (a dev service container under `vidocq:dev`): a **Cassini REST** API writes the tasks through a **Mansart** pool, Jakarta Data repositories and `@Transactional` services, and langchain4j-cdi's **MCP server** reads them; Flyway migrations and the Vidocq **dev console** |

## Prerequisites

- **JDK 25.** Vidocq 0.4.0-SNAPSHOT ships Java 25 class files (class file major version 69); its Maven plugin and
  runtime both refuse to run on an older JDK. Point `JAVA_HOME` at a JDK 25 install for every Maven and `java`
  command below, e.g. `JAVA_HOME=$(sdk home java 25-tem)`.
- **Maven** (plain `mvn` — this repository has no Maven wrapper and does not want one).
- **Node.js 22.19 or newer** (for `npx` and `node`, used only by `test-mcp.sh`, `test-tasks.sh` and `run-inspector.sh`
  to run the MCP Inspector and read its JSON).

## Build

```bash
export JAVA_HOME=/path/to/jdk-25
mvn -nsu -B clean verify
```

This compiles every module, runs the unit tests (plain JUnit, no container), and produces a runnable
distribution for each server: `mcp-time-server/target/mcp-time-server-<version>/` and
`mcp-tasks-server/target/mcp-tasks-server-<version>/`. `-nsu` keeps a local install of Vidocq from being
replaced by an older snapshot (see "The snapshot dependencies").

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

```bash
cd mcp-tasks-server
JAVA_HOME=/path/to/jdk-25 ./run.sh
```

The tasks server keeps every port it opens in 18090-18099, on `127.0.0.1`: the REST API and the MCP endpoint on
`http://127.0.0.1:18090` (`/tasks`, `/projects`, `/mcp`), and, in a dev launch, the dev console on
`http://127.0.0.1:18092/`. Its [README](mcp-tasks-server/README.md) has the `vidocq:dev` command, with the debugger
on `127.0.0.1:18091`.

## Running from an IDE

An IDE starts `McpTimeServerApp` from its own build output, `mcp-time-server/target/classes`, with the dependency
jars on one flat module path. Two things must already be in that output, or the server starts, listens, and
answers **404 to everything, `/mcp` included**:

- this module's bean index and client proxies, which the Vauban and Cassini **annotation processors** write while
  the module compiles;
- the beans of the `langchain4j-cdi-mcp-server` **dependency jar**, `McpEndpoint` among them, which only the
  `vidocq:generate` goal indexes. Maven runs that goal at `process-classes`; an IDE's own build never does.

To check a launch, look for `McpEndpoint` in `mcp-time-server/target/classes/META-INF/vauban-beans.list` and for
`Application layer ready: modules [...]` in the server log. Even then, two examples fail in an IDE-style launch (one
flat module path, after `mvn process-classes`): see "Known limitation" at the end of this section.

### IntelliJ IDEA

Open the repository as a Maven project, then run or debug the shared **`McpTimeServerApp`** configuration. It is
stored in `.run/McpTimeServerApp.run.xml`, where IntelliJ picks up shared run configurations. It is set up to do
three things, which nobody has seen it do inside IntelliJ yet (see "Not verified inside IntelliJ yet" below):

1. **JDK.** It runs on the JDK that IntelliJ lists as `temurin-25`, the name IntelliJ gives an Eclipse Temurin 25
   that it downloads or detects (`.sdkmanrc` asks for `25-tem`). If your JDK 25 has another name, IntelliJ reports
   that it cannot find `temurin-25` and offers to look for one. You can also select your JDK 25 or newer in the
   configuration's JRE field.
2. **Build.** IntelliJ compiles the module. For that build to run the annotation processors, IntelliJ's Maven import
   must resolve every entry of `annotationProcessorPaths` as a jar, which is why `mcp-time-server/pom.xml` names the
   processor jars. With the `vidocq-runtime-*-codegen` POM aggregates used before, that resolution failed, and on
   Vidocq 0.3.0 IntelliJ then compiled without any processor.
3. **`vidocq:generate`.** A *Before launch* Maven step runs this goal alone on `mcp-time-server/pom.xml`. The goal
   indexes the MCP server jar. It also repairs this module's own bean index: an incremental build recompiles only
   the files you changed, and the annotation processor can then rewrite `META-INF/vauban-beans.list` with the beans
   of those files only. Measured from the command line, the goal alone takes under a second (0.74 to 0.76 s), and
   an index cut down to one entry is back to its 31 entries afterwards. The goal can run alone because
   `scanDependencies` is configured at plugin level in `mcp-time-server/pom.xml`.

The tasks server has its own shared configuration, **`McpTasksServerApp`** (`.run/McpTasksServerApp.run.xml`), set up
the same way. It also starts the JVM in `mcp-tasks-server/`, needs the local PostgreSQL its README describes, and passes
`--add-modules ALL-MODULE-PATH`. Its *Before launch* `vidocq:generate` step indexes the MCP server jar, but cannot
repair that module's own bean index; [its README](mcp-tasks-server/README.md#running-from-an-ide) says why, and what
was measured.

**Not verified inside IntelliJ yet.** The configuration file uses the format IntelliJ writes, and the processor jars
follow the code of IntelliJ's Maven import. Both were checked against the IntelliJ IDEA 2026.2 sources (build
262.10968.63). Inside IntelliJ, the only check so far is that a Maven reimport with the processor jars no longer
logs a resolution error. Nobody has built or run the project through this configuration yet, so these points
remain open:

- whether the import now gives the module a processor path, and IntelliJ's build runs the processors;
- whether the run icon next to `main` reuses the shared configuration ("Gutter icon" below);
- whether Vauban's javac weaving plugin runs during IntelliJ's build, which decides whether HotSwap fails as
  described in "Debug and HotSwap" below;
- how long the *Before launch* step takes inside IntelliJ, against under a second from the command line.

Things to know:

- **Maven runner JDK.** The *Before launch* goal runs on the JRE set in *Settings → Build, Execution, Deployment →
  Build Tools → Maven → Runner* (the project JDK by default). It must be 25 or newer.
- **Gutter icon.** According to IntelliJ's source code, the run icon next to `main` reuses the existing
  configuration for this class. If the `McpTimeServerApp` configuration you see has no `vidocq:generate` step under
  *Before launch*, it is a temporary configuration that IntelliJ created from an earlier click. Delete it and reopen
  the project, so that IntelliJ loads the shared configuration again.
- **Build without Run.** *Build Project* or *Rebuild Project* on its own can leave the bean index incomplete until
  the next launch through this configuration.
- **Renamed or deleted beans** stay in the bean index, because the goal only adds entries. *Rebuild Project* or
  `mvn clean`, followed by a launch, removes them.
- **Editing a `pom.xml`** needs *Sync Maven Changes*: the goal does not update IntelliJ's libraries.
- **Debug and HotSwap.** `vidocq:generate` also adds a constructor taking a `ProxyLink` to this module's
  normal-scoped beans, directly in `target/classes`. When you reload an edited bean class during a Debug session,
  IntelliJ probably compiles it without that constructor. The JVM then refuses the redefinition, because it would
  remove a method (`delete method not implemented`). This was measured through JDI with a class that lost such a
  constructor, and has not been observed in IntelliJ yet. Restart the Debug session instead: the restart runs the
  *Before launch* steps again. If you depend on HotSwap, delegate the build to Maven (below). Maven is expected to
  keep the constructor, but that is not verified either.
- **Multi-module applications.** The *Before launch* step runs Maven on one POM, without workspace resolution.
  Sibling modules then come from `~/.m2` and may be stale, and `vidocq:generate` does not repair a module that has
  no `scanDependencies`. This repository has a single application module. For a multi-module application, delegate
  the build to Maven.

**Delegating to Maven instead.** With *Settings → Build, Execution, Deployment → Build Tools → Maven → Runner →
Delegate IDE build/run actions to Maven*, every build runs the Maven lifecycle, `vidocq:generate` included. This is
slower, and it is a per-machine setting that git does not share.

### Eclipse

Import the repository as an existing Maven project (m2e). Then launch `McpTimeServerApp` with *Run As → Java
Application*, or pick the shared `mcp-time-server/McpTimeServerApp.launch` under *Run → Run Configurations*. For the
tasks server, pick `mcp-tasks-server/McpTasksServerApp.launch`, which sets the working directory and
`--add-modules ALL-MODULE-PATH`.

- The root POM sets `m2e.apt.activation` to `jdt_apt`. m2e-apt then runs the processors of
  `annotationProcessorPaths`, with `-Avauban.validation=false`, inside JDT's compiler.
- m2e runs `vidocq:generate` during workspace builds, incremental ones included. No lifecycle mapping covers that
  execution, so m2e executes it and only reports an informational marker. Do not add an `<ignore/>` mapping for it.
- m2e derives the project's JRE from `maven.compiler.release`, which gives JavaSE-25.

Nobody has tried this in Eclipse yet. Known risks:

- `vidocq:generate` rewrites files that JDT compiled without refreshing the workspace. The workspace can fall out of
  sync, and with *Refresh using native hooks or polling* the builds may loop.
- JDT's incremental builds may truncate the bean index, as IntelliJ's do.
- JDT does not run Vauban's javac weaving plugin, so Hot Code Replace of a bean class may fail as described for
  IntelliJ.

*Project → Clean* rebuilds everything, `vidocq:generate` included.

### Known limitation: `convert_time` and `plan_meeting` fail in an IDE-style launch

With Vidocq 0.4.0-SNAPSHOT, an IDE-style launch after `mvn process-classes`

```bash
java -p mcp-time-server/target/classes:<runtime dependency jars> \
    -m io.vidocq.tools.lc4jcdi.mcptimeserver/io.vidocq.tools.lc4jcdi.mcptimeserver.McpTimeServerApp
```

passes **4 of the 7** `./test-mcp.sh` checks:

| `test-mcp.sh` checks | Result |
|---|---|
| `tools/list`, `tools/call current_time`, `resources/templates/list`, `resources/read` | pass |
| both `tools/call convert_time` checks, `prompts/get plan_meeting` | fail: `Invocation failed: convertTime - No McpServerSPI implementation found.` (`planMeeting` for the prompt) |

That launch was run from a terminal. No launch from inside IntelliJ or Eclipse has been measured yet.

Only the client sees the error; the server logs nothing about it. `convert_time` and `plan_meeting` are the
examples that build their results through the `org.mcpjava` factories (`ToolResponse.ofText`,
`ToolResponse.ofError`, `PromptResponse.of`, `TextContent.of`), and those factories look up an `McpServerSPI`
provider. This is the failure described in "Workarounds" (3) below: on a flat module path, Vidocq moves the MCP
server into the Vauban application layer but keeps `mcp-server-api` in the boot layer, where the lookup cannot see
the provider. `run.sh` passes all 7 checks.

The fix belongs upstream, in langchain4j-cdi: `langchain4j-cdi-mcp-server` can declare its provider in its
`module-info.java`, so that the boot-layer copy of the module serves the lookup:

```java
provides org.mcpjava.server.spi.McpServerSPI with dev.langchain4j.cdi.mcp.server.spi.CdiMcpServerSPI;
```

With a `langchain4j-cdi-mcp-server` jar built that way, the same launch passes 7 of 7; built from the same sources
without that line, it passes 4 of 7. The other possible fix is a class-loader fallback in the MCP API itself
([mcp-java/java-mcp-annotations#71](https://github.com/mcp-java/java-mcp-annotations/issues/71)).

`mcp-tasks-server` does not have this problem: its tools implement `ToolResponse` themselves and its prompts and
resources return a `String`, so none of them needs an `McpServerSPI`. In the same IDE-style launch, with
`--add-modules ALL-MODULE-PATH`, every one of its MCP calls answers (measured from a terminal).

With Vidocq 0.3.0, the same launch passed only 2 of 7 checks. 0.3.0 moved the MCP server into the application layer
but left `langchain4j-cdi-mcp-invoker-cdi41` in the boot layer, and every tool, resource and prompt call failed with
a `ClassCastException` on `McpInvokerProvider`.

Launching from an IDE without any Maven step is tracked upstream in
[Vidocq/vidocq#83](https://codefloe.com/Vidocq/vidocq/issues/83).

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

The tasks server has its own script:

```bash
./test-tasks.sh --start
```

It starts the already-built `mcp-tasks-server` through its `run.sh`, as a dev launch on `127.0.0.1:18093` with the
dev console on 18094, and on a database file of its own, which it deletes on exit. Then it checks, with `curl` and
the same MCP Inspector CLI: the ports the server listens on, the MCP surface, a task written over REST and read
back over MCP, the rollback of an all-or-nothing bulk write, validation errors, a prompt, the dev console's pool
panel and its password redaction, and the data surviving a restart. `--start` is required, because the checks
count the seeded tasks of a fresh database and restart the server. `TASKS_URL` and `DEVCONSOLE_PORT` pick other
ports; it refuses 8080, 8888 and any port already taken, and always stops the server on exit.

## Explore in the MCP Inspector UI

```bash
./run-inspector.sh
```

Opens the [MCP Inspector](https://www.npmjs.com/package/@modelcontextprotocol/inspector) web UI (same pinned
2.6.0 as `test-mcp.sh`), preconfigured for Streamable HTTP on `http://localhost:8080/mcp`. Start the server
first, from `run.sh` or an IDE: this script does not start it. It sends one MCP request to the endpoint and warns
if nothing answers, then starts the Inspector anyway, so you can start the server afterwards.

The Inspector prints its URL, with a fresh access token, and opens it in your browser. Switch the server card on
to connect, then use the **Tools**, **Prompts** and **Resources** tabs. `Ctrl+C` stops the Inspector.

Another server URL, or another UI port:

```bash
CLIENT_PORT=6280 ./run-inspector.sh http://localhost:8081/mcp
```

`MCP_AUTO_OPEN_ENABLED=false` keeps the browser closed. The script header lists the other ports the Inspector
uses.

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

**The dev console is not a dependency.** Neither server declares it: `mvn vidocq:dev` adds the console, with the
live panel module of each Vidocq extension in use, because both servers have
`vidocq-runtime-chappe-webserver-extension`. The distributions never contain it (Vidocq/vidocq#143). Build with
`-nsu` (`--no-snapshot-updates`) when you test a local install of Vidocq: a snapshot deployed to the repository
later than your install would replace it without a word.

## Workarounds

`mcp-time-server` relies on three host-level workarounds, first needed on Vidocq 0.3.0 and kept on 0.4.0-SNAPSHOT.
None touch MCP server production code. `mcp-tasks-server` relies on the first and the third too; its own are in
[its README](mcp-tasks-server/README.md#why-the-code-looks-like-this), "Why the code looks like this".

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
   (`-Dvidocq.app.path=app`), so the service interface and its provider share one layer, and `./test-mcp.sh`
   passes all 7 checks. `langchain4j-cdi-mcp-invoker-cdi41` has to move with the other two. Its module requires
   both `dev.langchain4j.cdi.mcp.server` and `mcp.server.api`, and `run.sh` keeps `lib/` on the module path with
   `--add-modules ALL-MODULE-PATH`, so the JVM resolves every module left there in the boot layer. Left in `lib/`,
   the invoker stops the JVM before `main` with `java.lang.module.FindException: Module
   dev.langchain4j.cdi.mcp.server not found, required by dev.langchain4j.cdi.mcp.invoker.cdi41` (measured on
   0.4.0-SNAPSHOT). In `app/`, the synthetic `McpInvokerProvider` bean it registers implements the interface of the
   MCP server in the same layer, and the log reports `MCP: CDI 4.1 invoker provider ready: 4 method invoker(s)
   registered`. An invoker split from the MCP server does not fall back to reflection either. With Vidocq 0.3.0 on a
   flat module path, the MCP server moved into the application layer without it, the synthetic bean implemented
   the boot-layer copy of `McpInvokerProvider`, and every tool, resource and prompt call failed with a
   `ClassCastException` (see "Running from an IDE").

## Examples

### `mcp-time-server`

Four small, self-contained examples of the MCP server's feature surface, all about IANA time zones — see
[`mcp-time-server/README.md`](mcp-time-server/README.md).

### `mcp-tasks-server`

A task tracker on PostgreSQL: Mansart pool, Jakarta Data repositories and transactions behind a Cassini REST API that
writes, four MCP tools, two resources and two prompts that read, Flyway migrations, and the dev console — see
[`mcp-tasks-server/README.md`](mcp-tasks-server/README.md).

## Pending owner decisions

- **License.** No `LICENSE` file is included; that choice is left to the repository owner.
- **CI.** No CI workflow is configured yet.
