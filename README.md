# Incident Copilot

Turn raw logs into actionable incident insights in seconds.

Incident Copilot is a lightweight backend service that analyzes application logs and stack traces using an LLM and returns structured, evidence-based incident analysis — including observations, ranked causes, confidence levels, and concrete next steps.
## Why

When an incident hits, engineers spend time scanning logs, forming hypotheses, and figuring out where to start.

Incident Copilot automates that first step:
- highlights what matters
- suggests likely causes
- and points you to the next action

It is not a replacement for human judgment — it is a fast first responder that reduces time-to-understanding.

## Features

- Analyze raw logs and stack traces in seconds
- Extract key observations directly from the input
- Suggest likely causes with confidence levels
- Provide evidence-backed reasoning
- Recommend actionable next debugging steps

### Technical highlights

- REST API (POST /analyze)
- OpenAI JSON mode for structured output
- Input validation and consistent error handling
- Configurable timeouts for LLM calls

## Tech Stack

| Component       | Technology           |
|-----------------|----------------------|
| Language        | Java 21              |
| Framework       | Spring Boot 3.4      |
| Build tool      | Maven                |
| LLM provider    | OpenAI API (gpt-4o)  |
| HTTP client     | Spring RestClient    |
| Validation      | Jakarta Bean Validation |
| Testing         | JUnit 5 + Mockito    |

## Project Structure

The build is a Maven multi-module project with an aggregator POM at the root:

```
.
├── pom.xml                                  # Aggregator / parent POM (packaging=pom)
├── incident-copilot-core/                   # Framework-agnostic domain + analysis logic
│   └── src/main/java/com/incident/copilot/core/
│       ├── domain/                          # Records + enums (IncidentInput, IncidentAnalysis, ...)
│       ├── analysis/
│       │   ├── IncidentAnalysisService.java # Plain Java analyzer (no Spring annotations)
│       │   └── LlmClient.java               # LLM client abstraction
│       └── exception/                       # Domain-level exceptions (LlmClientException, ...)
├── incident-copilot-spring-boot-starter/    # Reusable Spring Boot auto-configuration
│   └── src/main/java/com/incident/copilot/spring/
│       ├── IncidentCopilotAutoConfiguration.java
│       ├── IncidentCopilotProperties.java
│       ├── IncidentSignalRecorder.java
│       ├── IncidentMetrics.java / MicrometerIncidentMetrics.java
│       └── IncidentExceptionCaptureResolver.java
└── incident-copilot-app/                    # Spring Boot application — REST API + wiring
    └── src/main/java/com/incident/copilot/
        ├── IncidentCopilotApplication.java  # Entry point
        ├── client/
        │   └── OpenAiClient.java            # OpenAI API client with timeouts (implements LlmClient)
        ├── controller/
        │   ├── IncidentController.java      # POST /analyze endpoint
        │   └── GlobalExceptionHandler.java  # Centralized error handling
        ├── dto/
        │   ├── AnalyzeRequest.java          # Request validation
        │   ├── AnalyzeResponse.java         # Response structure
        │   ├── PossibleCause.java           # Cause with confidence + evidence
        │   ├── ErrorResponse.java           # Error response structure
        │   └── IncidentAnalysisMapper.java  # Domain → wire mapping
        └── config/
            └── OpenApiConfig.java           # springdoc
```

### What belongs where

- **`incident-copilot-core`** — pure Java, no Spring dependency. Holds the domain model (records, enums), the `IncidentAnalysisService` analyzer, the `LlmClient` abstraction, and domain-level exceptions. Publishable as a plain library.
- **`incident-copilot-spring-boot-starter`** — reusable auto-configuration. Given a `LlmClient` bean, it wires an `IncidentAnalysisService` plus the signal-capture surface (`IncidentSignalRecorder`, `IncidentMetrics`, MVC `IncidentExceptionCaptureResolver`). All dependencies on Micrometer, Spring Web, and the Servlet API are `<optional>true</optional>` — they only activate when the consuming app already has them. Exposes a single `incident-copilot.*` properties namespace.
- **`incident-copilot-app`** — the runnable Spring Boot application. Provides product-specific pieces only: the entry point, the REST controller and DTOs, the `OpenAiClient` implementation of `LlmClient`, global error handling, and the springdoc config. It owns no Spring integration plumbing — everything else is delivered by the starter.

### What a consuming app gets for free

Adding the starter as a dependency and providing a `LlmClient` bean is enough to get:

