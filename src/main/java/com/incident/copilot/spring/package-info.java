/**
 * Spring Boot integration layer. Designed to be extracted into
 * {@code incident-copilot-spring-boot-starter} unchanged:
 *
 * <ul>
 *   <li>{@link com.incident.copilot.spring.IncidentCopilotAutoConfiguration}
 *       — conditional wiring gated by {@code incident-copilot.*} properties.</li>
 *   <li>{@link com.incident.copilot.spring.IncidentCopilotProperties}
 *       — configuration binding.</li>
 *   <li>{@link com.incident.copilot.spring.IncidentSignalRecorder},
 *       {@link com.incident.copilot.spring.IncidentMetrics},
 *       {@link com.incident.copilot.spring.MicrometerIncidentMetrics}
 *       — capture SPI + Micrometer-backed default.</li>
 *   <li>{@link com.incident.copilot.spring.IncidentExceptionCaptureResolver}
 *       — non-invasive MVC exception observer.</li>
 * </ul>
 *
 * <p>Depends on {@code com.incident.copilot.core} only.
 */
package com.incident.copilot.spring;
