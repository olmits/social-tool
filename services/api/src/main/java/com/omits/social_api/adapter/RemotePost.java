package com.omits.social_api.adapter;

import java.time.Instant;

/**
 * A post read back from a platform, in platform-neutral form.
 */
public record RemotePost(String remoteId, String authorHandle, String text, Instant createdAt) {
}
