package com.omits.social_api.adapter.bluesky;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.adapter.bluesky.dto.CreateSessionRequest;
import com.omits.social_api.adapter.bluesky.dto.CreateSessionResponse;
import com.omits.social_api.adapter.exception.PlatformApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds a {@link BlueskyAdapter} bound to a specific account: resolves the account's stored app
 * password, opens an authenticated Bluesky session, and hands back an adapter tied to it.
 */
@Component
@RequiredArgsConstructor
public class BlueskyAdapterFactory {

    private static final String CREATE_SESSION_PATH = "/xrpc/com.atproto.server.createSession";

    private final WebClient blueskyWebClient;
    private final CredentialStore credentialStore;

    public BlueskyAdapter forAccount(Account account) {
        String appPassword = credentialStore.resolve(account.getCredentialRef());
        BlueskySession session = createSession(account.getHandle(), appPassword);
        return new BlueskyAdapter(blueskyWebClient, session);
    }

    private BlueskySession createSession(String identifier, String password) {
        CreateSessionResponse response = blueskyWebClient.post()
                .uri(CREATE_SESSION_PATH)
                .bodyValue(new CreateSessionRequest(identifier, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> BlueskyErrors.toException("createSession", resp))
                .bodyToMono(CreateSessionResponse.class)
                .block();
        if (response == null) {
            throw new PlatformApiException("Bluesky createSession returned an empty response");
        }
        return new BlueskySession(response.did(), response.accessJwt(), response.refreshJwt());
    }
}
