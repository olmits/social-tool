package com.omits.social_api.adapter.bluesky;

/**
 * An authenticated Bluesky session, obtained from {@code com.atproto.server.createSession}.
 * {@code did} is the authoring repository; {@code accessJwt} authorizes subsequent XRPC calls.
 */
public record BlueskySession(String did, String accessJwt, String refreshJwt) {
}
