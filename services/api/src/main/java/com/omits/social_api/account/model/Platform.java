package com.omits.social_api.account.model;

/**
 * A social platform an account can post to.
 *
 * <p>{@link #maxPostLength()} lives here rather than in the generation slice because it is a
 * property of the platform, not of how a post was written — the drafting prompt needs it, the
 * panel's character counter needs it, and the Go publisher will need it too. Keeping one
 * answer in one place is what stops those three drifting apart.
 */
public enum Platform {

    /**
     * 300 graphemes, not characters. Bluesky counts grapheme clusters, so an emoji is one and
     * a combining sequence is one — see {@code generation.PostLength}, which is why no caller
     * should measure a post with {@code String.length()}.
     */
    BLUESKY(300),

    MASTODON(500),

    /**
     * Reddit's self-post body limit. Far enough above the others that it effectively never
     * binds, which is itself worth knowing when reading a prompt that quotes it.
     */
    REDDIT(40_000);

    private final int maxPostLength;

    Platform(int maxPostLength) {
        this.maxPostLength = maxPostLength;
    }

    /** The longest post this platform will accept, measured in graphemes. */
    public int maxPostLength() {
        return maxPostLength;
    }
}
