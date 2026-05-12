# Incident Copilot

Turn raw logs and stack traces into actionable incident insights in seconds — and capture incident signals from inside your Spring Boot app.

Incident Copilot ships as:

- a small framework-agnostic **core library** (domain model + analysis logic),
- a **Spring Boot starter** that auto-configures incident capture and metrics, and
- a runnable **demo app** that exposes a REST API and wires an OpenAI-backed `LlmClient`.

---

## 1. Project overview

When an incident hits, engineers spend their first minutes scanning logs, forming hypotheses, and figuring out where to start. Incident Copilot automates that first pass:

- highlights what matters in a log / stack trace,
- suggests likely causes with confidence and evidence,
- and recommends concrete next debugging steps.

It is a fast first responder, not a replacement for human judgment, and not a full observability platform.

**Status: MVP / pre-1.0.** The artifacts are **not yet published to Maven Central**. Consumers either build & install locally (see §4) or wait for a published release. See §9 for current limitations and §10 for the roadmap.

---

## 2. Module overview

The build is a Maven multi-module project (`packaging=pom` at the root):

| Module | Type | Purpose |
|---|---|---|
| `incident-copilot-core` | Library | Framework-agnostic domain model (`IncidentInput`, `IncidentAnalysis`, `IncidentSeverity`, `IncidentCategory`, …), the `IncidentAnalysisService` analyzer, the `LlmClient` abstraction, the `IncidentClassifier` rule-based tagger, and the `IncidentSink` extension point. No Spring dependency. |
| `incident-copilot-spring-boot-starter` | Library | Spring Boot 3 auto-configuration. Given a `LlmClient` bean, wires `IncidentAnalysisService`, `IncidentSignalRecorder`, an `IncidentMetrics` (Micrometer or no-op), and an MVC `HandlerExceptionResolver` for non-invasive exception capture. Micrometer / Spring Web / Servlet API deps are `<optional>true</optional>` — only active when the consumer already pulls them in. |
| `incident-copilot-app` | Application | Runnable Spring Boot reference app: REST API (`POST /analyze`), DTOs and mapping, an `OpenAiClient` implementation of `LlmClient`, global error handling, springdoc, and an Actuator surface for inspecting metrics. **This is the demo, not part of the library surface.** Consumers do not depend on it. |

---

## 3. For library users: using the starter

> This section assumes the artifacts are resolvable from your Maven repository. Until Maven Central publication, see §4 first to install them locally.

The starter is a standard Spring Boot 3 auto-configuration registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. There is no `@EnableIncidentCopilot` annotation or `@Import` to add.

### 3.1 Add the dependency

```xml
<dependency>
    <groupId>com.incident</groupId>
    <artifactId>incident-copilot-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The starter transitively brings `incident-copilot-core`. It does **not** pull in Micrometer, Spring Web, or the Servlet API — those are `<optional>true</optional>` and activate only when your application already depends on them (which a typical `spring-boot-starter-web` + `spring-boot-starter-actuator` app does).

### 3.2 Provide an `LlmClient` bean (required for analysis)

The choice of LLM provider belongs to the application, not the library. The starter does **not** ship an `LlmClient`:

```java
@Configuration
class LlmConfig {

    @Bean
    LlmClient llmClient() {
        return (systemPrompt, userMessage) -> {
            // Call your LLM provider (OpenAI, Anthropic, Bedrock, a local model, …)
            // and return the raw assistant response as a JSON string.
            return "...";
        };
    }
}
```

Without an `LlmClient` bean, the `IncidentAnalysisService` is simply not created — capture and metrics still work, analysis does not.

### 3.3 Optional `application.yml` configuration

```yaml
incident-copilot:
  enabled: true               # master switch (default: true)
  publish-metrics: true       # emit Micrometer counters (default: true)
  capture-exceptions: true    # install MVC exception resolver (default: true)
