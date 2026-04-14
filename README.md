# Incident Copilot

A lightweight backend service that analyzes application logs and stack traces using an LLM and returns structured, evidence-based incident analysis.

## Why

When an incident hits, developers spend valuable time reading through logs, identifying patterns, and forming hypotheses. Incident Copilot automates the first pass of that triage — turning raw logs into a structured analysis with observations, ranked causes, confidence levels, and concrete next steps.

This is not a replacement for human judgment. It is a fast first responder that helps you focus on what matters.

## Features

- Single REST endpoint for incident analysis (`POST /analyze`)
- Structured JSON response with observations, ranked causes, and next steps
- Confidence levels (`high` / `medium` / `low`) for each possible cause
- Evidence-based analysis — every cause is linked to specific input references
- Input validation with clear error responses
- Configurable HTTP timeouts for LLM calls
- OpenAI JSON mode for reliable structured output
- Custom exception handling with consistent error format

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

The server starts on `http://localhost:8080`.

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

## Current Limitations

This is an MVP. Key limitations:

- **No authentication** — the endpoint is open
- **No persistence** — analysis results are not stored
- **No rate limiting** — unprotected against abuse
- **Single LLM provider** — hardcoded to OpenAI
- **No streaming** — full response is returned at once
- **No multi-turn context** — each request is independent

## Roadmap

- [ ] Add API key authentication
- [ ] Add rate limiting
- [ ] Support multiple LLM providers (Anthropic, local models)
- [ ] Add request/response logging and observability
- [ ] Containerize with Docker
- [ ] Add CI/CD pipeline

## Future Ideas

- Batch analysis for multiple log files
- Incident severity classification
- Integration with alerting tools (PagerDuty, Opsgenie)
- Slack/Teams bot interface
- Historical incident pattern matching with a vector store

## License

This project is not yet licensed. A license will be added before any public release.
