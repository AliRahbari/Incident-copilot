/**
 * Minimal action-layer abstraction. An {@link com.incident.copilot.core.sink.IncidentSink}
 * receives an {@link com.incident.copilot.core.sink.IncidentSignal} whenever an
 * incident is captured, providing the extension point for future Jira, Slack,
 * webhook, or audit integrations. No such integration is shipped here — only
 * the contract.
 */
package com.incident.copilot.core.sink;
