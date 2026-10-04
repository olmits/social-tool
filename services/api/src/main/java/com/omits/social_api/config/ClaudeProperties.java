package com.omits.social_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param apiKey       the Anthropic API key. May be blank — see {@code application.yml} for
 *                     why it has an empty default rather than being required at startup.
 *                     {@code ClaudeDraftGenerator} rejects a blank key at call time.
 * @param model        configurable so the model can be changed, and its cost and quality
 *                     compared, without a rebuild
 * @param fetchArticle whether to give the model the web-fetch tool so it can read the article
 *                     behind a signal's URL rather than drafting from the headline alone. On
 *                     by default: a post written from a headline is generic enough that the
 *                     author could write it faster themselves. Off is the escape hatch if the
 *                     added latency stops being worth it.
 * @param timeoutSeconds per-request timeout. The SDK's own default scales to ten minutes,
 *                     which is strictly worse than nothing on a synchronous POST that a
 *                     browser is waiting on.
 */
@ConfigurationProperties(prefix = "social-api.claude")
public record ClaudeProperties(String apiKey, String model, boolean fetchArticle,
                               int timeoutSeconds) {
}
