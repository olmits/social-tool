package com.omits.social_api.generation;

import com.omits.social_api.account.credential.CredentialStore;
import com.omits.social_api.account.dto.AccountResponse;
import com.omits.social_api.account.dto.ConnectAccountCommand;
import com.omits.social_api.account.model.Platform;
import com.omits.social_api.draft.dto.DraftResponse;
import com.omits.social_api.draft.model.DraftStatus;
import com.omits.social_api.generation.dto.GenerateDraftCommand;
import com.omits.social_api.generation.exception.DraftGenerationException;
import com.omits.social_api.generation.exception.DraftRefusedException;
import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.SignalResponse;
import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.voice.dto.CreateVoiceProfileCommand;
import com.omits.social_api.voice.dto.VoiceProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The end-to-end contract of {@code POST /drafts/generate}, with the model stubbed out.
 *
 * <p>{@code DraftGenerator} is a {@code @MockitoBean} for the same reason {@code CredentialStore}
 * is in {@code DraftControllerIntegrationTest}: without it this test would call the real,
 * paid API on every run. That substitution is the main thing the interface buys.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenerationControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";
    private static final String VOICE = "Dry, concrete, first person. No emoji.";

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
    private DraftGenerator draftGenerator;

    @MockitoBean
    private CredentialStore credentialStore;

    @LocalServerPort
    private int port;

    @BeforeEach
    void stubCredentials() {
        when(credentialStore.store(anyString())).thenReturn("social-api/accounts/ref");
    }

    // --- happy path -----------------------------------------------------------

    @Test
    void returns201WithAnAiGeneratedDraftLinkedToItsSignal() {
        UUID accountId = createAccount("gen-user.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        stubGenerated("a considered take on shipping Go services");

        DraftResponse draft = generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId));

        assertThat(draft).isNotNull();
        assertThat(draft.status()).isEqualTo(DraftStatus.DRAFT);
        assertThat(draft.aiGenerated()).isTrue();
        assertThat(draft.signalId()).isEqualTo(signalId);
        assertThat(draft.content()).isEqualTo("a considered take on shipping Go services");
    }

    /** A generated draft has to be an ordinary draft, or review and approval do not apply. */
    @Test
    void theGeneratedDraftIsReadableThroughTheOrdinaryDraftRoutes() {
        UUID accountId = createAccount("gen-readable.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        stubGenerated("readable");

        DraftResponse created = generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId));

        DraftResponse fetched = client().get().uri("/drafts/" + created.id())
                .retrieve().body(DraftResponse.class);
        assertThat(fetched).isNotNull();
        assertThat(fetched.aiGenerated()).isTrue();
    }

    @Test
    void fallsBackToTheDefaultVoiceWhenNoneIsNamed() {
        UUID accountId = createAccount("gen-default-voice.bsky.social");
        UUID signalId = ingestOneSignal();
        createVoiceProfile(true);
        stubGenerated("used the default voice");

        DraftResponse draft = generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, null));

        assertThat(draft).isNotNull();
        assertThat(draft.content()).isEqualTo("used the default voice");
    }

    // --- failures -------------------------------------------------------------

    @Test
    void returns404ForAnUnknownSignal() {
        UUID accountId = createAccount("gen-no-signal.bsky.social");
        createVoiceProfile(true);
        stubGenerated("never reached");

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, UUID.randomUUID(), Platform.BLUESKY, null)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returns404ForAnUnknownVoiceProfile() {
        UUID accountId = createAccount("gen-no-voice.bsky.social");
        UUID signalId = ingestOneSignal();
        stubGenerated("never reached");

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, UUID.randomUUID())))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returns409ForADisabledVoiceProfile() {
        UUID accountId = createAccount("gen-disabled-voice.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(false).id();
        stubGenerated("never reached");

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void returns409ForADisconnectedAccount() {
        UUID accountId = createAccount("gen-disconnected.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        client().delete().uri("/accounts/" + accountId).retrieve().toBodilessEntity();
        stubGenerated("never reached");

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void returns409WhenTheAccountIsOnADifferentPlatform() {
        UUID accountId = createAccount("gen-mismatch.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        stubGenerated("never reached");

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.MASTODON, voiceId)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    /** Upstream failed; this service did not. */
    @Test
    void returns502WhenGenerationFails() {
        UUID accountId = createAccount("gen-upstream-down.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        when(draftGenerator.generate(any()))
                .thenThrow(new DraftGenerationException("Claude draft generation failed (500)"));

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId)))
                .isInstanceOf(HttpServerErrorException.class)
                .satisfies(e -> assertThat(serverStatusOf(e)).isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(draftsFor(accountId)).isEmpty();
    }

    /** A refusal is a successful 200 with nothing usable in it — 422, not 502. */
    @Test
    void returns422WhenTheModelRefuses() {
        UUID accountId = createAccount("gen-refused.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        when(draftGenerator.generate(any())).thenThrow(new DraftRefusedException("cyber"));

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId)))
                .satisfies(e -> assertThat(statusOf(e))
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    /** A failed generation must not leave a half-made row behind. */
    @Test
    void persistsNothingWhenGenerationFails() {
        UUID accountId = createAccount("gen-no-orphan.bsky.social");
        UUID signalId = ingestOneSignal();
        UUID voiceId = createVoiceProfile(true).id();
        when(draftGenerator.generate(any()))
                .thenThrow(new DraftGenerationException("Claude draft generation failed"));

        assertThatThrownBy(() -> generate(new GenerateDraftCommand(
                accountId, signalId, Platform.BLUESKY, voiceId)));

        assertThat(draftsFor(accountId)).isEmpty();
    }

    @Test
    void requiresAuthentication() {
        RestClient unauthenticated = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();

        assertThatThrownBy(() -> unauthenticated.post().uri("/drafts/generate")
                .body(new GenerateDraftCommand(UUID.randomUUID(), UUID.randomUUID(),
                        Platform.BLUESKY, null))
                .retrieve().toBodilessEntity())
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // --- helpers --------------------------------------------------------------

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

    private static HttpStatus serverStatusOf(Throwable e) {
        return HttpStatus.valueOf(((HttpServerErrorException) e).getStatusCode().value());
    }

    private static String unique(String prefix) {
        return prefix + "-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private void stubGenerated(String content) {
        when(draftGenerator.generate(any())).thenReturn(new GeneratedDraft(content, "an angle"));
    }

    private DraftResponse generate(GenerateDraftCommand command) {
        return client().post().uri("/drafts/generate").body(command)
                .retrieve().body(DraftResponse.class);
    }

    private UUID createAccount(String handle) {
        AccountResponse account = client().post().uri("/accounts")
                .body(new ConnectAccountCommand(Platform.BLUESKY, handle, "app-password", null))
                .retrieve().body(AccountResponse.class);
        assertThat(account).isNotNull();
        return account.id();
    }

    private VoiceProfileResponse createVoiceProfile(boolean enabled) {
        VoiceProfileResponse profile = client().post().uri("/voice-profiles")
                .body(new CreateVoiceProfileCommand(unique("Technical"), VOICE, enabled, enabled))
                .retrieve().body(VoiceProfileResponse.class);
        assertThat(profile).isNotNull();
        return profile;
    }

    private UUID ingestOneSignal() {
        String externalId = unique("sig");
        var payload = new ObjectMapper().createObjectNode().put("id", externalId);
        client().post().uri("/signals")
                .body(new IngestSignalsCommand(List.of(new IngestSignalsCommand.Item(
                        SignalSource.HACKER_NEWS, externalId, "Shipping Go services", null,
                        "https://example.test/" + externalId, 90, 312, payload, Instant.now()))))
                .retrieve().toBodilessEntity();

        List<SignalResponse> signals = client().get().uri("/signals?source=HACKER_NEWS")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        assertThat(signals).isNotNull();
        return signals.stream()
                .filter(signal -> signal.externalId().equals(externalId))
                .findFirst().orElseThrow().id();
    }

    private List<DraftResponse> draftsFor(UUID accountId) {
        return client().get().uri("/drafts?accountId=" + accountId)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }
}
