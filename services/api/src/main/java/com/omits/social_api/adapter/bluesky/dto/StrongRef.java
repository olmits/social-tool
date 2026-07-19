package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An AT Protocol strong reference: a record's {@code uri} paired with its content hash {@code cid}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrongRef(String uri, String cid) {
}
