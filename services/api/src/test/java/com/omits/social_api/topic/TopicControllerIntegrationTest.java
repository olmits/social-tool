package com.omits.social_api.topic;

import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.dto.CreateTopicCommand;
import com.omits.social_api.topic.dto.TopicQueriesResponse;
import com.omits.social_api.topic.dto.TopicResponse;
import com.omits.social_api.topic.dto.UpdateTopicCommand;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TopicControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";

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

    private TopicResponse create(String name, boolean enabled, Map<SignalSource, String> queries) {
        return client().post().uri("/topics")
                .body(new CreateTopicCommand(name, enabled, queries))
                .retrieve().body(TopicResponse.class);
    }

    private TopicResponse get(UUID id) {
        return client().get().uri("/topics/" + id).retrieve().body(TopicResponse.class);
    }

    private List<TopicQueriesResponse> workList() {
        return client().get().uri("/topics/queries")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void createsATopicWithItsQueries() {
        String name = uniqueName("Local-first");

        TopicResponse created = create(name, true, Map.of(
                SignalSource.DEVTO, "local-first",
                SignalSource.HACKER_NEWS, "crdt"));

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo(name);
        assertThat(created.enabled()).isTrue();
        assertThat(created.queries())
                .containsEntry(SignalSource.DEVTO, "local-first")
                .containsEntry(SignalSource.HACKER_NEWS, "crdt");
        assertThat(created.createdAt()).isNotNull();

        assertThat(get(created.id()).queries()).isEqualTo(created.queries());
    }

    @Test
    void rejectsADuplicateNameWithAConflict() {
        String name = uniqueName("Runtimes");
        create(name, true, Map.of());

        // Case-insensitively: "Runtimes" and "runtimes" are one topic to the reader.
        assertThatThrownBy(() -> create(name.toUpperCase(), true, Map.of()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsABlankNameWithABadRequest() {
        assertThatThrownBy(() -> create("   ", true, Map.of()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void patchesOnlyTheFieldsItIsGiven() {
        TopicResponse created = create(uniqueName("Databases"), true, Map.of(SignalSource.DEVTO, "postgres"));

        TopicResponse patched = client().patch().uri("/topics/" + created.id())
                .body(new UpdateTopicCommand(null, false, null))
                .retrieve().body(TopicResponse.class);

        assertThat(patched.enabled()).isFalse();
        assertThat(patched.name()).isEqualTo(created.name());
        assertThat(patched.queries()).containsEntry(SignalSource.DEVTO, "postgres");
    }

    @Test
    void replacesQueriesWhenTheyArePresentInThePatch() {
        TopicResponse created = create(uniqueName("Observability"), true, Map.of(
                SignalSource.DEVTO, "otel", SignalSource.HACKER_NEWS, "tracing"));

        TopicResponse patched = client().patch().uri("/topics/" + created.id())
                .body(new UpdateTopicCommand(null, null, Map.of(SignalSource.DEVTO, "opentelemetry")))
                .retrieve().body(TopicResponse.class);

        assertThat(patched.queries()).containsExactly(Map.entry(SignalSource.DEVTO, "opentelemetry"));
    }

    @Test
    void deletesATopicAndItsQueries() {
        TopicResponse created = create(uniqueName("Temporary"), true, Map.of(SignalSource.DEVTO, "x"));

        client().delete().uri("/topics/" + created.id()).retrieve().toBodilessEntity();

        assertThatThrownBy(() -> get(created.id()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void returnsNotFoundForAnUnknownTopic() {
        assertThatThrownBy(() -> get(UUID.randomUUID()))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * The poller's whole work list in one call — this is why {@code /topics/queries} exists
     * rather than the worker filtering {@code GET /topics} itself.
     */
    @Test
    void workListCarriesOnlyEnabledTopics() {
        TopicResponse on = create(uniqueName("Polled"), true, Map.of(SignalSource.GITHUB_TRENDING, "topic:llm"));
        TopicResponse off = create(uniqueName("Paused"), false, Map.of(SignalSource.DEVTO, "paused"));

        List<TopicQueriesResponse> work = workList();

        assertThat(work).anySatisfy(entry -> {
            assertThat(entry.topicId()).isEqualTo(on.id());
            assertThat(entry.queries()).containsEntry(SignalSource.GITHUB_TRENDING, "topic:llm");
        });
        assertThat(work).noneMatch(entry -> entry.topicId().equals(off.id()));
    }

    /** "queries" must not be parsed as a topic id by the {@code /{id}} route. */
    @Test
    void workListPathDoesNotCollideWithTheIdRoute() {
        assertThat(workList()).isNotNull();
    }

    @Test
    void requiresApiKey() {
        RestClient unauthenticated = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();

        assertThatThrownBy(() -> unauthenticated.get().uri("/topics").retrieve().toBodilessEntity())
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
