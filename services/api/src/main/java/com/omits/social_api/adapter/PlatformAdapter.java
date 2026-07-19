package com.omits.social_api.adapter;

/**
 * Common surface every platform adapter (Bluesky, Mastodon, Reddit, ...) implements.
 * An adapter instance is bound to a single account's credentials — obtain one from the
 * platform's factory rather than constructing it directly.
 *
 * <p>Note: this service does not publish in production (the Go worker does). These methods
 * back Phase 0 validation, reads, and metrics; they must not be wired into an approve → publish
 * flow here.
 */
public interface PlatformAdapter {

    /**
     * Publishes a new top-level post.
     *
     * @param text the post body
     * @return the platform-native identifier of the created post (Bluesky: the {@code at://} URI)
     */
    String post(String text);

    /**
     * Reads a single post by its platform-native identifier.
     */
    RemotePost read(String remoteId);

    /**
     * Publishes a reply to an existing post.
     *
     * @param parentRemoteId the identifier of the post being replied to
     * @param text           the reply body
     * @return the identifier of the created reply
     */
    String reply(String parentRemoteId, String text);

    /**
     * Fetches engagement metrics for a single post.
     */
    PostMetrics fetchMetrics(String remoteId);
}
