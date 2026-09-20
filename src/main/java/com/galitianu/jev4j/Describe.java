package com.galitianu.jev4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes an enum constant used as a choice label. The description is sent as the label's
 * criteria text. Constants without it are sent undescribed.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Describe {
    /**
     * The criteria text for the annotated constant.
     *
     * @return the description sent to the API as the label's criteria
     */
    String value();
}