```

### 3.4 What the starter auto-configures

| Bean | Condition | Replaceable? |
|---|---|---|
| `IncidentClassifier` | always | Yes (`@ConditionalOnMissingBean`) |
| `IncidentAnalysisService` | an `LlmClient` bean is present | Yes |
| `IncidentSignalRecorder` | always; collects all `IncidentSink` beans for fan-out | Yes |
| `IncidentMetrics` → `MicrometerIncidentMetrics` | `MeterRegistry` on classpath + `publish-metrics=true` | Yes |
| `IncidentMetrics` → `NoOpIncidentMetrics` | no `MeterRegistry`, or `publish-metrics=false` | Yes |
| `IncidentExceptionCaptureResolver` | servlet web app + `capture-exceptions=true` | Yes |

### 3.5 What the consuming app must still provide

- An `LlmClient` implementation (required for LLM analysis only — capture/metrics work without it).
- Any REST controllers or HTTP surface that calls `IncidentAnalysisService`.
- Request/response DTOs and domain→wire mapping.
- Global exception handling / `@ControllerAdvice`, if you want custom error bodies.
- A `MeterRegistry` (typically via `spring-boot-starter-actuator`) if you want metrics published to a real backend rather than the no-op.

### 3.6 What the starter does **not** do — important

- It does **not** read or tail your application log files.
- It does **not** intercept arbitrary `Logger.error(...)` calls or every uncaught throwable in the JVM.
- It does **not** ship sinks for Slack, Jira, PagerDuty, webhooks, or any other destination.

The starter captures incident signals only via the channels listed in 3.7 and 3.8 below.

### 3.7 MVC exception capture

When the app is a **servlet** Spring Boot app and `incident-copilot.capture-exceptions=true` (default), the starter registers a non-invasive `HandlerExceptionResolver`. For every exception that bubbles out of an `@Controller`/`@RestController` handler, the resolver:

1. classifies the throwable into `(severity, category)`,
2. records the `incident.copilot.captures` counter, and
3. fans out an `IncidentSignal` to any registered `IncidentSink` beans.

The resolver returns `null` — it never produces a response and never alters the response your existing `@ControllerAdvice` would have produced.

This is the **only** automatic capture path. Reactive (WebFlux) apps are not auto-captured. Background-thread exceptions are not auto-captured. For anything else, see 3.8.

### 3.8 Explicit captures from application code

For any code path that is not an MVC handler — scheduled jobs, message listeners, completed analyses, business validation failures, etc. — inject `IncidentSignalRecorder` and capture explicitly:

```java
@Component
class OrderJob {

    private final IncidentSignalRecorder recorder;

    OrderJob(IncidentSignalRecorder recorder) {
        this.recorder = recorder;
    }

    @Scheduled(fixedDelay = 60_000)
    void run() {
        try {
            // ... business logic ...
        } catch (RuntimeException ex) {
            recorder.capture(ex);     // classify + meter + fan out to sinks
            throw ex;
        }
    }
}
```

`recorder.capture(IncidentAnalysis)` is the matching overload for the analysis path — call it after `IncidentAnalysisService` returns so you get a metric and a sink fan-out tagged with the analysis's own severity/category.

### 3.9 Metrics

When a `MeterRegistry` bean is available and `publish-metrics=true`, the starter publishes one Micrometer counter:

| Metric | Tags | Description |
|---|---|---|
| `incident.copilot.captures` | `severity`, `category` | Incremented once per captured incident signal. |

Tag values are uppercase enum names of `IncidentSeverity` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`, `UNKNOWN`) and `IncidentCategory` (`MEMORY`, `CONCURRENCY`, `IO`, `NETWORK`, `DATABASE`, `CONFIGURATION`, `STARTUP`, `UNKNOWN`). Both tags are always present. `UNKNOWN` is a valid outcome when no classifier rule matches.

To replace the classifier, contribute your own `IncidentClassifier` bean — `@ConditionalOnMissingBean` means your implementation wins. See §8 for how to inspect metrics via Actuator in the demo app.

### 3.10 The `IncidentSink` extension point

Every captured signal is fanned out to zero or more `IncidentSink` beans **after** metrics are recorded. This is the single extension point for acting on a signal — future Jira/Slack/webhook integrations will plug in here. **No such integration is shipped today, only the contract.**

