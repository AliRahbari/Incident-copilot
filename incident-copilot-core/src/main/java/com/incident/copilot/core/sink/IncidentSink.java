package com.incident.copilot.core.sink;

/**
 * A destination that consumes a captured {@link IncidentSignal}. Intentionally
 * the minimum viable contract: no lifecycle, no filtering, no routing DSL.
 *
 * <p>Typical implementations: a webhook poster, a Jira issue creator, a Slack
 * notifier, an audit log writer. None of those ship with this library — this
 * is the extension point consuming apps plug into.
 *
 * <p>Implementations must be thread-safe and should return promptly; fan-out
 * is synchronous and a slow sink slows the calling thread. Any thrown
 * exception is caught and logged by the fan-out layer — it will not break the
 * application flow nor affect other sinks — but sinks are encouraged to handle
 * their own failures when a more specific recovery is possible.
 */
@FunctionalInterface
public interface IncidentSink {

    void accept(IncidentSignal signal);
}
