package com.galitianu.jev4j;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Maps enum constants to wire labels and back. */
final class Labels<E extends Enum<E>> {

    private final Class<E> type;
    private final Map<String, Object> criteria;
    private final Map<String, E> byLabel;

    private Labels(Class<E> type, Map<String, Object> criteria, Map<String, E> byLabel) {
        this.type = type;
        this.criteria = criteria;
        this.byLabel = byLabel;
    }

    static <E extends Enum<E>> Labels<E> of(Class<E> type) {
        E[] constants = type.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new TypeSafeException("Enum " + type.getName() + " has no constants to use as choice labels.");
        }
        Map<String, Object> criteria = new LinkedHashMap<>();
        Map<String, E> byLabel = new HashMap<>();
        for (E constant : constants) {
            Field field;
            try {
                field = type.getField(constant.name());
            } catch (NoSuchFieldException e) {
                throw new TypeSafeException("Cannot reflect enum constant " + constant, e);
            }
            Label label = field.getAnnotation(Label.class);
            Describe describe = field.getAnnotation(Describe.class);
            String wire = label == null ? constant.name().toLowerCase(Locale.ROOT) : label.value();
            if (byLabel.put(wire, constant) != null) {
                throw new TypeSafeException("Enum " + type.getName() + " maps two constants to the label \"" + wire + "\".");
            }
            criteria.put(wire, describe == null ? null : describe.value());
        }
        return new Labels<>(type, Collections.unmodifiableMap(criteria), byLabel);
    }

    Map<String, Object> criteria() {
        return criteria;
    }

    E decode(String label) {
        E constant = byLabel.get(label);
        if (constant == null) {
            throw new TypeSafeException("The API returned label \"" + label + "\" which is not a constant of " + type.getName() + ".");
        }
        return constant;
    }
}
