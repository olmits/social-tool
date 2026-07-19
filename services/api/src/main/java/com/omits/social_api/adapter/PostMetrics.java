package com.omits.social_api.adapter;

/**
 * Engagement metrics for a single post, in platform-neutral form.
 */
public record PostMetrics(int likes, int reposts, int replies, int quotes) {
}
