package com.omits.social_api.draft;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.dto.AccountResponse;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.dto.CreateDraftCommand;
import com.omits.social_api.draft.dto.DraftResponse;
import com.omits.social_api.draft.dto.FailDraftCommand;
import com.omits.social_api.draft.dto.PublishDraftCommand;
import com.omits.social_api.draft.dto.ScheduleDraftCommand;
import com.omits.social_api.draft.model.DraftStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DraftControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant SCHEDULED_AT = Instant.parse("2026-08-01T12:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("social-api.security.api-key", () -> API_KEY);
    }

    @MockitoBean
    private CredentialStore credentialStore;

    @LocalServerPort
    private int port;

    @BeforeEach
    void stubCredentialStore() {
        when(credentialStore.store(anyString())).thenReturn("social-api/accounts/ref");
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultHeader("X-API-Key", API_KEY)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }

    private static HttpStatus statusOf(Throwable e) {
        return HttpStatus.valueOf(((HttpClientErrorException) e).getStatusCode().value());
    }

    private UUID createAccount(String handle) {
        AccountResponse account = client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, handle, "app-password", null))
                .retrieve().body(AccountResponse.class);
        assertThat(account).isNotNull();
        return account.id();
    }

    private DraftResponse createDraft(UUID accountId, String content) {
        return client().post().uri("/drafts")
                .body(new CreateDraftCommand(accountId, Platform.BLUESKY, content))
                .retrieve().body(DraftResponse.class);
    }

    @Test
    void createReturnsDraftInDraftStatus() {
        UUID accountId = createAccount("create-user.bsky.social");

        DraftResponse draft = createDraft(accountId, "hello world");

        assertThat(draft).isNotNull();
        assertThat(draft.id()).isNotNull();
        assertThat(draft.accountId()).isEqualTo(accountId);
        assertThat(draft.platform()).isEqualTo(Platform.BLUESKY);
        assertThat(draft.content()).isEqualTo("hello world");
        assertThat(draft.status()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.aiGenerated()).isFalse();
        assertThat(draft.disclosureIncluded()).isFalse();
    }

    @Test
    void lifecycleFromDraftToPublished() {
        UUID accountId = createAccount("publish-user.bsky.social");
        UUID draftId = createDraft(accountId, "post me").id();

        DraftResponse approved = client().patch().uri("/drafts/" + draftId + "/approve")
                .retrieve().body(DraftResponse.class);
        assertThat(approved).isNotNull();
        assertThat(approved.status()).isEqualTo(DraftStatus.APPROVED);

        DraftResponse scheduled = client().patch().uri("/drafts/" + draftId + "/schedule")
                .body(new ScheduleDraftCommand(SCHEDULED_AT))
                .retrieve().body(DraftResponse.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.status()).isEqualTo(DraftStatus.SCHEDULED);
        assertThat(scheduled.scheduledAt()).isEqualTo(SCHEDULED_AT);

        DraftResponse published = client().patch().uri("/drafts/" + draftId + "/published")
                .body(new PublishDraftCommand("at://did:plc:xyz/app.bsky.feed.post/1"))
                .retrieve().body(DraftResponse.class);
        assertThat(published).isNotNull();
        assertThat(published.status()).isEqualTo(DraftStatus.PUBLISHED);
        assertThat(published.remoteId()).isEqualTo("at://did:plc:xyz/app.bsky.feed.post/1");
    }

    @Test
    void lifecycleFromScheduledToFailed() {
        UUID accountId = createAccount("fail-user.bsky.social");
        UUID draftId = createDraft(accountId, "may fail").id();

        client().patch().uri("/drafts/" + draftId + "/approve").retrieve().toBodilessEntity();
        client().patch().uri("/drafts/" + draftId + "/schedule")
                .body(new ScheduleDraftCommand(SCHEDULED_AT)).retrieve().toBodilessEntity();

        DraftResponse failed = client().patch().uri("/drafts/" + draftId + "/failed")
                .body(new FailDraftCommand("rate limited by platform"))
                .retrieve().body(DraftResponse.class);
        assertThat(failed).isNotNull();
        assertThat(failed.status()).isEqualTo(DraftStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("rate limited by platform");
    }

    @Test
    void getReturnsDraftAndUnknownIdReturns404() {
        UUID accountId = createAccount("get-user.bsky.social");
        UUID draftId = createDraft(accountId, "fetch me").id();

        DraftResponse fetched = client().get().uri("/drafts/" + draftId)
                .retrieve().body(DraftResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(draftId);

        assertThatThrownBy(() -> client().get().uri("/drafts/" + UUID.randomUUID())
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void listFiltersByAccountAndStatus() {
        UUID accountId = createAccount("list-user.bsky.social");
        UUID draftId = createDraft(accountId, "stays draft").id();
        UUID approvedId = createDraft(accountId, "gets approved").id();
        client().patch().uri("/drafts/" + approvedId + "/approve").retrieve().toBodilessEntity();

        List<DraftResponse> allForAccount = client().get().uri("/drafts?accountId=" + accountId)
                .retrieve().body(new ParameterizedTypeReference<List<DraftResponse>>() {
                });
        assertThat(allForAccount).extracting(DraftResponse::id).contains(draftId, approvedId);

        List<DraftResponse> approvedForAccount = client()
                .get().uri("/drafts?accountId=" + accountId + "&status=APPROVED")
                .retrieve().body(new ParameterizedTypeReference<List<DraftResponse>>() {
                });
        assertThat(approvedForAccount).extracting(DraftResponse::id).contains(approvedId).doesNotContain(draftId);
    }

    @Test
    void invalidTransitionReturns409() {
        UUID accountId = createAccount("conflict-user.bsky.social");
        UUID draftId = createDraft(accountId, "not approved yet").id();

        // Scheduling requires APPROVED; the draft is still DRAFT.
        assertThatThrownBy(() -> client().patch().uri("/drafts/" + draftId + "/schedule")
                .body(new ScheduleDraftCommand(SCHEDULED_AT))
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void scheduleWithNullScheduledAtReturns400() {
        UUID accountId = createAccount("badreq-user.bsky.social");
        UUID draftId = createDraft(accountId, "approve then bad schedule").id();
        client().patch().uri("/drafts/" + draftId + "/approve").retrieve().toBodilessEntity();

        assertThatThrownBy(() -> client().patch().uri("/drafts/" + draftId + "/schedule")
                .body(new ScheduleDraftCommand(null))
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsRequestWithoutApiKey() {
        RestClient anonymousClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();

        assertThatThrownBy(() -> anonymousClient.get().uri("/drafts")
                .retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