```java
@Bean
IncidentSink auditSink() {
    return signal -> log.info("incident captured: {}/{}",
            signal.severity(), signal.category());
}
```

`IncidentSignal` carries the classified severity/category, a timestamp, and exactly one of:

- a finished `IncidentAnalysis` (analysis path) — via `signal.analysisOpt()`
- a raw `Throwable` (exception-capture path) — via `signal.throwableOpt()`

Safety guarantees:

- **Zero sinks is the default.** No bean ⇒ fan-out is a no-op.
- **Sink failures never break the caller.** Each sink runs inside a try/catch that logs and continues; a throwing sink does not suppress the metric and does not propagate.
- **Fan-out is synchronous on the calling thread.** Slow sinks slow capture — sinks that do I/O should manage their own concurrency.

---

## 4. Using the project locally (before Maven Central publication)

Until the libraries are published, third parties consume them by installing the artifacts into a local Maven repository.

### Option 1 — clone & `mvn install` (recommended)

```bash
git clone https://github.com/AliRahbari/Incident-copilot.git
cd Incident-copilot

# Installs incident-copilot-core and -spring-boot-starter into ~/.m2/repository.
# The app module is built but its deploy is skipped (it's the demo).
mvn clean install -DskipTests
```

Then, in your own Spring Boot project's `pom.xml`:

```xml
<dependency>
    <groupId>com.incident</groupId>
    <artifactId>incident-copilot-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Maven resolves it from your local `~/.m2/repository` without any other configuration.

### Option 2 — build JARs and install them manually

If you only have the JARs (e.g. a CI artifact handoff) and not the source tree:

```bash
mvn install:install-file \
  -Dfile=incident-copilot-core-0.1.0.jar \
  -DgroupId=com.incident \
  -DartifactId=incident-copilot-core \
  -Dversion=0.1.0 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=incident-copilot-spring-boot-starter-0.1.0.jar \
  -DgroupId=com.incident \
  -DartifactId=incident-copilot-spring-boot-starter \
  -Dversion=0.1.0 \
  -Dpackaging=jar
```

Pair each JAR with its `-sources.jar` and `-javadoc.jar` if your IDE / consumers want them.

---

## 5. For maintainers: build & test

Requirements: **JDK 21+**, **Maven 3.9+**, and (for the demo app at runtime) an OpenAI API key.

```bash
# Build everything (parent + 3 modules), run all tests
mvn clean verify

# Build without tests
mvn clean install -DskipTests

# Run only the unit/integration tests
mvn test

# Run only the demo app (mounts core + starter from the reactor)
mvn -pl incident-copilot-app -am spring-boot:run

# Run the demo app with a real OpenAI key
export OPENAI_API_KEY=sk-...
mvn -pl incident-copilot-app -am spring-boot:run
```

The server starts on `http://localhost:8585`. A fat-jar is also produced under `incident-copilot-app/target/`:

```bash
java -jar incident-copilot-app/target/incident-copilot-app-*.jar
```

### Docker / Docker Compose (demo app only)

```bash
# Build and run via Docker
docker build -t incident-copilot .
docker run -p 8585:8585 -e OPENAI_API_KEY=sk-... incident-copilot

# Or via Docker Compose
export OPENAI_API_KEY=sk-...
docker compose up --build
```

The Docker image bundles the runnable demo app, not the library — consumers of the starter do not need it.

---

## 6. For maintainers: publishing

Three publication paths are supported. Detailed step-by-step instructions live in [`docs/PUBLISHING.md`](docs/PUBLISHING.md); the summary below should be enough to pick the right one.

| Path | Audience | Effort | Notes |
|---|---|---|---|
| **Local install** (`mvn clean install`) | Yourself + anyone with the source tree | None | The default during development. See §4. |
| **GitHub Packages** | Anyone who can configure a GitHub Maven repo + auth token | Low | Activated with `-Prelease,github` (see PUBLISHING.md). Consumers must add a `<repository>` block and authenticate to GHCR Maven. |
| **Maven Central** | Anyone, no extra config | High | Requires verified namespace ownership on the Central Portal, GPG-signed artifacts, source + javadoc jars, and a published license. Activated with `-Prelease,central`. |

