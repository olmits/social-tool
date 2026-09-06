package com.omits.social_api.signal.model;

/**
 * A content source the trend radar polls.
 *
 * <p>The Go pollers own the fetching; this enum exists so the core API can validate and
 * filter what they send. Adding a source is an entry here plus a {@code Source}
 * implementation in {@code services/workers/internal/source/}.
 *
 * <p>{@link #REDDIT} and {@link #PRODUCT_HUNT} are declared but not yet polled — both need
 * API credentials, and Reddit additionally needs the app approval described in PLAN.md.
 */
public enum SignalSource {
    HACKER_NEWS,
    DEVTO,
    GITHUB_TRENDING,
    REDDIT,
    PRODUCT_HUNT
}
