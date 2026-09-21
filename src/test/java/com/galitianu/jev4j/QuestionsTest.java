package com.galitianu.jev4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galitianu.jev4j.errors.TypeSafeException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuestionsTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode wire(Question<?> q) {
        return M.valueToTree(q.toWire());
    }

    @Test
    void noulWithoutCriteriaOmitsCriteria() {
        JsonNode w = wire(Noul.of("Is this billing?"));
        assertEquals("noul", w.get("type").asText());
        assertEquals("Is this billing?", w.get("instructions").asText());
        assertFalse(w.has("criteria"));
    }

    @Test
    void noulWithCriteriaSendsTrueFalseKeys() {
        JsonNode w = wire(Noul.of("Escalate?", "needs a manager", "routine"));
        assertEquals("needs a manager", w.get("criteria").get("true").asText());
        assertEquals("routine", w.get("criteria").get("false").asText());
    }

    @Test
    void instructionsCanBeStructured() {
        JsonNode w = wire(Noul.of(Map.of("question", "Does `record` match?", "record", Map.of("id", 7))));
        assertEquals(7, w.get("instructions").get("record").get("id").asInt());
    }

    @Test
    void enumChoiceUsesLowercaseNamesLabelsAndDescriptions() {
        Choice<TestSupport.Tone> q = Choice.of("Tone?", TestSupport.Tone.class);
        JsonNode c = wire(q).get("criteria");
        assertEquals(List.of("calm", "frustrated", "very_angry"), List.copyOf(fieldNames(c)));
        assertTrue(c.get("calm").isNull());
        assertEquals("irritated but polite", c.get("frustrated").asText());
        assertEquals("hostile", c.get("very_angry").asText());
        assertEquals(TestSupport.Tone.ANGRY, q.decodeLabel("very_angry"));
        assertThrows(TypeSafeException.class, () -> q.decodeLabel("angry"));
    }

    @Test
    void stringChoicePreservesOrderAndNulls() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("shipping", "damage or loss");
        criteria.put("refunds", null);
        JsonNode c = wire(Choice.of("Team?", criteria)).get("criteria");
        assertEquals(List.of("shipping", "refunds"), List.copyOf(fieldNames(c)));
        assertTrue(c.get("refunds").isNull());
        assertNull(Choice.of("x", "a", "b").criteria().get("a"));
    }

    @Test
    void choiceRejectsEmptyAndDuplicateLabels() {
        assertThrows(TypeSafeException.class, () -> Choice.of("x"));
        assertThrows(TypeSafeException.class, () -> Choice.of("x", "a", "a"));
        assertThrows(TypeSafeException.class, () -> Choice.of("x", Map.of()));
    }

    @Test
    void scoreSendsLevelsAsArray() {
        JsonNode w = wire(Score.of("Urgency?", "low", "mid", "high"));
        assertTrue(w.get("criteria").isArray());
        assertEquals(3, w.get("criteria").size());
    }

    @Test
    void scoreRequiresBetweenTwoAndTenLevels() {
        assertThrows(TypeSafeException.class, () -> Score.of("x", "only one"));
        assertThrows(TypeSafeException.class, () -> Score.of("x", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")));
        assertEquals(2, Score.of("x", "a", "b").levels().size());
    }

    @Test
    void namedReturnsCopyWithKey() {
        Noul q = Noul.of("x");
        Noul named = q.named("billing");
        assertTrue(q.name().isEmpty());
        assertEquals("billing", named.name().orElseThrow());
        assertThrows(TypeSafeException.class, () -> q.named(" "));
    }

    @Test
    void requestAssignsPositionalKeysAndHonorsNames() {
        Noul a = Noul.of("a");
        Noul b = Noul.of("b").named("second");
        Noul c = Noul.of("c");
        SystemOneRequest r = SystemOneRequest.builder().state("s").questions(a, b, c).question("explicit", Noul.of("d")).build();
        assertEquals(List.of("q0", "second", "q1", "explicit"), List.copyOf(r.questions().keySet()));
        assertEquals("q1", r.keyOf(c));
        assertThrows(TypeSafeException.class, () -> r.keyOf(Noul.of("other")));
    }

    @Test
    void requestRejectsDuplicatesAndEmpty() {
        Noul a = Noul.of("a");
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder().state("s").questions(a, a).build());
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder().state("s").questions(Noul.of("x").named("k"), Noul.of("y").named("k")).build());
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder().state("s").build());
    }

    private static List<String> fieldNames(JsonNode n) {
        List<String> names = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
