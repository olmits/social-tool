package com.omits.social_api.voice;

import com.omits.social_api.voice.dto.CreateVoiceProfileCommand;
import com.omits.social_api.voice.dto.UpdateVoiceProfileCommand;
import com.omits.social_api.voice.dto.VoiceProfileResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VoiceProfileControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";
    private static final String INSTRUCTIONS = "Dry, concrete, first person. No emoji, no hashtags.";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("social-api.security.api-key", () -> API_KEY);
    }

    @LocalServerPort
    private int port;

    @Test
    void createsAProfileAndReadsItBack() {
        String name = uniqueName("Technical");

        VoiceProfileResponse created = create(name, INSTRUCTIONS, null, null);

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);
        assertThat(created.instructions()).isEqualTo(INSTRUCTIONS);
        assertThat(created.enabled()).isTrue();
        assertThat(created.isDefault()).isFalse();

        assertThat(get(created.id()).name()).isEqualTo(name);
    }

    @Test
    void listIncludesACreatedProfile() {
        String name = uniqueName("Analytical");
        create(name, INSTRUCTIONS, null, null);

        assertThat(list()).extracting(VoiceProfileResponse::name).contains(name);
    }

    @Test
    void returns404ForAnUnknownProfile() {
        assertThatThrownBy(() -> get(UUID.randomUUID()))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** 409 rather than 400, so the panel can tell "taken" from "not valid" by status alone. */
    @Test
    void returns409ForADuplicateNameIgnoringCase() {
        String name = uniqueName("Terse");
        create(name, INSTRUCTIONS, null, null);

        assertThatThrownBy(() -> create(name.toUpperCase(), INSTRUCTIONS, null, null))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void returns400ForBlankInstructions() {
        assertThatThrownBy(() -> create(uniqueName("Blank"), "   ", null, null))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchLeavesUnsetFieldsAlone() {
        VoiceProfileResponse created = create(uniqueName("Patchable"), INSTRUCTIONS, null, null);

        VoiceProfileResponse patched = patch(created.id(),
                new UpdateVoiceProfileCommand(null, null, false, null));

        assertThat(patched.enabled()).isFalse();
        assertThat(patched.name()).isEqualTo(created.name());
        assertThat(patched.instructions()).isEqualTo(INSTRUCTIONS);
    }

    /**
     * The real point of this test is the partial unique index: moving the default has to clear
     * the old holder first, or the write is rejected outright. A green assertion here means the
     * clear-then-set ordering in {@code VoiceProfileService} survived the round trip.
     */
    @Test
    void movingTheDefaultLeavesExactlyOneProfileHoldingIt() {
        VoiceProfileResponse first = create(uniqueName("First"), INSTRUCTIONS, null, true);
        assertThat(get(first.id()).isDefault()).isTrue();

        VoiceProfileResponse second = create(uniqueName("Second"), INSTRUCTIONS, null, true);

        assertThat(get(second.id()).isDefault()).isTrue();
        assertThat(get(first.id()).isDefault()).isFalse();
        assertThat(list()).filteredOn(VoiceProfileResponse::isDefault).hasSize(1);
    }

    @Test
    void patchCanMoveTheDefaultOntoAnExistingProfile() {
        create(uniqueName("Holder"), INSTRUCTIONS, null, true);
        VoiceProfileResponse challenger = create(uniqueName("Challenger"), INSTRUCTIONS, null, null);

        VoiceProfileResponse promoted = patch(challenger.id(),
                new UpdateVoiceProfileCommand(null, null, null, true));

        assertThat(promoted.isDefault()).isTrue();
        assertThat(list()).filteredOn(VoiceProfileResponse::isDefault).hasSize(1);
    }

    @Test
    void deleteRemovesTheProfile() {
        VoiceProfileResponse created = create(uniqueName("Doomed"), INSTRUCTIONS, null, null);

        client().delete().uri("/voice-profiles/" + created.id()).retrieve().toBodilessEntity();

        assertThatThrownBy(() -> get(created.id()))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returns404WhenDeletingAProfileThatDoesNotExist() {
        assertThatThrownBy(() -> client().delete().uri("/voice-profiles/" + UUID.randomUUID())
                .retrieve().toBodilessEntity())
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
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

    /** The table is shared across tests in this class, so each one needs its own name. */
    private static String uniqueName(String prefix) {
        return prefix + "-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private VoiceProfileResponse create(String name, String instructions, Boolean enabled,
                                        Boolean isDefault) {
        return client().post().uri("/voice-profiles")
                .body(new CreateVoiceProfileCommand(name, instructions, enabled, isDefault))
                .retrieve().body(VoiceProfileResponse.class);
    }

    private VoiceProfileResponse get(UUID id) {
        return client().get().uri("/voice-profiles/" + id)
                .retrieve().body(VoiceProfileResponse.class);
    }

    private VoiceProfileResponse patch(UUID id, UpdateVoiceProfileCommand command) {
        return client().patch().uri("/voice-profiles/" + id)
                .body(command).retrieve().body(VoiceProfileResponse.class);
    }

    private List<VoiceProfileResponse> list() {
        return client().get().uri("/voice-profiles")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }
}
