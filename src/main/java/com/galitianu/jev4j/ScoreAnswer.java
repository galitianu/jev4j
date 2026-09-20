package com.galitianu.jev4j;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Answer to a {@link Score} question.
 *
 * @param score         probability-weighted score; may fall between levels
 * @param confidence    the API's confidence in the score, from 0 to 1
 * @param legend        level index to level description
 * @param probabilities probability per level index, summing to 1
 */
public record ScoreAnswer(double score, double confidence, Map<Integer, Object> legend, Map<Integer, Double> probabilities)
    implements Answer {

    public ScoreAnswer {
        legend = Collections.unmodifiableMap(new TreeMap<>(legend));
        probabilities = Collections.unmodifiableMap(new TreeMap<>(probabilities));
    }

    /** The level closest to {@link #score()}. */
    public int nearestLevel() {
        int level = (int) Math.round(score);
        int max = legend.isEmpty() ? level : legend.keySet().stream().max(Integer::compare).orElse(level);
        return Math.max(0, Math.min(level, max));
    }

    /** The most probable level. */
    public int topLevel() {
        return probabilities.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElseGet(this::nearestLevel);
    }

    /** Description of {@link #nearestLevel()}, or {@code null} when undescribed. */
    public Object nearestDescription() {
        return legend.get(nearestLevel());
    }
}
