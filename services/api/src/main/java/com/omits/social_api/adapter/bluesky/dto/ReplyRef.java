package com.omits.social_api.adapter.bluesky.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The {@code reply} block of a feed post: the thread {@code root} and the immediate {@code parent}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReplyRef(StrongRef root, StrongRef parent) {
}
