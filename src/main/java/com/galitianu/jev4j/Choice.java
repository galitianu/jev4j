package com.galitianu.jev4j;

import com.galitianu.jev4j.errors.TypeSafeException;
import com.galitianu.jev4j.internal.Keys;
import com.galitianu.jev4j.internal.Labels;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A question that selects one label from a fixed set.
 *
 * <p>Labels can be plain strings or the constants of an enum. With an enum, the wire label is the
 * constant name in lower case unless overridden with {@link Label}, and the description comes from
 * {@link Describe}. The answer's {@link ChoiceAnswer#choice()} is then the enum constant.
 *
 * @param <L> the label type: an enum, or {@link String}
 */
public final class Choice<L> implements Question<ChoiceAnswer<L>> {

    /** The API accepts at most this many options. */
    public static final int MAX_OPTIONS = 255;

    private final Object instructions;
    private final Map<String, Object> criteria;
    private final Function<String, L> decoder;
    private final String name;

    private Choice(Object instructions, Map<String, Object> criteria, Function<String, L> decoder, String name) {
        this.instructions = instructions;
        this.criteria = criteria;
        this.decoder = decoder;
        this.name = name;
    }

    /** A choice between the constants of an enum. */
    public static <E extends Enum<E>> Choice<E> of(Object instructions, Class<E> labels) {
        Labels<E> mapping = Labels.of(labels);
        return new Choice<>(instructions, mapping.criteria(), mapping::decode, null);
    }

    /**
     * A choice between string labels with descriptions. A {@code null} description leaves the
     * label undescribed. Iteration order of the map is preserved on the wire.
     */
    public static Choice<String> of(Object instructions, Map<String, ?> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new TypeSafeException("Choice criteria must contain at least one label.");
        }
        Map<String, Object> copy = new LinkedHashMap<>(criteria);
        return new Choice<>(instructions, Collections.unmodifiableMap(copy), Choice::identity, null);
    }

    /** A choice between undescribed string labels. */
    public static Choice<String> of(Object instructions, String... labels) {
        if (labels == null || labels.length == 0) {
            throw new TypeSafeException("Choice criteria must contain at least one label.");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String label : labels) {
            if (copy.containsKey(label)) {
                throw new TypeSafeException("Duplicate choice label: " + label);
            }
            copy.put(label, null);
        }
        return new Choice<>(instructions, Collections.unmodifiableMap(copy), Choice::identity, null);
    }

    private static String identity(String s) {
        return s;
    }

    @Override
    public String type() {
        return "choice";
    }

    @Override
    public Object instructions() {
        return instructions;
    }

    /** Wire labels mapped to their descriptions, in wire order. */
    public Map<String, Object> criteria() {
        return criteria;
    }

    /** Converts a wire label back to {@code L}. Throws when the label is unknown. */
    public L decodeLabel(String label) {
        return decoder.apply(label);
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Choice<L> named(String name) {
        return new Choice<>(instructions, criteria, decoder, Keys.requireValid(name));
    }

    @Override
    public Map<String, Object> toWire() {
        if (criteria.size() > MAX_OPTIONS) {
            throw new TypeSafeException(
                "Choice question has " + criteria.size() + " options; the maximum is " + MAX_OPTIONS + ".");
        }
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("type", "choice");
        wire.put("instructions", instructions);
        wire.put("criteria", criteria);
        return wire;
    }

    @Override
    public String toString() {
        return "Choice" + (name == null ? "" : "[" + name + "]") + "(" + instructions + ", " + criteria.keySet() + ")";
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
