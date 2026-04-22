package com.incident.copilot.core.domain;

/**
 * Coarse taxonomy of the failure mode. Deliberately small for MVP — just enough
 * buckets to drive basic routing and reporting. More categories can be added
 * later; {@link #UNKNOWN} is the safe default when classification is not
 * attempted.
 */
public enum IncidentCategory {
    MEMORY,
    CONCURRENCY,
    IO,
    NETWORK,
    DATABASE,
    CONFIGURATION,
    STARTUP,
    UNKNOWN
}
