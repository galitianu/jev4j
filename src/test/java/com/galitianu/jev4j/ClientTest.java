package com.galitianu.jev4j;

import static com.galitianu.jev4j.TestSupport.choiceAnswer;
import static com.galitianu.jev4j.TestSupport.list;
import static com.galitianu.jev4j.TestSupport.noulAnswer;
import static com.galitianu.jev4j.TestSupport.scoreAnswer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.galitianu.jev4j.errors.ApiConnectionException;
import com.galitianu.jev4j.errors.ApiException;
import com.galitianu.jev4j.errors.ApiTimeoutException;
import com.galitianu.jev4j.errors.AuthenticationException;
import com.galitianu.jev4j.errors.InternalServerException;
import com.galitianu.jev4j.errors.PermissionDeniedException;
import com.galitianu.jev4j.errors.RateLimitException;
import com.galitianu.jev4j.errors.TypeSafeException;
import com.galitianu.jev4j.errors.UnprocessableEntityException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientTest {

    private FakeApi api;

    @BeforeEach
    void start() throws Exception {
        api = new FakeApi();
    }

    @AfterEach
    void stop() {
        api.close();
    }

    record Ticket(String subject, String body) {}

    @Test
    void sendsExpectedBodyAndHeadersAndDecodesTypedAnswers() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("q0", noulAnswer(0.91));
        answers.put("tone", choiceAnswer("very_angry", 0.7, Map.of("calm", 0.1, "frustrated", 0.2, "very_angry", 0.7)));
        answers.put("q1", scoreAnswer(1.4, 0.55, list("can wait", "this week", "today"), List.of(0.05, 0.5, 0.45)));
        api.ok(answers);

        var isBilling = Noul.of("Is this ticket about billing?");
        var tone = Choice.of("Tone?", TestSupport.Tone.class).named("tone");
        var urgency = Score.of("Urgency?", "can wait", "this week", "today");

        try (TypeSafeClient client = TestSupport.client(api).defaultModel("jev-test").defaultHeader("X-Trace", "abc").build()) {
            SystemOneResponse res = client.systemOne(new Ticket("Charged twice", "Please fix."), isBilling, tone, urgency);

            assertEquals("jev-1.13.0", res.model());
            assertEquals(12, res.usage().inputTokens());
            assertEquals(0.91, res.get(isBilling).noul());
            assertTrue(res.get(isBilling).isYes(0.9));
            ChoiceAnswer<TestSupport.Tone> t = res.get(tone);
            assertEquals(TestSupport.Tone.ANGRY, t.choice());
            assertEquals(0.7, t.confidence());
            assertEquals(0.2, t.probability(TestSupport.Tone.FRUSTRATED));
            ScoreAnswer s = res.get(urgency);
            assertEquals(1.4, s.score());
            assertEquals(1, s.nearestLevel());
            assertEquals(1, s.topLevel());
            assertEquals("this week", s.nearestDescription());
            assertEquals(3, s.legend().size());
            assertEquals(1, res.nouls().size());
            assertEquals(1, res.choices().size());
            assertEquals(1, res.scores().size());
        }

        FakeApi.Recorded req = api.requests.get(0);
        assertEquals("POST", req.method());
        assertEquals("/v1/systemone", req.path());
        assertEquals("Bearer test-key", req.header("Authorization"));
        assertEquals("application/json", req.header("Content-Type"));
        assertEquals("abc", req.header("X-Trace"));
        assertEquals(Version.USER_AGENT, req.header("X-Typesafe-Sdk"));
        assertTrue(req.header("User-Agent").startsWith("jev4j/"));
        assertNull(req.header("X-Typesafe-Retry-Count"));

        JsonNode body = req.body();
        assertEquals("jev-test", body.get("model").asText());
        assertEquals("Charged twice", body.get("state").get("subject").asText());
        JsonNode qs = body.get("questions");
        assertEquals(List.of("q0", "tone", "q1"), fieldNames(qs));
        assertEquals("noul", qs.get("q0").get("type").asText());
        assertEquals("hostile", qs.get("tone").get("criteria").get("very_angry").asText());
        assertEquals("today", qs.get("q1").get("criteria").get(2).asText());
    }

    @Test
    void stringKeyedQuestionsAndModelOverrideAndExtraBody() {
        api.ok(Map.of("team", choiceAnswer("shipping", 0.8, Map.of("shipping", 0.8, "refunds", 0.2))));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            SystemOneRequest req = SystemOneRequest.builder()
                .state("Box arrived crushed")
                .model("jev-1.0.0")
                .question("team", Choice.of("Team?", "shipping", "refunds"))
                .extraBody("metadata", Map.of("tenant", "acme"))
                .build();
            SystemOneResponse res = client.systemOne(req);
            assertEquals("shipping", res.choice("team").choice());
            assertThrows(TypeSafeException.class, () -> res.noul("team"));
            assertThrows(TypeSafeException.class, () -> res.answer("missing"));
        }
        JsonNode body = api.requests.get(0).body();
        assertEquals("jev-1.0.0", body.get("model").asText());
        assertEquals("acme", body.get("metadata").get("tenant").asText());
    }

    @Test
    void asyncReturnsSameResult() throws Exception {
        api.ok(Map.of("q0", noulAnswer(0.2)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            Noul q = Noul.of("x");
            SystemOneResponse res = client.systemOneAsync("state", q).get();
            assertEquals(0.2, res.get(q).noul());
        }
    }

    @Test
    void retriesOn429HonoringRetryAfterMsThenSucceeds() {
        api.json(429, Map.of("error", "slow down"), Map.of("retry-after-ms", "1"));
        api.json(529, Map.of("error", "overloaded"));
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            Noul q = Noul.of("x");
            assertEquals(0.5, client.systemOne("s", q).get(q).noul());
        }
        assertEquals(3, api.requests.size());
        assertNull(api.requests.get(0).header("X-Typesafe-Retry-Count"));
        assertEquals("1", api.requests.get(1).header("X-Typesafe-Retry-Count"));
        assertEquals("2", api.requests.get(2).header("X-Typesafe-Retry-Count"));
    }

    @Test
    void givesUpAfterMaxRetriesWithRateLimitException() {
        api.json(429, Map.of("error", "slow down"), Map.of("Retry-After", "0"));
        api.json(429, Map.of("error", "slow down"), Map.of("Retry-After", "0"));
        try (TypeSafeClient client = TestSupport.client(api).retry(RetryPolicy.defaults().withMaxRetries(1)).build()) {
            RateLimitException e = assertThrows(RateLimitException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals(429, e.status());
            assertEquals("429 slow down", e.getMessage());
            assertEquals(Duration.ZERO, e.retryAfter().orElseThrow());
        }
        assertEquals(2, api.requests.size());
    }

    @Test
    void doesNotRetryNonRetryableStatusesAndMapsExceptionTypes() {
        api.json(401, Map.of("detail", "Invalid API key"), Map.of("x-typesafe-request-id", "req_123"));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            AuthenticationException e = assertThrows(AuthenticationException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals("401 Invalid API key", e.getMessage());
            assertEquals("req_123", e.requestId().orElseThrow());
        }
        assertEquals(1, api.requests.size());

        api.json(422, Map.of("detail", List.of(Map.of("loc", List.of("body", "questions", "q0"), "msg", "field required"))));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            UnprocessableEntityException e = assertThrows(UnprocessableEntityException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals("422 questions.q0: field required", e.getMessage());
        }

        api.text(403, "");
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            ApiException e = assertThrows(PermissionDeniedException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals("403 status code (no body)", e.getMessage());
            assertNull(e.body());
        }

        api.text(418, "I'm a teapot");
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            ApiException e = assertThrows(ApiException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals(418, e.status());
            assertEquals("418 I'm a teapot", e.getMessage());
        }
    }

    @Test
    void timesOutPerAttemptAndRetriesTimeouts() {
        api.delayMs = 300;
        api.ok(Map.of("q0", noulAnswer(0.5)));
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api)
            .timeout(Duration.ofMillis(100))
            .retry(RetryPolicy.defaults().withMaxRetries(1).withBackoff(Duration.ofMillis(1), Duration.ofMillis(1)))
            .build()) {
            ApiTimeoutException e = assertThrows(ApiTimeoutException.class, () -> client.systemOne("s", Noul.of("x")));
            assertEquals(Duration.ofMillis(100), e.timeout());
        }
        assertEquals(2, api.requests.size());
    }

    @Test
    void perCallOptionsOverrideClientRetryAndTimeout() {
        api.json(500, Map.of("error", "boom"));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            SystemOneRequest req = SystemOneRequest.builder()
                .state("s").question(Noul.of("x"))
                .options(RequestOptions.none().withRetry(RetryPolicy.none()).withHeader("X-Call", "1"))
                .build();
            assertThrows(InternalServerException.class, () -> client.systemOne(req));
        }
        assertEquals(1, api.requests.size());
        assertEquals("1", api.requests.get(0).header("X-Call"));
    }

    @Test
    void closeIsIdempotentAndLeavesASuppliedHttpClientAlone() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        TypeSafeClient owned = TestSupport.client(api).build();
        owned.close();
        owned.close();

        HttpClient supplied = HttpClient.newHttpClient();
        try (TypeSafeClient client = TestSupport.client(api).httpClient(supplied).build()) {
            assertEquals(0.5, client.systemOne("s", Noul.of("x")).noul("q0").noul());
        }
        // The SDK did not own it, so it must still work after the client was closed.
        api.ok(Map.of("q0", noulAnswer(0.25)));
        try (TypeSafeClient client = TestSupport.client(api).httpClient(supplied).build()) {
            assertEquals(0.25, client.systemOne("s", Noul.of("x")).noul("q0").noul());
        }
    }

    @Test
    void connectionErrorsSurfaceAsApiConnectionException() {
        api.close();
        try (TypeSafeClient client = TestSupport.client(api).retry(RetryPolicy.none()).build()) {
            TypeSafeException e = assertThrows(ApiConnectionException.class, () -> client.systemOne("s", Noul.of("x")));
            assertInstanceOf(ApiConnectionException.class, e);
        }
    }

    @Test
    void rejectsMissingAnswerAndTypeMismatch() {
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            assertThrows(TypeSafeException.class, () -> client.systemOne("s", Noul.of("a"), Noul.of("b")));
        }
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            assertThrows(TypeSafeException.class, () -> client.systemOne("s", Score.of("a", "x", "y")));
        }
    }

    @Test
    void listsModels() {
        api.json(200, Map.of("models", List.of(Map.of("name", "jev-1.13.0", "description", "flagship", "release_date", "2026-01-01"))));
        try (TypeSafeClient client = TestSupport.client(api).build()) {
            List<ModelCard> models = client.models().list();
            assertEquals(1, models.size());
            assertEquals("jev-1.13.0", models.get(0).name());
            assertEquals("2026-01-01", models.get(0).releaseDate());
        }
        assertEquals("GET", api.requests.get(0).method());
        assertEquals("/v1/models", api.requests.get(0).path());
        assertNull(api.requests.get(0).header("Content-Type"));
    }

    @Test
    void builderRequiresApiKeyAndTrimsBaseUrl() {
        assertThrows(TypeSafeException.class, () -> TypeSafeClient.builder().apiKey(" ").build());
        api.ok(Map.of("q0", noulAnswer(0.5)));
        try (TypeSafeClient client = TypeSafeClient.builder().apiKey("k").baseUrl(api.baseUrl() + "/").build()) {
            client.systemOne("s", Noul.of("x"));
        }
        assertEquals("/v1/systemone", api.requests.get(0).path());
    }

    private static List<String> fieldNames(JsonNode n) {
        List<String> names = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
