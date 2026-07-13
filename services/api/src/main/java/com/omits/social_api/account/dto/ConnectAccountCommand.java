package com.omits.social_api.account.dto;

import com.omits.social_api.account.model.Platform;

public record ConnectAccountCommand(Platform platform, String handle, String credentialValue, String instance) {
}
