package com.incident.copilot.core.analysis;

import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins down the classification rules. These are the user-visible contract
 * behind metric tags, so regressions here directly alter dashboards and
 * alerts — treat changes as breaking unless new buckets are additive.
 */
class IncidentClassifierTest {

    private final IncidentClassifier classifier = new IncidentClassifier();

    // ----- Throwable classification -----

    @Test
    void classify_outOfMemoryError_isCriticalMemory() {
        Classification c = classifier.classify(new OutOfMemoryError("Java heap space"));

        assertEquals(IncidentSeverity.CRITICAL, c.severity());
        assertEquals(IncidentCategory.MEMORY, c.category());
    }

    @Test
    void classify_stackOverflow_isHighMemory() {
        Classification c = classifier.classify(new StackOverflowError());

        assertEquals(IncidentSeverity.HIGH, c.severity());
        assertEquals(IncidentCategory.MEMORY, c.category());
    }

    @Test
    void classify_sqlException_isHighDatabase() {
        Classification c = classifier.classify(new SQLException("duplicate key"));

        assertEquals(IncidentSeverity.HIGH, c.severity());
        assertEquals(IncidentCategory.DATABASE, c.category());
    }

    @Test
    void classify_sqlSubclass_stillMatchesDatabase() {
        Classification c = classifier.classify(
                new SQLIntegrityConstraintViolationException("unique constraint"));

        assertEquals(IncidentCategory.DATABASE, c.category());
    }

    @Test
    void classify_connectException_isHighNetwork() {
        Classification c = classifier.classify(new ConnectException("connection refused"));

        assertEquals(IncidentSeverity.HIGH, c.severity());
        assertEquals(IncidentCategory.NETWORK, c.category());
    }

    @Test
    void classify_socketTimeout_isHighNetwork() {
        Classification c = classifier.classify(new SocketTimeoutException("read timed out"));

        assertEquals(IncidentCategory.NETWORK, c.category());
    }

    @Test
    void classify_unknownHost_isHighNetwork() {
        Classification c = classifier.classify(new UnknownHostException("db-primary.internal"));

        assertEquals(IncidentCategory.NETWORK, c.category());
    }

    @Test
    void classify_timeoutException_isMediumConcurrency() {
        Classification c = classifier.classify(new TimeoutException("future timed out"));

        assertEquals(IncidentSeverity.MEDIUM, c.severity());
        assertEquals(IncidentCategory.CONCURRENCY, c.category());
    }

    @Test
    void classify_fileNotFound_isMediumIo() {
        Classification c = classifier.classify(new FileNotFoundException("/var/log/app.log"));

        assertEquals(IncidentSeverity.MEDIUM, c.severity());
        assertEquals(IncidentCategory.IO, c.category());
    }

    @Test
    void classify_genericIoException_fallsBackToIo() {
        Classification c = classifier.classify(new IOException("broken pipe"));

        assertEquals(IncidentCategory.IO, c.category());
    }

    @Test
    void classify_walksCauseChain() {
        Throwable root = new SQLException("deadlock detected");
        Throwable wrapper = new RuntimeException("wrapped", root);

        Classification c = classifier.classify(wrapper);

        assertEquals(IncidentCategory.DATABASE, c.category());
    }

    @Test
    void classify_unknownException_fallsBackToMediumUnknown() {
        Classification c = classifier.classify(new IllegalStateException("something odd"));

        assertEquals(IncidentSeverity.MEDIUM, c.severity());
        assertEquals(IncidentCategory.UNKNOWN, c.category());
    }

    @Test
    void classify_deeplyNestedChain_terminates() {
        // Build a 50-deep wrapper chain with no match anywhere; the classifier
        // must terminate via its depth cap and fall back to MEDIUM/UNKNOWN.
        Throwable t = new IllegalStateException("leaf");
        for (int i = 0; i < 50; i++) {
            t = new RuntimeException("wrap-" + i, t);
        }

        Classification c = classifier.classify(t);

        assertNotNull(c);
        assertEquals(IncidentSeverity.MEDIUM, c.severity());
        assertEquals(IncidentCategory.UNKNOWN, c.category());
    }

    @Test
    void classify_nullThrowable_isUnknown() {
        assertEquals(Classification.UNKNOWN, classifier.classify((Throwable) null));
    }

    // ----- Text classification -----

    @Test
    void classify_textMentioningHeap_isMemory() {
        Classification c = classifier.classify(
                "java.lang.OutOfMemoryError: Java heap space at CacheManager.put");

        assertEquals(IncidentCategory.MEMORY, c.category());
        assertEquals(IncidentSeverity.HIGH, c.severity());
    }

    @Test
    void classify_textMentioningDeadlock_isConcurrency() {
        Classification c = classifier.classify(
                "Thread http-nio-8080-exec-3 is BLOCKED waiting for monitor; deadlock detected");

        assertEquals(IncidentCategory.CONCURRENCY, c.category());
    }

    @Test
    void classify_textMentioningConnectionRefused_isNetwork() {
        Classification c = classifier.classify(
                "Failed to connect: Connection refused to db-primary.internal:5432");

        assertEquals(IncidentCategory.NETWORK, c.category());
    }

    @Test
    void classify_textMentioningJdbc_isDatabase() {
        Classification c = classifier.classify(
                "JDBC query timeout on SELECT from orders; connection pool exhausted");

        assertEquals(IncidentCategory.DATABASE, c.category());
    }

    @Test
    void classify_textMentioningBean_isConfiguration() {
        Classification c = classifier.classify(
                "BeanCreationException: could not resolve placeholder 'db.url'");

        assertEquals(IncidentCategory.CONFIGURATION, c.category());
    }

    @Test
    void classify_textMentioningContextRefresh_isStartup() {
        Classification c = classifier.classify(
                "Application failed to start: context refresh aborted");

        assertEquals(IncidentCategory.STARTUP, c.category());
    }

    @Test
    void classify_textWithCriticalKeyword_isCritical() {
        Classification c = classifier.classify("CRITICAL outage on payment service");

        assertEquals(IncidentSeverity.CRITICAL, c.severity());
    }

    @Test
    void classify_textWithWarning_isMedium() {
        Classification c = classifier.classify("Degraded latency observed on /api/users");

        assertEquals(IncidentSeverity.MEDIUM, c.severity());
    }

    @Test
    void classify_blankText_isUnknown() {
        assertEquals(Classification.UNKNOWN, classifier.classify(""));
        assertEquals(Classification.UNKNOWN, classifier.classify("   "));
        assertEquals(Classification.UNKNOWN, classifier.classify((String) null));
    }

    @Test
    void classify_textWithNoMatchingKeywords_isUnknown() {
        assertEquals(Classification.UNKNOWN, classifier.classify("lorem ipsum dolor sit amet"));
    }
}
