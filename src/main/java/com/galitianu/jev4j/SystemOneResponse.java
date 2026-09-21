package com.galitianu.jev4j;

import com.galitianu.jev4j.errors.TypeSafeException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Answers keyed by question, with the resolved model and token usage. */
public final class SystemOneResponse {

    private final String model;
    private final Map<String, Answer> answers;
    private final SystemOneRequest request;
    private final Usage usage;

    SystemOneResponse(String model, Map<String, Answer> answers, SystemOneRequest request, Usage usage) {
        this.model = model;
        this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        this.request = request;
        this.usage = usage;
    }

    /** The concrete model that answered, for example {@code jev-1.13.0}. */
    public String model() {
        return model;
    }

    /** Token usage for the request. */
    public Usage usage() {
        return usage;
    }

    /** The request these answers belong to. */
    public SystemOneRequest request() {
        return request;
    }

    /** All answers by wire key. */
    public Map<String, Answer> answers() {
        return answers;
    }

    /** The typed answer for a question handle that was part of the request. */
    @SuppressWarnings("unchecked")
    public <A extends Answer> A get(Question<A> question) {
        return (A) answer(request.keyOf(question));
    }

    /** The answer under a wire key. */
    public Answer answer(String key) {
        Answer a = answers.get(key);
        if (a == null) {
            throw new TypeSafeException("No answer for question \"" + key + "\".");
        }
        return a;
    }

    public NoulAnswer noul(String key) {
        return as(key, NoulAnswer.class);
    }

    public ChoiceAnswer<?> choice(String key) {
        return as(key, ChoiceAnswer.class);
    }

    public ScoreAnswer score(String key) {
        return as(key, ScoreAnswer.class);
    }

    /** All noul answers by key. */
    public Map<String, NoulAnswer> nouls() {
        return ofType(NoulAnswer.class);
    }

    /** All choice answers by key. */
    public Map<String, ChoiceAnswer<?>> choices() {
        return answers.entrySet().stream()
            .filter(e -> e.getValue() instanceof ChoiceAnswer)
            .collect(Collectors.toMap(Map.Entry::getKey, e -> (ChoiceAnswer<?>) e.getValue(), (a, b) -> a, LinkedHashMap::new));
    }

    /** All score answers by key. */
    public Map<String, ScoreAnswer> scores() {
        return ofType(ScoreAnswer.class);
    }

    private <T extends Answer> T as(String key, Class<T> type) {
        Answer a = answer(key);
        if (!type.isInstance(a)) {
            throw new TypeSafeException("Answer \"" + key + "\" is a " + a.getClass().getSimpleName() + ", not a " + type.getSimpleName() + ".");
        }
        return type.cast(a);
    }

    private <T extends Answer> Map<String, T> ofType(Class<T> type) {
        return answers.entrySet().stream()
            .filter(e -> type.isInstance(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, e -> type.cast(e.getValue()), (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public String toString() {
        return "SystemOneResponse{model=" + model + ", answers=" + answers + ", usage=" + usage + "}";
    }
}
