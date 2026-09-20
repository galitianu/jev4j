package com.galitianu.jev4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TestSupport {
    private TestSupport() {}

    enum Tone {
        CALM,
        @Describe("irritated but polite") FRUSTRATED,
        @Label("very_angry") @Describe("hostile") ANGRY
    }

    static TypeSafeClient.Builder client(FakeApi api) {
        return TypeSafeClient.builder()
            .apiKey("test-key")
            .baseUrl(api.baseUrl())
            .timeout(Duration.ofSeconds(5))
            .retry(RetryPolicy.defaults().withBackoff(Duration.ofMillis(1), Duration.ofMillis(5)))
            .random(() -> 0.0);
    }

    static Map<String, Object> noulAnswer(double p) {
        return Map.of("type", "noul", "noul", p);
    }

    static Map<String, Object> choiceAnswer(String choice, double confidence, Map<String, Double> probs) {
        return Map.of("type", "choice", "choice", choice, "confidence", confidence, "probabilities", probs);
    }

    static Map<String, Object> scoreAnswer(double score, double confidence, List<String> legend, List<Double> probs) {
        Map<String, Object> l = new java.util.LinkedHashMap<>();
        Map<String, Object> p = new java.util.LinkedHashMap<>();
        for (int i = 0; i < legend.size(); i++) {
            l.put(String.valueOf(i), legend.get(i));
            p.put(String.valueOf(i), probs.get(i));
        }
        return Map.of("type", "score", "score", score, "confidence", confidence, "legend", l, "probabilities", p);
    }

    static List<String> list(String... s) {
        return new ArrayList<>(List.of(s));
    }
}
