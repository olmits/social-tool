package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A hydrated post as returned by {@code app.bsky.feed.getPosts}. Carries the strong-ref parts
 * ({@code uri}, {@code cid}), the author, the underlying record, and engagement counts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostView(
        String uri,
        String cid,
        PostAuthor author,
        PostRecord record,
        int likeCount,
        int repostCount,
        int replyCount,
        int quoteCount) {
}
