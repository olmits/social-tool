package com.omits.social_api.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reason this class exists at all: {@code String.length()} is wrong in both directions,
 * and at Bluesky's 300 limit that is the difference between a draft that publishes and one the
 * Go worker rejects.
 */
class PostLengthTest {

    @Test
    void countsPlainTextTheObviousWay() {
        assertThat(PostLength.graphemes("hello")).isEqualTo(5);
    }

    @Test
    void countsNothingForNullOrEmpty() {
        assertThat(PostLength.graphemes(null)).isZero();
        assertThat(PostLength.graphemes("")).isZero();
    }

    /** Outside the BMP, so {@code String.length()} says 2. */
    @Test
    void countsAnEmojiAsOneGrapheme() {
        String rocket = "🚀";

        assertThat(rocket.length()).isEqualTo(2);
        assertThat(PostLength.graphemes(rocket)).isEqualTo(1);
    }

    /** "e" plus a combining acute: one character to a reader, two to {@code length()}. */
    @Test
    void countsACombiningSequenceAsOneGrapheme() {
        String combined = "é";

        assertThat(combined.length()).isEqualTo(2);
        assertThat(PostLength.graphemes(combined)).isEqualTo(1);
    }

    /** A ZWJ family emoji is several code points and still one thing on screen. */
    @Test
    void countsAZeroWidthJoinerSequenceAsOneGrapheme() {
        String family = "👩‍💻";

        assertThat(PostLength.graphemes(family)).isEqualTo(1);
    }
}
