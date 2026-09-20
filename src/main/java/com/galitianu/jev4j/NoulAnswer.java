package com.galitianu.jev4j;

/**
 * Answer to a {@link Noul} question.
 *
 * @param noul probability of "yes", from 0 to 1
 */
public record NoulAnswer(double noul) implements Answer {

    /** Whether the probability of yes is at least {@code threshold}. */
    public boolean isYes(double threshold) {
        return noul >= threshold;
    }
}
