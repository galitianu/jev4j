package com.galitianu.jev4j;

/** An answer to a {@link Question}. Pattern-match over the permitted records. */
public sealed interface Answer permits NoulAnswer, ChoiceAnswer, ScoreAnswer {}
