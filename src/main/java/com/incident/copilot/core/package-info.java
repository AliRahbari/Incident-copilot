/**
 * Framework-agnostic core of Incident Copilot. Everything under
 * {@code com.incident.copilot.core} is intentionally free of Spring, servlet,
 * and transport concerns so it can move to a standalone
 * {@code incident-copilot-core} module without changes.
 *
 * <p>Subpackages:
 * <ul>
 *   <li>{@link com.incident.copilot.core.domain} — value types and enums.</li>
 *   <li>{@link com.incident.copilot.core.analysis} — the analyzer that turns
 *       {@link com.incident.copilot.core.domain.IncidentInput} into an
 *       {@link com.incident.copilot.core.domain.IncidentAnalysis}.</li>
 *   <li>{@link com.incident.copilot.core.exception} — domain-level exceptions
 *       thrown by the analyzer and its collaborators.</li>
 * </ul>
 */
package com.incident.copilot.core;
