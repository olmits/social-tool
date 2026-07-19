package com.omits.social_api.adapter.bluesky;

import com.omits.social_api.account.Account;
import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.model.Platform;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlueskyAdapterFactoryTest {

    private MockWebServer server;
    private CredentialStore credentialStore;
    private BlueskyAdapterFactory factory;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        credentialStore = mock(CredentialStore.class);
        WebClient webClient = WebClient.create(server.url("/").toString());
        factory = new BlueskyAdapterFactory(webClient, credentialStore);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Test
    void forAccountResolvesCredentialAndOpensSessionBoundToAccount() throws InterruptedException {
        when(credentialStore.resolve("secret-ref")).thenReturn("app-password");
        Account account = new Account(Platform.BLUESKY, "user.bsky.social", "secret-ref");

        server.enqueue(jsonResponse("""
                {"did":"did:plc:session","accessJwt":"access-jwt","refreshJwt":"refresh-jwt","handle":"user.bsky.social"}"""));

        BlueskyAdapter adapter = factory.forAccount(account);

        RecordedRequest sessionRequest = server.takeRequest();
        assertThat(sessionRequest.getPath()).isEqualTo("/xrpc/com.atproto.server.createSession");
        assertThat(sessionRequest.getBody().readUtf8())
                .contains("\"identifier\":\"user.bsky.social\"")
                .contains("\"password\":\"app-password\"");

        // The returned adapter carries the session did/accessJwt: a subsequent post authors under
        // the session repo and sends the session bearer token.
        server.enqueue(jsonResponse("""
                {"uri":"at://did:plc:session/app.bsky.feed.post/xyz","cid":"bafycid"}"""));

        adapter.post("hello");

        RecordedRequest postRequest = server.takeRequest();
        assertThat(postRequest.getHeader("Authorization")).isEqualTo("Bearer access-jwt");
        assertThat(postRequest.getBody().readUtf8()).contains("\"repo\":\"did:plc:session\"");
    }
}
