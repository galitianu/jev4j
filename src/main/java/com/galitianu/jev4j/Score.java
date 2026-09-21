package com.galitianu.jev4j;

import com.galitianu.jev4j.errors.TypeSafeException;
import com.galitianu.jev4j.internal.Keys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A question that rates the state on an ordered rubric. Level {@code i} is described by
 * {@code levels.get(i)}. The answer's {@link ScoreAnswer#score()} is probability-weighted and may
 * fall between levels.
 */
public final class Score implements Question<ScoreAnswer> {

    /** The API requires at least this many levels. */
    public static final int MIN_LEVELS = 2;
    /** The API accepts at most this many levels. */
    public static final int MAX_LEVELS = 10;

    private final Object instructions;
    private final List<Object> levels;
    private final String name;

    private Score(Object instructions, List<Object> levels, String name) {
        this.instructions = instructions;
        this.levels = levels;
        this.name = name;
    }

    /** A score over string level descriptions, indexed from zero. */
    public static Score of(Object instructions, String... levels) {
        if (levels == null) {
            throw new TypeSafeException("Score levels must not be null.");
        }
        return of(instructions, Arrays.asList(levels));
    }

    /**
     * A score over level descriptions, indexed from zero. Each description may be a string, a
     * JSON-serializable object, or {@code null}.
     */
    public static Score of(Object instructions, List<?> levels) {
        if (levels == null || levels.size() < MIN_LEVELS) {
            throw new TypeSafeException(
                "Score question has " + (levels == null ? 0 : levels.size()) + " levels; at least " + MIN_LEVELS + " are required.");
        }
        if (levels.size() > MAX_LEVELS) {
            throw new TypeSafeException(
                "Score question has " + levels.size() + " levels; the maximum is " + MAX_LEVELS + ".");
        }
        return new Score(instructions, Collections.unmodifiableList(new ArrayList<>(levels)), null);
    }

    @Override
    public String type() {
        return "score";
    }

    @Override
    public Object instructions() {
        return instructions;
    }

    /** Level descriptions, indexed from zero. */
    public List<Object> levels() {
        return levels;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Score named(String name) {
        return new Score(instructions, levels, Keys.requireValid(name));
    }

    @Override
    public Map<String, Object> toWire() {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("type", "score");
        wire.put("instructions", instructions);
        wire.put("criteria", levels);
        return wire;
    }

    @Override
    public String toString() {
        return "Score" + (name == null ? "" : "[" + name + "]") + "(" + instructions + ", " + levels.size() + " levels)";
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
