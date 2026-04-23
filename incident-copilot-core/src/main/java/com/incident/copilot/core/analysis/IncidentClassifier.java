package com.incident.copilot.core.analysis;

import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Rule-based incident classifier. Deliberately lightweight: no DSL, no external
 * configuration, no ML. Two entry points:
 * <ul>
 *   <li>{@link #classify(Throwable)} — walks the cause chain and matches against
 *       well-known JDK/standard exception types.</li>
 *   <li>{@link #classify(String)} — case-insensitive keyword scan over free text
 *       (typically the LLM's summary + observations).</li>
 * </ul>
 * Both methods are null-safe and fall back to {@link Classification#UNKNOWN} when
 * nothing matches. Consumers can replace this bean with their own implementation
 * to extend the taxonomy without forking the starter.
 */
public class IncidentClassifier {

    private static final int MAX_CAUSE_DEPTH = 16;

    public Classification classify(Throwable throwable) {
        if (throwable == null) {
            return Classification.UNKNOWN;
        }

        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            Classification match = matchException(current);
            if (match != null) {
                return match;
            }
            Throwable next = current.getCause();
            if (next == null || next == current) {
                break;
            }
            current = next;
            depth++;
        }

        return new Classification(IncidentSeverity.MEDIUM, IncidentCategory.UNKNOWN);
    }

    public Classification classify(String text) {
        if (text == null || text.isBlank()) {
            return Classification.UNKNOWN;
        }
        String lower = text.toLowerCase(Locale.ROOT);

        IncidentCategory category = categoryFromText(lower);
        IncidentSeverity severity = severityFromText(lower);

        if (category == IncidentCategory.UNKNOWN && severity == IncidentSeverity.UNKNOWN) {
            return Classification.UNKNOWN;
        }
        return new Classification(severity, category);
    }

    private static Classification matchException(Throwable t) {
        if (t instanceof OutOfMemoryError) {
            return new Classification(IncidentSeverity.CRITICAL, IncidentCategory.MEMORY);
        }
        if (t instanceof StackOverflowError) {
            return new Classification(IncidentSeverity.HIGH, IncidentCategory.MEMORY);
        }
        if (t instanceof SQLException) {
            return new Classification(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        }
        if (t instanceof ConnectException
                || t instanceof UnknownHostException
                || t instanceof SocketTimeoutException
                || t instanceof NoRouteToHostException) {
            return new Classification(IncidentSeverity.HIGH, IncidentCategory.NETWORK);
        }
        if (t instanceof TimeoutException
                || t instanceof RejectedExecutionException
                || t instanceof InterruptedException) {
            return new Classification(IncidentSeverity.MEDIUM, IncidentCategory.CONCURRENCY);
        }
        if (t instanceof NoSuchFileException
                || t instanceof AccessDeniedException
                || t instanceof java.io.FileNotFoundException) {
            return new Classification(IncidentSeverity.MEDIUM, IncidentCategory.IO);
        }
        if (isInstanceOfByName(t,
                "org.springframework.dao.DataAccessException",
                "org.hibernate.HibernateException",
                "jakarta.persistence.PersistenceException",
                "javax.persistence.PersistenceException")) {
            return new Classification(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        }
        if (isInstanceOfByName(t,
                "org.springframework.beans.factory.BeanCreationException",
                "org.springframework.beans.factory.BeanInstantiationException",
                "org.springframework.beans.factory.NoSuchBeanDefinitionException",
                "org.springframework.boot.context.properties.bind.BindException",
                "java.util.MissingResourceException")) {
            return new Classification(IncidentSeverity.HIGH, IncidentCategory.CONFIGURATION);
        }
        if (isInstanceOfByName(t,
                "org.springframework.boot.context.config.ConfigDataResourceNotFoundException",
                "org.springframework.context.ApplicationContextException")) {
            return new Classification(IncidentSeverity.CRITICAL, IncidentCategory.STARTUP);
        }
        // Fall back on the IOException supertype only after the more specific network/IO subtypes.
        if (t instanceof IOException) {
            return new Classification(IncidentSeverity.MEDIUM, IncidentCategory.IO);
        }
        return null;
    }

    private static IncidentCategory categoryFromText(String lower) {
        if (containsAny(lower,
                "out of memory", "outofmemoryerror", "java heap space", "heap dump",
                "gc overhead", "metaspace", "permgen", "allocation failure")) {
            return IncidentCategory.MEMORY;
        }
        if (containsAny(lower,
                "deadlock", "lock contention", "thread blocked", " blocked on ",
                "synchronized", "monitor state", "thread pool exhausted", "rejectedexecution")) {
            return IncidentCategory.CONCURRENCY;
        }
        if (containsAny(lower,
                "connection refused", "connection reset", "unknown host",
                "sockettimeout", "no route to host", "connect timeout",
                "host unreachable", "dns resolution")) {
            return IncidentCategory.NETWORK;
        }
        if (containsAny(lower,
                "sqlexception", "jdbc", "deadlock detected",
                "constraint violation", "duplicate key", "connection pool exhausted",
                "query timeout", "hibernate")) {
            return IncidentCategory.DATABASE;
        }
        if (containsAny(lower,
                "no such file", "filenotfound", "disk full", "no space left",
                "ioexception", "broken pipe", "access denied")) {
            return IncidentCategory.IO;
        }
        if (containsAny(lower,
                "beancreationexception", "bean definition", "missing property",
                "could not resolve placeholder", "configuration property",
                "@value", "autowired", "no such bean")) {
            return IncidentCategory.CONFIGURATION;
        }
        if (containsAny(lower,
                "applicationcontext", "context refresh", "@postconstruct",
                "startup failed", "failed to start", "application failed to start",
                "bootstrap failed")) {
            return IncidentCategory.STARTUP;
        }
        return IncidentCategory.UNKNOWN;
    }

    private static IncidentSeverity severityFromText(String lower) {
        if (containsAny(lower, "critical", "fatal", "outage", "severity=1",
                "p1 incident", "unrecoverable")) {
            return IncidentSeverity.CRITICAL;
        }
        if (containsAny(lower, "error", "exception", "failed", "failure",
                "unavailable", "crash")) {
            return IncidentSeverity.HIGH;
        }
        if (containsAny(lower, "warn", "warning", "degraded", "slow",
                "latency", "timeout")) {
            return IncidentSeverity.MEDIUM;
        }
        if (containsAny(lower, "info", "notice")) {
            return IncidentSeverity.LOW;
        }
        return IncidentSeverity.UNKNOWN;
    }

    /**
     * Walks the runtime class hierarchy of {@code t} and returns true if any
     * superclass FQCN matches one of the candidates. Used for third-party types
     * (Spring, Hibernate, JPA) that we deliberately do not depend on at compile
     * time so core stays framework-free.
     */
    private static boolean isInstanceOfByName(Throwable t, String... candidates) {
        Class<?> c = t.getClass();
        while (c != null && c != Object.class) {
            String name = c.getName();
            for (String candidate : candidates) {
                if (name.equals(candidate)) {
                    return true;
                }
            }
            c = c.getSuperclass();
        }
        return false;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
