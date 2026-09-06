package com.omits.social_api.signal;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.omits.social_api.signal.dto.IngestSignalsCommand;
import com.omits.social_api.signal.dto.IngestSignalsResponse;
import com.omits.social_api.signal.model.SignalSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalServiceTest {

    private static final Instant FETCHED_AT = Instant.parse("2026-08-22T09:00:00Z");

    private final SignalRepository signalRepository = mock(SignalRepository.class);
    private final SignalService signalService = new SignalService(signalRepository);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parses a payload literal the way Jackson would when the poller posts it. */
    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("test fixture is not valid JSON: " + raw, e);
        }
    }

    private static IngestSignalsCommand.Item item(SignalSource source, String externalId) {
        return new IngestSignalsCommand.Item(source, externalId, "topic " + externalId,
                "https://example.test/" + externalId, 50, json("{\"id\":\"" + externalId + "\"}"), FETCHED_AT);
    }

    private void stubNothingStored() {
        when(signalRepository.findBySourceAndExternalIdIn(any(), anyCollection())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private List<Signal> captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(signalRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // --- ingest ---------------------------------------------------------------

    @Test
    void storesFirstSightingsAsNewSignals() {
        stubNothingStored();

        IngestSignalsResponse response = signalService.ingest(new IngestSignalsCommand(List.of(
                item(SignalSource.HACKER_NEWS, "1"),
                item(SignalSource.DEVTO, "2"))));

        assertThat(response.received()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(2);
        assertThat(response.updated()).isZero();
        assertThat(captureSaved()).hasSize(2);
    }

    @Test
    void refreshesAlreadyStoredSignalsInsteadOfDuplicating() {
        Signal stored = new Signal(SignalSource.HACKER_NEWS, "1", "old topic",
                "https://example.test/old", 10, "{\"id\":\"1\"}", FETCHED_AT.minusSeconds(3600));
        when(signalRepository.findBySourceAndExternalIdIn(any(), anyCollection())).thenReturn(List.of(stored));

        IngestSignalsCommand.Item incoming = new IngestSignalsCommand.Item(
                SignalSource.HACKER_NEWS, "1", "new topic", "https://example.test/new", 90,
                json("{\"id\":\"1\",\"score\":900}"), FETCHED_AT);

        IngestSignalsResponse response = signalService.ingest(new IngestSignalsCommand(List.of(incoming)));

        assertThat(response.created()).isZero();
        assertThat(response.updated()).isEqualTo(1);

        // The same row, with its mutable fields refreshed — an item's score and headline
        // both move as it rises through a listing.
        List<Signal> saved = captureSaved();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst()).isSameAs(stored);
        assertThat(stored.getTopic()).isEqualTo("new topic");
        assertThat(stored.getUrl()).isEqualTo("https://example.test/new");
        assertThat(stored.getScore()).isEqualTo(90);
        assertThat(stored.getFetchedAt()).isEqualTo(FETCHED_AT);
    }

    @Test
    void treatsSameExternalIdOnDifferentSourcesAsDistinctSignals() {
        // Identity is (source, externalId): dev.to article 1 and HN item 1 are unrelated.
        stubNothingStored();

        IngestSignalsResponse response = signalService.ingest(new IngestSignalsCommand(List.of(
                item(SignalSource.HACKER_NEWS, "1"),
                item(SignalSource.DEVTO, "1"))));

        assertThat(response.created()).isEqualTo(2);
        assertThat(captureSaved()).hasSize(2);
    }

    @Test
    void collapsesRepeatsWithinOneBatch() {
        // A source listing the same item twice in one response must not reach the unique
        // index as two inserts.
        stubNothingStored();

        IngestSignalsCommand.Item first = item(SignalSource.DEVTO, "7");
        IngestSignalsCommand.Item duplicate = new IngestSignalsCommand.Item(
                SignalSource.DEVTO, "7", "winning topic", "https://example.test/7", 77,
                json("{\"id\":\"7\"}"), FETCHED_AT);

        IngestSignalsResponse response = signalService.ingest(
                new IngestSignalsCommand(List.of(first, duplicate)));

        assertThat(response.received()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(1);

        List<Signal> saved = captureSaved();
        assertThat(saved).hasSize(1);
        // Last occurrence wins.
        assertThat(saved.getFirst().getTopic()).isEqualTo("winning topic");
    }

    @Test
    void acceptsAnEmptyBatch() {
        IngestSignalsResponse response = signalService.ingest(new IngestSignalsCommand(List.of()));

        assertThat(response.received()).isZero();
        assertThat(response.created()).isZero();
        assertThat(response.updated()).isZero();
    }

    // --- validation -----------------------------------------------------------

    @Test
    void rejectsNullCommand() {
        assertThatThrownBy(() -> signalService.ingest(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(signalRepository, never()).saveAll(any());
    }

    @Test
    void rejectsNullSignalList() {
        assertThatThrownBy(() -> signalService.ingest(new IngestSignalsCommand(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(signalRepository, never()).saveAll(any());
    }

    @Test
    void rejectsBatchesOverTheLimit() {
        List<IngestSignalsCommand.Item> oversized = IntStream.range(0, SignalService.MAX_BATCH_SIZE + 1)
                .mapToObj(i -> item(SignalSource.DEVTO, String.valueOf(i)))
                .toList();

        assertThatThrownBy(() -> signalService.ingest(new IngestSignalsCommand(oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum");
        verify(signalRepository, never()).saveAll(any());
    }

    @Test
    void rejectsScoresOutsideTheNormalizedRange() {
        // The poller normalizes to 0-100; anything else means a broken poller, and storing
        // it would corrupt the cross-source ranking the score exists for.
        for (int score : new int[]{-1, 101}) {
            IngestSignalsCommand.Item bad = new IngestSignalsCommand.Item(
                    SignalSource.DEVTO, "1", "topic", "https://example.test/1", score,
                    json("{\"id\":1}"), FETCHED_AT);

            assertThatThrownBy(() -> signalService.ingest(new IngestSignalsCommand(List.of(bad))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("score");
        }
        verify(signalRepository, never()).saveAll(any());
    }

    @Test
    void rejectsIncompleteItems() {
        record Case(String name, IngestSignalsCommand.Item item) {
        }

        List<Case> cases = List.of(
                new Case("null source", new IngestSignalsCommand.Item(
                        null, "1", "topic", "https://example.test/1", 10, json("{}"), FETCHED_AT)),
                new Case("blank externalId", new IngestSignalsCommand.Item(
                        SignalSource.DEVTO, " ", "topic", "https://example.test/1", 10, json("{}"), FETCHED_AT)),
                new Case("blank topic", new IngestSignalsCommand.Item(
                        SignalSource.DEVTO, "1", " ", "https://example.test/1", 10, json("{}"), FETCHED_AT)),
                new Case("blank url", new IngestSignalsCommand.Item(
                        SignalSource.DEVTO, "1", "topic", " ", 10, json("{}"), FETCHED_AT)),
                new Case("null rawPayload", new IngestSignalsCommand.Item(
                        SignalSource.DEVTO, "1", "topic", "https://example.test/1", 10, null, FETCHED_AT)),
                new Case("null fetchedAt", new IngestSignalsCommand.Item(
                        SignalSource.DEVTO, "1", "topic", "https://example.test/1", 10, json("{}"), null)));

        for (Case testCase : cases) {
            assertThatThrownBy(() -> signalService.ingest(new IngestSignalsCommand(List.of(testCase.item()))))
                    .as(testCase.name())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(signalRepository, never()).saveAll(any());
    }

    @Test
    void rejectsTheWholeBatchWhenOneItemIsInvalid() {
        // Validation runs before any write, so a malformed item cannot leave a batch
        // half-stored.
        IngestSignalsCommand.Item valid = item(SignalSource.DEVTO, "1");
        IngestSignalsCommand.Item invalid = new IngestSignalsCommand.Item(
                SignalSource.DEVTO, "2", "topic", "https://example.test/2", 500, json("{}"), FETCHED_AT);

        assertThatThrownBy(() -> signalService.ingest(new IngestSignalsCommand(List.of(valid, invalid))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(signalRepository, never()).saveAll(any());
    }

    // --- list -----------------------------------------------------------------

    @Test
    void listsEverySignalWhenNoSourceGiven() {
        signalService.list(null);

        verify(signalRepository).findAllByOrderByScoreDesc();
    }

    @Test
    void listsBySourceWhenGiven() {
        signalService.list(SignalSource.HACKER_NEWS);

        verify(signalRepository).findBySourceOrderByScoreDesc(SignalSource.HACKER_NEWS);
    }
}
