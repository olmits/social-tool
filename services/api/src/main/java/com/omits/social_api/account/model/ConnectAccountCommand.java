package com.omits.social_api.account.model;

public record ConnectAccountCommand(Platform platform, String handle, String credentialValue, String instance) {
}
