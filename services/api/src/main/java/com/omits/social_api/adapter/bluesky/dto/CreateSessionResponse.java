package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSessionResponse(String did, String accessJwt, String refreshJwt, String handle) {
}