- a ready `IncidentAnalysisService` bean (wired against your `LlmClient` and the app's `ObjectMapper`)
- an `IncidentSignalRecorder` for explicit captures from application code
- Micrometer counter publication when a `MeterRegistry` is on the classpath
- a non-invasive `HandlerExceptionResolver` for MVC apps that records a signal per thrown exception without altering the response

Every bean uses `@ConditionalOnMissingBean`, so any of these can be replaced by the consuming app.

### Using the starter in another Spring Boot application

The starter is a standard Spring Boot 3 auto-configuration — it is picked up from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. There is no `@EnableIncidentCopilot` or manual `@Import` to add. A minimal consumer only has to do three things:

**1. Add the dependency**

```xml
<dependency>
    <groupId>com.incident</groupId>
    <artifactId>incident-copilot-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**2. Provide a `LlmClient` bean** — this is the only bean the starter expects the consuming app to contribute, because the choice of LLM provider belongs to the application, not the library:

```java
@Configuration
class LlmConfig {

    @Bean
    LlmClient llmClient() {
        return (systemPrompt, userMessage) -> {
            // call your LLM provider here and return the raw assistant response
            return "...";
        };
    }
}
```

**3. (Optional) Override defaults via `application.yml`:**

```yaml
incident-copilot:
  enabled: true               # master switch (default: true)
  publish-metrics: true       # emit Micrometer counters (default: true)
  capture-exceptions: true    # install MVC exception resolver (default: true)
```

Once the starter is on the classpath and a `LlmClient` is available, the following beans are auto-wired:

| Bean | Condition | Can the app override? |
|------|-----------|-----------------------|
| `IncidentAnalysisService` | `LlmClient` bean present | Yes (`@ConditionalOnMissingBean`) |
| `IncidentSignalRecorder` | always | Yes |
| `IncidentMetrics` → `MicrometerIncidentMetrics` | `MeterRegistry` bean on classpath + `publish-metrics=true` | Yes |
| `IncidentMetrics` → `NoOpIncidentMetrics` | no `MeterRegistry` or `publish-metrics=false` | Yes |
| `IncidentExceptionCaptureResolver` | servlet web app + `capture-exceptions=true` | Yes |

**What remains app-specific** (the starter deliberately does not ship these):

- the `LlmClient` implementation (OpenAI, Anthropic, Bedrock, a local model, a mock for tests, …)
- any REST controllers or HTTP surface that calls `IncidentAnalysisService`
- request/response DTOs and domain → wire mapping
- global exception handling / `@ControllerAdvice`
- `MeterRegistry` wiring (typically via `spring-boot-starter-actuator`), if you want the Micrometer path rather than the no-op
- the OpenAPI / springdoc configuration, if any

Micrometer, Spring Web, and the Servlet API are declared as `<optional>true</optional>` on the starter, so a non-web or non-metrics application pulls in no extra transitive classes and simply gets the no-op / servlet-skipped paths.

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- An OpenAI API key

### Environment Variables

| Variable        | Required | Description              |
|-----------------|----------|--------------------------|
| `OPENAI_API_KEY` | Yes      | Your OpenAI API key      |

### Run Locally

```bash
# Clone the repository
git clone https://github.com/AliRahbari/Incident-copilot.git
cd Incident-copilot

# Set your API key
export OPENAI_API_KEY=sk-...

# Build the whole project (parent + both modules)
mvn clean package

# Run the Spring Boot app module
mvn -pl incident-copilot-app -am spring-boot:run
```

The server starts on `http://localhost:8585`.

Alternatively, after `mvn clean package` you can run the produced fat jar directly:

```bash
java -jar incident-copilot-app/target/incident-copilot-app-*.jar
```

### Run with Docker

```bash
# Build the image
docker build -t incident-copilot .

# Run the container
docker run -p 8585:8585 -e OPENAI_API_KEY=sk-... incident-copilot
```

The server starts on `http://localhost:8585`.

### Run with Docker Compose

```bash
# Set your API key (or add it to a .env file in the project root)
export OPENAI_API_KEY=sk-...

# Build and run
docker compose up --build
```

The server starts on `http://localhost:8585`.

### Run Tests

Run the full test suite across both modules from the repository root:

```bash
mvn test
```

## API Usage

### Request

```
POST /analyze
Content-Type: application/json
```

```json
{
  "input": "2024-03-15 14:23:01 ERROR [http-nio-8080-exec-3] com.example.UserService - Failed to fetch user profile\njava.net.SocketTimeoutException: Connect timed out\n\tat java.net.Socket.connect(Socket.java:633)\n\tat com.example.UserService.getProfile(UserService.java:47)\n\tat com.example.UserController.show(UserController.java:28)"
}
```

### Response

```json
{
  "summary": "UserService is failing to fetch user profiles due to a socket connection timeout, likely indicating the downstream dependency is unreachable or slow.",
  "observations": [
    "java.net.SocketTimeoutException: Connect timed out",
    "Error originates in com.example.UserService.getProfile(UserService.java:47)",
    "Called from com.example.UserController.show(UserController.java:28)",
    "Single occurrence logged at 2024-03-15 14:23:01 on thread http-nio-8080-exec-3"
  ],
  "possibleCauses": [
    {
      "cause": "Downstream service that UserService.getProfile connects to is unreachable or not responding within the timeout window",
      "confidence": "high",
      "evidence": [
        "java.net.SocketTimeoutException: Connect timed out",
        "Stack trace points to Socket.connect as the failure point"
      ]
    },
    {
      "cause": "Connection timeout threshold is configured too low for the target service's typical response time",
      "confidence": "low",
      "evidence": [
        "SocketTimeoutException on connect phase rather than read phase"
      ]
    }
  ],
  "nextSteps": [
    "Check health and reachability of the service that UserService.getProfile(UserService.java:47) connects to",
    "Review connection timeout configuration for the HTTP client used in UserService",
    "Check for network-level issues (DNS, firewall, security groups) between this service and its downstream dependency",
    "Look for additional correlated errors around 2024-03-15 14:23:01 in other services"
  ]
}
```

### Error Responses

| Status | Condition                          | Body                                          |
|--------|------------------------------------|-----------------------------------------------|
| 400    | Blank or missing input             | `{"error": "input: Input must not be blank"}` |
| 400    | Input exceeds 50,000 characters    | `{"error": "input: Input must not exceed 50,000 characters"}` |
| 502    | LLM API call failed                | `{"error": "LLM service error — please try again later"}` |
| 502    | LLM returned unparseable response  | `{"error": "LLM returned an invalid response — please try again"}` |
| 500    | Unexpected server error            | `{"error": "Internal server error"}` |

## Spring integration & metrics

The Spring integration lives in the reusable `incident-copilot-spring-boot-starter` module (package `com.incident.copilot.spring`). The app consumes it as a dependency and adds no integration code of its own.

### Configuration properties

All keys use the `incident-copilot` prefix and default to `true` (i.e. the integration is fully on out of the box):

| Key | Default | Effect |
|-----|---------|--------|
| `incident-copilot.enabled` | `true` | Master switch. When `false`, none of the integration beans are registered. |
| `incident-copilot.publish-metrics` | `true` | Publish Micrometer metrics when a `MeterRegistry` is present. When `false`, a no-op `IncidentMetrics` is used. |
| `incident-copilot.capture-exceptions` | `true` | Register a `HandlerExceptionResolver` that records a signal for exceptions thrown from MVC handlers. The resolver never produces a response — it only records and delegates back to the existing `@ControllerAdvice`. |

### Metrics

When a `MeterRegistry` is available and metrics are enabled, a single Micrometer counter is published:

| Metric | Tags | Description |
|--------|------|-------------|
| `incident.copilot.captures` | `severity`, `category` | Incremented once per captured incident signal. |

Tag values are the uppercase enum names of `IncidentSeverity` and `IncidentCategory`. Both tags are always present — if severity or category is unknown (for example, when the signal originates from a raw exception rather than a completed analysis), the value is `UNKNOWN`. Sum over tags gives the overall total; filtering by a single tag gives a per-severity or per-category breakdown.

The capture code path does not require Actuator — any Micrometer `MeterRegistry` bean is enough to emit. Actuator is pulled in by the application so the metric can be *inspected* over HTTP (see below). The starter itself does not force Actuator on its consumers.

#### Inspecting metrics via Actuator

The application exposes a minimal Actuator surface — only the `metrics` endpoint — for local inspection:

```bash
# List all registered metric names (includes incident.copilot.captures)
curl http://localhost:8585/actuator/metrics

# Show the captures counter with available tags and total value
curl http://localhost:8585/actuator/metrics/incident.copilot.captures

# Filter to a specific severity/category slice
curl "http://localhost:8585/actuator/metrics/incident.copilot.captures?tag=severity:UNKNOWN&tag=category:UNKNOWN"
```

Only `metrics` is exposed (`management.endpoints.web.exposure.include: metrics`). No `health`, `env`, `beans`, or other endpoints are reachable, and no Actuator security is configured — this is a development and testing aid, not a production-grade observability surface.

The integration tests (`IncidentCopilotIntegrationTest`, `MicrometerIncidentMetricsTest`, `ActuatorMetricsEndpointTest`) cover both the metric contract and its Actuator visibility on every `mvn test`.

## Current Limitations

This is an MVP. Key limitations:

- **No authentication** — the endpoint is open
- **No persistence** — analysis results are not stored
- **No rate limiting** — unprotected against abuse
- **Single LLM provider** — hardcoded to OpenAI
- **No streaming** — full response is returned at once
- **No multi-turn context** — each request is independent

## Roadmap

### Short term
- Add API key authentication
- Add rate limiting

### Mid term
- Support multiple LLM providers
- Add observability (logs, metrics)
- CI/CD pipeline

### Long term
- Incident pattern detection
- Integration with alerting tools
- Historical analysis

## Future Ideas

- Batch analysis for multiple log files
- Incident severity classification
- Integration with alerting tools (PagerDuty, Opsgenie)
- Slack/Teams bot interface
- Historical incident pattern matching with a vector store

## When to use Incident Copilot

- You have a stack trace and need a quick starting point
- You want a structured first-pass analysis before deep debugging
- You are dealing with unfamiliar code or services
- You want to reduce time-to-diagnosis during incidents

## What this is NOT

- Not a full observability platform
- Not a replacement for logs, tracing, or metrics
- Not guaranteed to be correct in all cases
- Not a production-grade incident management system (yet)

## License

This project is not yet licensed. A license will be added before any public release.