The build is already wired for source jars, javadoc jars, GPG signing, and the modern Central Portal flow via `central-publishing-maven-plugin` — all gated behind a `release` profile so day-to-day builds stay fast.

Before Maven Central can be used, the following must be settled (intentionally not invented in the POM — they appear as `TODO` placeholders):

- A real reverse-DNS `groupId` whose namespace you have verified on the Central Portal (e.g. `io.github.<your-handle>`). The current `com.incident` will be rejected.
- A published license (LICENSE file + `<licenses>` entry).
- Developer metadata (`<developers>` entry).
- A GPG key uploaded to a public keyserver, with secret-key material available to the release machine.
- Central Portal credentials in `~/.m2/settings.xml` under `<server><id>central</id></server>`.

The demo app (`incident-copilot-app`) is configured with `maven-deploy-plugin.skip=true`. It is built and tested by every release but **never deployed** — it's not a library.

---

## 7. The demo app (`incident-copilot-app`)

`incident-copilot-app` is a thin, runnable reference application that:

- exposes the historical `POST /analyze` REST endpoint,
- ships an `OpenAiClient` implementation of `LlmClient`,
- provides a `@ControllerAdvice` for consistent error bodies,
- exposes springdoc / Swagger UI, and
- enables only the Actuator `metrics` endpoint, for inspecting `incident.copilot.captures` over HTTP.

It exists to show how the starter is wired in practice and to give the project an end-to-end demo. **Library consumers do not depend on it** and should not pull it in transitively.

### REST API at a glance

```
POST /analyze
Content-Type: application/json

{ "input": "...raw log or stack trace..." }
```

Returns a structured `summary` + `observations` + `possibleCauses` (with `confidence` and `evidence`) + `nextSteps`. Error responses:

| Status | Condition |
|---|---|
| 400 | Blank, missing, or > 50,000-character input |
| 502 | LLM API call failed, or LLM returned an unparseable response |
| 500 | Unexpected server error |

---

## 8. Metrics & Actuator

When `incident-copilot-spring-boot-starter` runs against a `MeterRegistry`, it publishes:

| Metric | Tags |
|---|---|
| `incident.copilot.captures` | `severity`, `category` |

The demo app exposes only the `metrics` Actuator endpoint (`management.endpoints.web.exposure.include: metrics`) — enough to verify the counter locally, not a production observability surface:

```bash
# List all registered metric names
curl http://localhost:8585/actuator/metrics

# Show the captures counter with tags + value
curl http://localhost:8585/actuator/metrics/incident.copilot.captures

# Filter to a specific severity/category slice
curl "http://localhost:8585/actuator/metrics/incident.copilot.captures?tag=severity:UNKNOWN&tag=category:UNKNOWN"
```

`IncidentCopilotIntegrationTest`, `MicrometerIncidentMetricsTest`, and `ActuatorMetricsEndpointTest` cover the metric contract and its Actuator visibility on every `mvn test`.

---

## 9. Current limitations

This is an MVP. Honest list:

- **Not on Maven Central yet** — consume via local install (§4) until publication.
- **No authentication / rate limiting** on the demo app's `/analyze` endpoint.
- **No persistence** — analyses are not stored.
- **Single LLM provider in the demo** — `OpenAiClient` (consumers wire their own).
- **No streaming responses** from the analysis API.
- **No multi-turn context.**
- **Capture paths are narrow by design**: servlet MVC handler exceptions + explicit `IncidentSignalRecorder` calls. No global log scraping, no JVM-wide exception hook.
- **No sinks shipped**: Slack/Jira/webhook integrations are out of scope for 0.1.x.

---

## 10. Roadmap

See [`docs/Roadmap.md`](docs/Roadmap.md) for the full plan (phases 0–7, v0.1 scope, success criteria, and out-of-scope items).

---

## License

This project is not yet licensed publicly. A license will be added before the first published release; see the `TODO` markers in the parent `pom.xml` and `docs/PUBLISHING.md`.
