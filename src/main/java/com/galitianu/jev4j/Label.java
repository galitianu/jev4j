package com.galitianu.jev4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the wire label of an enum constant used with {@link Choice#of(Object, Class)}.
 * Without it, the label is the constant name in lower case.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Label {
    String value();
}
