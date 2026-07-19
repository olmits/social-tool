package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The {@code app.bsky.feed.post} record embedded in a {@link PostView}. {@code reply} is present
 * only when the post is itself a reply — used to recover the thread root.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostRecord(String text, String createdAt, ReplyRef reply) {
}
