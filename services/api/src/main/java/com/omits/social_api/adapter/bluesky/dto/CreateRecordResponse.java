package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRecordResponse(String uri, String cid) {
}
