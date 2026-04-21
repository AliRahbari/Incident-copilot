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

```
src/main/java/com/incident/copilot/
├── IncidentCopilotApplication.java       # Entry point
├── client/
│   └── OpenAiClient.java                # OpenAI API client with timeouts
├── controller/
│   ├── IncidentController.java          # POST /analyze endpoint
│   └── GlobalExceptionHandler.java      # Centralized error handling
├── dto/
│   ├── AnalyzeRequest.java              # Request validation
│   ├── AnalyzeResponse.java             # Response structure
│   ├── PossibleCause.java               # Cause with confidence + evidence
│   └── ErrorResponse.java               # Error response structure
├── exception/
│   ├── LlmClientException.java          # API call failures
│   └── LlmResponseException.java        # Unparseable LLM responses
└── service/
    └── IncidentAnalysisService.java      # Analysis orchestration + prompt
```

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

# Build and run
./mvnw spring-boot:run
```

The server starts on `http://localhost:8585`.

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

```bash
./mvnw test
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

The service ships with a small Spring auto-configuration layer (under `com.incident.copilot.spring`) designed so it can later be extracted into a standalone `incident-copilot-spring-boot-starter` without renaming anything.

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
| `incident_copilot.captures` | `severity`, `category` | Incremented once per captured incident signal. |

Tag values are the uppercase enum names of `IncidentSeverity` and `IncidentCategory`. Both tags are always present — if severity or category is unknown (for example, when the signal originates from a raw exception rather than a completed analysis), the value is `UNKNOWN`. Sum over tags gives the overall total; filtering by a single tag gives a per-severity or per-category breakdown.

No Actuator dependency is required to emit these metrics — any Micrometer `MeterRegistry` bean on the classpath will do.

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
