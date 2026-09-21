package com.galitianu.jev4j;

import com.galitianu.jev4j.errors.TypeSafeException;
import com.galitianu.jev4j.internal.Keys;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State plus named questions for {@code POST /v1/systemone}. Build one with {@link #builder()} when
 * you need a model override, explicit keys, extra body fields, or per-call options; otherwise use
 * {@link TypeSafeClient#systemOne(Object, Question...)}.
 */
public final class SystemOneRequest {

    private final Object state;
    private final String model;
    private final Map<String, Question<?>> questions;
    private final Map<Question<?>, String> keys;
    private final Map<String, Object> extraBody;
    private final RequestOptions options;

    private SystemOneRequest(Builder b) {
        this.state = b.state;
        this.model = b.model;
        this.extraBody = Collections.unmodifiableMap(new LinkedHashMap<>(b.extraBody));
        this.options = b.options;

        Map<String, Question<?>> byKey = new LinkedHashMap<>();
        Map<Question<?>, String> byHandle = new IdentityHashMap<>();
        int index = 0;
        for (Question<?> q : b.questions) {
            if (byHandle.containsKey(q)) {
                throw new TypeSafeException("The same question handle was added twice: " + q);
            }
            String key = b.explicitKeys.get(q);
            if (key == null) {
                key = q.name().orElse(null);
            }
            if (key == null) {
                do {
                    key = "q" + index++;
                } while (byKey.containsKey(key) || b.explicitKeys.containsValue(key));
            }
            if (byKey.put(key, q) != null) {
                throw new TypeSafeException("Two questions share the key \"" + key + "\".");
            }
            byHandle.put(q, key);
        }
        if (byKey.isEmpty()) {
            throw new TypeSafeException("At least one question is required.");
        }
        this.questions = Collections.unmodifiableMap(byKey);
        this.keys = byHandle;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Object state() {
        return state;
    }

    /** Model override, or empty to use the client default. */
    public Optional<String> model() {
        return Optional.ofNullable(model);
    }

    /** Questions by wire key, in request order. */
    public Map<String, Question<?>> questions() {
        return questions;
    }

    /** The wire key assigned to a handle in this request. */
    public String keyOf(Question<?> question) {
        String key = keys.get(question);
        if (key == null) {
            throw new TypeSafeException("Question " + question + " is not part of this request.");
        }
        return key;
    }

    /** Additional top-level body fields. */
    public Map<String, Object> extraBody() {
        return extraBody;
    }

    public RequestOptions options() {
        return options;
    }

    /** JSON body with the model resolved. */
    Map<String, Object> toWire(String defaultModel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", state);
        body.put("model", model == null ? defaultModel : model);
        Map<String, Object> qs = new LinkedHashMap<>();
        questions.forEach((k, q) -> qs.put(k, q.toWire()));
        body.put("questions", qs);
        body.putAll(extraBody);
        return body;
    }

    public static final class Builder {
        private Object state;
        private String model;
        private final List<Question<?>> questions = new ArrayList<>();
        private final Map<Question<?>, String> explicitKeys = new IdentityHashMap<>();
        private final Map<String, Object> extraBody = new LinkedHashMap<>();
        private RequestOptions options = RequestOptions.none();

        private Builder() {}

        /** Text, a JSON-serializable object or list, or {@code null}. */
        public Builder state(Object state) {
            this.state = state;
            return this;
        }

        /** Model override for this request. */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** Adds a question; the key is its {@code name()} or a positional key. */
        public Builder question(Question<?> question) {
            questions.add(question);
            return this;
        }

        /** Adds a question under an explicit wire key. */
        public Builder question(String key, Question<?> question) {
            questions.add(question);
            explicitKeys.put(question, Keys.requireValid(key));
            return this;
        }

        public Builder questions(Question<?>... questions) {
            for (Question<?> q : questions) {
                question(q);
            }
            return this;
        }

        public Builder questions(Map<String, ? extends Question<?>> questions) {
            questions.forEach(this::question);
            return this;
        }

        /** Adds a top-level body field forwarded verbatim. */
        public Builder extraBody(String field, Object value) {
            extraBody.put(field, value);
            return this;
        }

        public Builder options(RequestOptions options) {
            this.options = options == null ? RequestOptions.none() : options;
            return this;
        }

        public SystemOneRequest build() {
            return new SystemOneRequest(this);
        }
    }
}
