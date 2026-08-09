package com.omits.social_api.draft.model;

public enum DraftStatus {
    DRAFT,
    APPROVED,
    SCHEDULED,
    PUBLISHED,
    FAILED,
    /** Terminal: the draft was rejected during review and will never be published. */
    DISCARDED
}
