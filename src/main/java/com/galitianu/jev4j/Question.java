package com.galitianu.jev4j;

import java.util.Map;
import java.util.Optional;

/**
 * A typed question handle. Pass handles to {@link TypeSafeClient#systemOne(Object, Question...)}
 * and read the matching answer back with {@link SystemOneResponse#get(Question)}.
 *
 * <p>Handles are immutable. The wire key is either the explicit {@link #name()} or a positional
 * key assigned per request; the API does not use the key for inference.
 *
 * @param <A> the answer type produced by this question
 */
public sealed interface Question<A extends Answer> permits Noul, Choice, Score {

    /** The wire type: {@code noul}, {@code choice}, or {@code score}. */
    String type();

    /** The instructions: a string, a JSON-serializable object or list, or {@code null}. */
    Object instructions();

    /** The explicit wire key, when one was given with {@code named(...)}. */
    Optional<String> name();

    /** Returns a copy of this question with an explicit wire key. */
    Question<A> named(String name);

    /** The JSON body of this question as sent to the API. */
    Map<String, Object> toWire();
}
