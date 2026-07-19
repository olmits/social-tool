package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An {@code app.bsky.feed.post} record. {@code reply} is omitted for top-level posts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeedPost(
        @JsonProperty("$type") String type,
        String text,
        String createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReplyRef reply) {

    public static final String TYPE = "app.bsky.feed.post";

    public static FeedPost of(String text, String createdAt, ReplyRef reply) {
        return new FeedPost(TYPE, text, createdAt, reply);
    }
}
