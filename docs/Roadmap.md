# Incident Copilot – Product Roadmap

## Vision

Incident Copilot aims to become a **Spring Boot-native incident intelligence layer**.

Instead of manually reading logs and stack traces, developers should be able to:

- detect meaningful incident signals automatically
- understand failures faster
- get actionable debugging guidance
- trigger actions such as alerts or tickets

The long-term goal is to transform raw runtime signals into **structured, actionable engineering intelligence**.

---

## Product Evolution

| Stage | Description |
|------|-------------|
| Current | Log / stack trace analysis API |
| Next | Embedded Spring Boot library |
| Future | Incident intelligence + integrations platform |

---

## Core Principles

- **Signal over noise** – focus on important failures, not all logs
- **Minimal friction** – easy integration via Spring Boot starter
- **LLM is optional** – intelligence must not block core functionality
- **Evidence-based analysis** – no hallucinations
- **Actionable output** – every result should help debugging
- **Incremental complexity** – grow in phases, avoid overengineering

---

## Phase 0 — MVP (Current)

### Goal
Prove that structured incident analysis is useful

### Includes
- REST API (`POST /analyze`)
- LLM-based analysis
- Structured JSON output:
    - summary
    - observations
    - possible causes
    - next steps
- Evaluation framework
- Example logs
- Docker support
- Documentation

### Status
In progress / validating usefulness

---

## Phase 1 — Core Domain Model

### Goal
Define a reusable and framework-agnostic incident model

### Introduce
- `IncidentInput`
- `IncidentAnalysis`
- `IncidentObservation`
- `PossibleCause`
- `RecommendedAction`
- `IncidentSeverity`
- `IncidentCategory`

### Output
- Clean domain layer
- No dependency on Spring or LLM
- Foundation for library extraction

---

## Phase 2 — Spring Boot Starter

### Goal
Make Incident Copilot embeddable into applications

### Module
`incident-copilot-spring-boot-starter`

### Capabilities
- Auto-configuration
- Configuration properties
- Enable/disable flags
- Hook into exception handling
- Basic incident publishing

### Example Usage

```yaml
incident-copilot:
  enabled: true
  analyze-exceptions: true
  publish-metrics: true
```
--- 

## Phase 3 — Signal Detection & Metrics
### Goal
Provide real monitoring value

### Focus
Capture incident-worthy signals, not all logs:
- uncaught exceptions
- repeated failures
- integration errors
- threshold-based incidents
### Metrics (Micrometer)
- exception count
- repeated failure count
- incidents by severity
- incidents by category
## Important
Must work without LLM

---


## Phase 4 — LLM Enrichment
### Goal
Add intelligent analysis to selected incidents

### Capabilities
Analyze filtered incidents
Add:
- summary
- observations
- causes
- next steps
### Constraints
- LLM is optional
- configurable
- cost-aware (sampling / thresholds)
- async where possible

---


## Phase 5 — Integrations (Action Layer)
### Goal
Turn insights into actions

### Integrations
- Jira (ticket creation)
- Slack / Teams (notifications)
- Generic webhooks
### Trigger Conditions
- high severity incidents
- repeated failures
- configurable rules

---


## Phase 6 — Code & Repository Awareness
### Goal
Improve debugging depth and context

### Capabilities
- map stack traces to source code
- extract relevant code snippets
- identify ownership/team
- highlight suspicious call paths
### Notes
- requires repository access
- higher complexity
- should follow validation of earlier phases

---

## Phase 7 — Advanced Intelligence (Future)
### Goal
Move toward engineering intelligence platform

### Ideas
- incident clustering
- pattern detection
- historical analysis
- anomaly detection
- recommendation learning

---

## v0.1 Scope (First Library Release)
### Included
* Core domain model
* Spring Boot starter
* basic exception capture
* Micrometer metrics
* optional analysis hook
## Excluded
* Jira integration
* Slack integration
* repository/code analysis
* UI/dashboard
* complex rule engine


---

### Out of Scope (for now)
* Full observability platform
* Log ingestion system (ELK replacement)
* Distributed tracing
* Real-time streaming pipelines
* AI-driven code modification

----

## Architecture Direction

### Planned modules:

* incident-copilot-core
* incident-copilot-spring-boot-starter
* incident-copilot-llm
* incident-copilot-integrations
* incident-copilot-ui

---

### Success Criteria
* Developers find analysis useful
* Integration takes minutes, not hours
* Metrics provide real operational insight
* LLM adds meaningful value (not noise)
* System remains lightweight and predictable
