package com.galitianu.jev4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answer to a {@link Choice} question.
 *
 * @param choice        the selected label
 * @param confidence    the API's confidence in the selection, from 0 to 1
 * @param probabilities probability per label, summing to 1, in wire order
 * @param <L>           the label type
 */
public record ChoiceAnswer<L>(L choice, double confidence, Map<L, Double> probabilities) implements Answer {

    public ChoiceAnswer {
        probabilities = Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
    }

    /** Probability of a label, or 0 when the label was not part of the answer. */
    public double probability(L label) {
        return probabilities.getOrDefault(label, 0.0);
    }
}
