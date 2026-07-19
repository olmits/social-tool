package com.omits.social_api.adapter.bluesky.dto;

/**
 * Body for {@code com.atproto.server.createSession}. {@code identifier} is the account handle;
 * {@code password} is the app password.
 */
public record CreateSessionRequest(String identifier, String password) {
}
