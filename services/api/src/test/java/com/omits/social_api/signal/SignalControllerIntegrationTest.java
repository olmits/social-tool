package com.omits.social_api.signal;

import tools.jackson.databind.ObjectMapper;
import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.IngestSignalsResponse;
import com.omits.social_api.signal.dto.SignalResponse;
import com.omits.social_api.signal.model.SignalSource;
import com.omits.social_api.topic.dto.CreateTopicCommand;
import com.omits.social_api.topic.dto.TopicResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalControllerIntegrationTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant FETCHED_AT = Instant.parse("2026-08-22T09:00:00Z");

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

    /** The table is shared across tests in this class, so each one needs its own ids. */
    private static String uniqueId() {
        return "id-" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static IngestSignalsCommand.Item item(SignalSource source, String externalId,
                                                  String title, int score) {
        return item(source, externalId, title, score, null);
    }

    private static IngestSignalsCommand.Item item(SignalSource source, String externalId,
                                                  String title, int score, UUID topicId) {
        var payload = MAPPER.createObjectNode().put("id", externalId).put("native", score);
        return new IngestSignalsCommand.Item(source, externalId, title, topicId,
                "https://example.test/" + externalId, score, score * 10, payload, FETCHED_AT);
    }

    private TopicResponse createTopic(String name, boolean enabled) {
        return client().post().uri("/topics")
                .body(new CreateTopicCommand(name + "-" + uniqueId(), enabled, Map.of()))
                .retrieve().body(TopicResponse.class);
    }

    private IngestSignalsResponse ingest(IngestSignalsCommand.Item... items) {
        return client().post().uri("/signals")
                .body(new IngestSignalsCommand(List.of(items)))
                .retrieve().body(IngestSignalsResponse.class);
    }

    private List<SignalResponse> list(SignalSource source) {
        String uri = source == null ? "/signals" : "/signals?source=" + source;
        return client().get().uri(uri)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    private List<SignalResponse> listByTopic(UUID topicId, SignalSource source) {
        String uri = "/signals?topicId=" + topicId + (source == null ? "" : "&source=" + source);
        return client().get().uri(uri)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    @Test
    void ingestStoresNewSignals() {
        String externalId = uniqueId();

        IngestSignalsResponse response = ingest(
                item(SignalSource.HACKER_NEWS, externalId, "A new thing", 88));

        assertThat(response).isNotNull();
        assertThat(response.received()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isZero();

        SignalResponse stored = list(SignalSource.HACKER_NEWS).stream()
                .filter(s -> s.externalId().equals(externalId))
                .findFirst().orElseThrow();
        assertThat(stored.id()).isNotNull();
        assertThat(stored.title()).isEqualTo("A new thing");
        assertThat(stored.score()).isEqualTo(88);
        assertThat(stored.nativeScore()).isEqualTo(880);
        assertThat(stored.topicId()).isNull();
        assertThat(stored.topicName()).isNull();
        assertThat(stored.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(stored.createdAt()).isNotNull();
        // rawPayload round-trips through the jsonb column intact. Asserted on the parsed
        // value, not the raw text: jsonb normalizes whitespace and does not preserve key
        // order, so the string that comes back is not the string that went in.
        assertThat(MAPPER.readTree(stored.rawPayload()).get("native").asInt()).isEqualTo(88);
    }

    /**
     * The property the whole design rests on: the radar re-polls the same listings every
     * run, so re-posting a batch must refresh rather than duplicate. Without this, a poller
     * restart or a retry after an ambiguous failure would multiply the table.
     */
    @Test
    void reingestingTheSameSignalUpdatesItInPlace() {
        String externalId = uniqueId();

        IngestSignalsResponse first = ingest(
                item(SignalSource.DEVTO, externalId, "Original title", 30));
        assertThat(first.created()).isEqualTo(1);

        UUID idAfterFirst = list(SignalSource.DEVTO).stream()
                .filter(s -> s.externalId().equals(externalId))
                .findFirst().orElseThrow().id();

        IngestSignalsResponse second = ingest(
                item(SignalSource.DEVTO, externalId, "Climbing title", 75));
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);

        List<SignalResponse> matching = list(SignalSource.DEVTO).stream()
                .filter(s -> s.externalId().equals(externalId))
                .toList();
        assertThat(matching).hasSize(1);
        assertThat(matching.getFirst().id()).isEqualTo(idAfterFirst);
        assertThat(matching.getFirst().title()).isEqualTo("Climbing title");
        assertThat(matching.getFirst().score()).isEqualTo(75);
    }

    @Test
    void sameExternalIdOnDifferentSourcesAreDistinctSignals() {
        String externalId = uniqueId();

        IngestSignalsResponse response = ingest(
                item(SignalSource.HACKER_NEWS, externalId, "From HN", 40),
                item(SignalSource.DEVTO, externalId, "From dev.to", 60));

        assertThat(response.created()).isEqualTo(2);
        assertThat(list(SignalSource.HACKER_NEWS)).anyMatch(s -> s.title().equals("From HN"));
        assertThat(list(SignalSource.DEVTO)).anyMatch(s -> s.title().equals("From dev.to"));
    }

    @Test
    void ingestCollapsesRepeatsWithinOneBatch() {
        String externalId = uniqueId();

        IngestSignalsResponse response = ingest(
                item(SignalSource.GITHUB_TRENDING, externalId, "First listing", 20),
                item(SignalSource.GITHUB_TRENDING, externalId, "Second listing", 25));

        assertThat(response.received()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(1);

        List<SignalResponse> matching = list(SignalSource.GITHUB_TRENDING).stream()
                .filter(s -> s.externalId().equals(externalId))
                .toList();
        assertThat(matching).hasSize(1);
        assertThat(matching.getFirst().title()).isEqualTo("Second listing");
    }

    @Test
    void listRanksByScoreDescending() {
        ingest(
                item(SignalSource.REDDIT, uniqueId(), "middle", 50),
                item(SignalSource.REDDIT, uniqueId(), "highest", 99),
                item(SignalSource.REDDIT, uniqueId(), "lowest", 5));

        List<Integer> scores = list(SignalSource.REDDIT).stream().map(SignalResponse::score).toList();

        assertThat(scores).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    @Test
    void ingestRejectsScoreOutsideNormalizedRange() {
        assertThatThrownBy(() -> ingest(item(SignalSource.DEVTO, uniqueId(), "too hot", 140)))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void ingestRejectsBlankTitle() {
        assertThatThrownBy(() -> ingest(item(SignalSource.DEVTO, uniqueId(), "  ", 10)))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- topics ---------------------------------------------------------------

    @Test
    void signalsCarryTheirTopicsNameSoThePanelNeedsNoJoin() {
        TopicResponse topic = createTopic("Local-first", true);
        String externalId = uniqueId();

        ingest(item(SignalSource.HACKER_NEWS, externalId, "A CRDT sync engine", 70, topic.id()));

        SignalResponse stored = listByTopic(topic.id(), null).getFirst();
        assertThat(stored.externalId()).isEqualTo(externalId);
        assertThat(stored.topicId()).isEqualTo(topic.id());
        assertThat(stored.topicName()).isEqualTo(topic.name());
    }

    @Test
    void listFiltersByTopicAndComposesWithTheSourceFilter() {
        TopicResponse topic = createTopic("Runtimes", true);
        TopicResponse other = createTopic("Databases", true);

        ingest(item(SignalSource.DEVTO, uniqueId(), "in topic, devto", 60, topic.id()),
                item(SignalSource.HACKER_NEWS, uniqueId(), "in topic, hn", 65, topic.id()),
                item(SignalSource.DEVTO, uniqueId(), "other topic", 70, other.id()));

        assertThat(listByTopic(topic.id(), null)).hasSize(2);
        assertThat(listByTopic(topic.id(), SignalSource.DEVTO))
                .singleElement()
                .satisfies(s -> assertThat(s.title()).isEqualTo("in topic, devto"));
    }

    @Test
    void ingestRejectsAnUnknownTopic() {
        assertThatThrownBy(() -> ingest(
                item(SignalSource.DEVTO, uniqueId(), "orphan", 10, UUID.randomUUID())))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /**
     * A disabled topic is the user saying "stop collecting this". Storing the batch anyway
     * would leave the panel showing fresh signals for a topic it presents as switched off.
     */
    @Test
    void ingestRejectsADisabledTopic() {
        TopicResponse disabled = createTopic("Paused", false);

        assertThatThrownBy(() -> ingest(
                item(SignalSource.DEVTO, uniqueId(), "unwanted", 10, disabled.id())))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    /** One bad topic rejects the batch rather than storing the part of it that was valid. */
    @Test
    void ingestRejectsTheWholeBatchWhenOneItemNamesABadTopic() {
        TopicResponse topic = createTopic("Observability", true);
        String survivor = uniqueId();

        assertThatThrownBy(() -> ingest(
                item(SignalSource.DEVTO, survivor, "would have been stored", 40, topic.id()),
                item(SignalSource.DEVTO, uniqueId(), "bad topic", 40, UUID.randomUUID())))
                .isInstanceOf(HttpClientErrorException.class);

        assertThat(listByTopic(topic.id(), null)).isEmpty();
    }

    @Test
    void ingestRequiresApiKey() {
        RestClient unauthenticated = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();

        assertThatThrownBy(() -> unauthenticated.post().uri("/signals")
                .body(new IngestSignalsCommand(List.of(item(SignalSource.DEVTO, uniqueId(), "title", 10))))
                .retrieve().body(IngestSignalsResponse.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
