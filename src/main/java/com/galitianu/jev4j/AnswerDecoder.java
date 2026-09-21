package com.galitianu.jev4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galitianu.jev4j.errors.TypeSafeException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Turns the JSON of {@code POST /v1/systemone} into a {@link SystemOneResponse}. */
final class AnswerDecoder {

    private final ObjectMapper mapper;

    AnswerDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    SystemOneResponse decode(SystemOneRequest request, JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new TypeSafeException("Unexpected response from POST /v1/systemone; expected a JSON object.");
        }
        JsonNode answersNode = root.path("answers");
        if (!answersNode.isObject()) {
            throw new TypeSafeException("Unexpected response from POST /v1/systemone; missing \"answers\".");
        }
        Map<String, Answer> answers = new LinkedHashMap<>();
        for (Map.Entry<String, Question<?>> e : request.questions().entrySet()) {
            JsonNode node = answersNode.get(e.getKey());
            if (node == null) {
                throw new TypeSafeException("The response has no answer for question \"" + e.getKey() + "\".");
            }
            answers.put(e.getKey(), decodeAnswer(e.getKey(), e.getValue(), node));
        }
        JsonNode usage = root.path("usage");
        return new SystemOneResponse(
            root.path("model").asText(null),
            answers,
            request,
            new Usage(usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong()));
    }

    Answer decodeAnswer(String key, Question<?> question, JsonNode node) {
        String type = node.path("type").asText(question.type());
        if (!type.equals(question.type())) {
            throw new TypeSafeException("Answer \"" + key + "\" has type \"" + type + "\" but the question was a " + question.type() + ".");
        }
        // An if/instanceof chain rather than a pattern switch: the library compiles
        // against Java 17, where switch patterns are still a preview feature.
        if (question instanceof Noul) {
            return new NoulAnswer(requireNumber(key, node, "noul"));
        }
        if (question instanceof Choice<?> c) {
            return decodeChoice(key, c, node);
        }
        if (question instanceof Score) {
            return decodeScore(key, node);
        }
        throw new TypeSafeException("Unknown question type: " + question.getClass().getName());
    }

    private <L> ChoiceAnswer<L> decodeChoice(String key, Choice<L> question, JsonNode node) {
        JsonNode choice = node.get("choice");
        if (choice == null || !choice.isTextual()) {
            throw new TypeSafeException("Answer \"" + key + "\" is missing \"choice\".");
        }
        Map<L, Double> probabilities = new LinkedHashMap<>();
        JsonNode probs = node.path("probabilities");
        for (Map.Entry<String, JsonNode> p : probs.properties()) {
            probabilities.put(question.decodeLabel(p.getKey()), p.getValue().asDouble());
        }
        return new ChoiceAnswer<>(question.decodeLabel(choice.asText()), node.path("confidence").asDouble(), probabilities);
    }

    private ScoreAnswer decodeScore(String key, JsonNode node) {
        Map<Integer, Object> legend = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : node.path("legend").properties()) {
            legend.put(parseLevel(key, e.getKey()), toPlain(e.getValue()));
        }
        Map<Integer, Double> probabilities = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : node.path("probabilities").properties()) {
            probabilities.put(parseLevel(key, e.getKey()), e.getValue().asDouble());
        }
        return new ScoreAnswer(requireNumber(key, node, "score"), node.path("confidence").asDouble(), legend, probabilities);
    }

    private Object toPlain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return mapper.treeToValue(node, Object.class);
        } catch (Exception e) {
            throw new TypeSafeException("Cannot decode legend entry: " + node, e);
        }
    }

    private static int parseLevel(String key, String level) {
        try {
            return Integer.parseInt(level);
        } catch (NumberFormatException e) {
            throw new TypeSafeException("Answer \"" + key + "\" has a non-integer score level \"" + level + "\".", e);
        }
    }

    private static double requireNumber(String key, JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isNumber()) {
            throw new TypeSafeException("Answer \"" + key + "\" is missing numeric \"" + field + "\".");
        }
        return v.asDouble();
    }
}
