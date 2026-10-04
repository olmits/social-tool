package com.omits.social_api.generation;

import java.text.BreakIterator;

/**
 * Measures a post the way the platforms do: in grapheme clusters, not {@code char}s.
 *
 * <p>{@code String.length()} counts UTF-16 code units, which is wrong in both directions — an
 * emoji outside the BMP counts as two, while "e" followed by a combining acute counts as two
 * where a reader sees one. Bluesky counts graphemes, and at a 300 limit that difference
 * decides whether an approved draft can actually be published or bounces at the publisher.
 */
public final class PostLength {

    private PostLength() {
    }

    /** The number of grapheme clusters in {@code text}; 0 for null. */
    public static int graphemes(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);
        int count = 0;
        while (it.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }
}
