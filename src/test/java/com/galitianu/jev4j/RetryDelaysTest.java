package com.galitianu.jev4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetryDelaysTest {

    private static HttpHeaders headers(Map<String, String> h) {
        Map<String, List<String>> m = new java.util.HashMap<>();
        h.forEach((k, v) -> m.put(k, List.of(v)));
        return HttpHeaders.of(m, (a, b) -> true);
    }

    @Test
    void exponentialBackoffWithCapAndJitter() {
        RetryPolicy p = RetryPolicy.defaults();
        assertEquals(Duration.ofMillis(500), RetryDelays.delayFor(0, null, p, () -> 0.0));
        assertEquals(Duration.ofMillis(1000), RetryDelays.delayFor(1, null, p, () -> 0.0));
        assertEquals(Duration.ofMillis(5000), RetryDelays.delayFor(10, null, p, () -> 0.0));
        assertEquals(Duration.ofMillis(375), RetryDelays.delayFor(0, null, p, () -> 1.0));
    }

    @Test
    void prefersRetryAfterMsThenSecondsThenDate() {
        assertEquals(Duration.ofMillis(250), RetryDelays.parseRetryAfter(headers(Map.of("retry-after-ms", "250", "retry-after", "5"))).orElseThrow());
        assertEquals(Duration.ofMillis(1500), RetryDelays.parseRetryAfter(headers(Map.of("retry-after", "1.5"))).orElseThrow());
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(30));
        Duration d = RetryDelays.parseRetryAfter(headers(Map.of("retry-after", date))).orElseThrow();
        assertTrue(d.toSeconds() >= 28 && d.toSeconds() <= 30);
        assertTrue(RetryDelays.parseRetryAfter(headers(Map.of("retry-after", "garbage"))).isEmpty());
        assertTrue(RetryDelays.parseRetryAfter(headers(Map.of("retry-after", "-3"))).isEmpty());
    }

    @Test
    void serverDelayBeyondMaxFallsBackToBackoff() {
        RetryPolicy p = RetryPolicy.defaults();
        assertEquals(Duration.ofSeconds(2), RetryDelays.delayFor(0, headers(Map.of("retry-after", "2")), p, () -> 0.0));
        assertEquals(Duration.ofMillis(500), RetryDelays.delayFor(0, headers(Map.of("retry-after", "3600")), p, () -> 0.0));
        RetryPolicy ignore = p.withRespectRetryAfter(false, Duration.ofSeconds(60));
        assertEquals(Duration.ofMillis(500), RetryDelays.delayFor(0, headers(Map.of("retry-after", "2")), ignore, () -> 0.0));
    }

    @Test
    void policyDefaultsMatchSiblingSdks() {
        RetryPolicy p = RetryPolicy.defaults();
        assertEquals(2, p.maxRetries());
        assertTrue(p.retries(408));
        assertTrue(p.retries(429));
        assertTrue(p.retries(529));
        assertTrue(p.retries(599));
        assertTrue(!p.retries(422));
        assertTrue(!p.retries(600));
        assertTrue(p.retries(new ApiTimeoutException(Duration.ofSeconds(1), null)));
        assertTrue(!p.withRetryTimeouts(false).retries(new ApiTimeoutException(Duration.ofSeconds(1), null)));
        assertTrue(!p.retries(new TypeSafeException("x")));
    }
}
