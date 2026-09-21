package com.galitianu.jev4j;

import static com.galitianu.jev4j.TestSupport.noulAnswer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.galitianu.jev4j.errors.InternalServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * System.Logger falls through to java.util.logging when no LoggerFinder is installed, which is
 * what happens in a plain JVM, so a JUL handler sees exactly what an unconfigured application
 * would see.
 */
class LoggingTest {

    private static final String API_KEY = "test-key";

    private FakeApi api;
    private Capture capture;
    private Logger root;
    private Level originalLevel;

    static final class Capture extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        List<LogRecord> atLeast(Level level) {
            return records.stream().filter(r -> r.getLevel().intValue() >= level.intValue()).toList();
        }

        List<LogRecord> from(String loggerName) {
            return records.stream().filter(r -> loggerName.equals(r.getLoggerName())).toList();
        }

        String allText() {
            StringBuilder sb = new StringBuilder();
            for (LogRecord r : records) {
                sb.append(r.getLoggerName()).append(' ').append(r.getMessage()).append('\n');
            }
            return sb.toString();
        }
    }

    @BeforeEach
    void start() throws Exception {
        api = new FakeApi();
        capture = new Capture();
        capture.setLevel(Level.ALL);
        root = Logger.getLogger("com.galitianu.jev4j");
        originalLevel = root.getLevel();
        root.setLevel(Level.ALL);
        root.addHandler(capture);
    }

    @AfterEach
    void stop() {
        root.removeHandler(capture);
        root.setLevel(originalLevel);
        api.close();
    }

    @Test
    void successfulCallLogsNothingAtInfoOrAbove() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            client.systemOne("s", Noul.of("x"));
        }
        assertEquals(List.of(), capture.atLeast(Level.INFO),
            "a library must stay silent at INFO and above; an application decides what it logs");
        assertFalse(capture.from("com.galitianu.jev4j.http").isEmpty(), "the call should still be visible at DEBUG");
    }

    @Test
    void failedCallLogsNothingAtInfoOrAboveBecauseItThrows() {
        api.json(500, Map.of("error", "boom"));
        try (TypeSafeClient client = TestSupport.client(api).retry(RetryPolicy.none()).build()) {
            assertThrows(InternalServerException.class, () -> client.systemOne("s", Noul.of("x")));
        }
        assertEquals(List.of(), capture.atLeast(Level.INFO),
            "the failure reaches the caller as an exception; logging it too reports it twice");
    }

    @Test
    void bodiesGoToTheWireLoggerOnlySoHttpDebuggingDoesNotLeakUserContent() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            client.systemOne("a customer wrote something private", Noul.of("x"));
        }
        String http = capture.from("com.galitianu.jev4j.http").stream()
            .map(LogRecord::getMessage).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(http.contains("something private"),
            "the state is the caller's user content and must not appear on the http logger");
        String wire = capture.from("com.galitianu.jev4j.wire").stream()
            .map(LogRecord::getMessage).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(wire.contains("something private"), "the wire logger is where bodies belong");
    }

    @Test
    void theApiKeyNeverReachesALogRecord() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).defaultHeader("X-Trace", "abc").build()) {
            client.systemOne("s", Noul.of("x"));
        }
        assertFalse(capture.allText().contains(API_KEY), "the API key must never be logged, at any level");
        assertFalse(capture.allText().toLowerCase().contains("authorization"), "nor the header carrying it");
    }

    @Test
    void aSilentlyDroppedHeaderIsWarnedAbout() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).defaultHeader("User-Agent", "mine/1.0").build()) {
            client.systemOne("s", Noul.of("x"));
        }
        List<LogRecord> warnings = capture.atLeast(Level.WARNING);
        assertEquals(1, warnings.size(), "the caller asked for something that did not happen");
        assertTrue(warnings.get(0).getMessage().contains("User-Agent"));
    }
}
