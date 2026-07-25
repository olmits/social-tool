package com.omits.social_api.draft.dto;

import com.omits.social_api.account.model.Platform;

import java.util.UUID;

public record CreateDraftCommand(UUID accountId, Platform platform, String content) {
}
