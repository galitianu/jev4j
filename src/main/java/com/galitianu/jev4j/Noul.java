package com.galitianu.jev4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A yes/no question. The answer is the probability of "yes", from 0 to 1. */
public final class Noul implements Question<NoulAnswer> {

    private final Object instructions;
    private final Object yes;
    private final Object no;
    private final boolean hasCriteria;
    private final String name;

    private Noul(Object instructions, Object yes, Object no, boolean hasCriteria, String name) {
        this.instructions = instructions;
        this.yes = yes;
        this.no = no;
        this.hasCriteria = hasCriteria;
        this.name = name;
    }

    /** A yes/no question with no descriptions of the outcomes. */
    public static Noul of(Object instructions) {
        return new Noul(instructions, null, null, false, null);
    }

    /**
     * A yes/no question with descriptions of what a yes and a no mean.
     * Either description may be a string, a JSON-serializable object, or {@code null}.
     */
    public static Noul of(Object instructions, Object yes, Object no) {
        return new Noul(instructions, yes, no, true, null);
    }

    @Override
    public String type() {
        return "noul";
    }

    @Override
    public Object instructions() {
        return instructions;
    }

    /** Description of the yes outcome, if any. */
    public Object yes() {
        return yes;
    }

    /** Description of the no outcome, if any. */
    public Object no() {
        return no;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Noul named(String name) {
        return new Noul(instructions, yes, no, hasCriteria, Keys.requireValid(name));
    }

    @Override
    public Map<String, Object> toWire() {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("type", "noul");
        wire.put("instructions", instructions);
        if (hasCriteria) {
            Map<String, Object> criteria = new LinkedHashMap<>();
            criteria.put("true", yes);
            criteria.put("false", no);
            wire.put("criteria", criteria);
        }
        return wire;
    }

    @Override
    public String toString() {
        return "Noul" + (name == null ? "" : "[" + name + "]") + "(" + instructions + ")";
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
