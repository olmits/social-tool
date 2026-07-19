package com.omits.social_api.adapter.bluesky.dto;

/**
 * Body for {@code com.atproto.repo.createRecord}. {@code repo} is the authoring DID,
 * {@code collection} the record namespace (e.g. {@code app.bsky.feed.post}).
 */
public record CreateRecordRequest(String repo, String collection, FeedPost record) {
}
