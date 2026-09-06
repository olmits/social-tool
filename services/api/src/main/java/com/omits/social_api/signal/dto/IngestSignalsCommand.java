package com.omits.social_api.signal.dto;

import tools.jackson.databind.JsonNode;
import com.omits.social_api.signal.model.SignalSource;

import java.time.Instant;
import java.util.List;

/**
 * One radar run's harvest, posted as a single batch by the Go poller.
 *
 * <p>A batch may mix sources: the radar fans out across every source in a run and reports
 * once, so a partially-failed run still delivers whatever its healthy sources returned.
 */
public record IngestSignalsCommand(List<Item> signals) {

    /**
     * A single normalized item.
     *
     * @param source     which source produced it
     * @param externalId the source's own id for the item; the dedup key alongside {@code source}
     * @param topic      the item's headline, used as drafting input
     * @param url        where the item lives
     * @param score      popularity normalized to 0-100 within its source for this run
     * @param rawPayload the source's original JSON for the item, carried as JSON rather than
     *                   as an encoded string: Go's {@code json.RawMessage} emits it inline,
     *                   the column it lands in is {@code jsonb}, and taking it as a
     *                   {@link JsonNode} means Jackson rejects a malformed payload here
     *                   instead of Postgres rejecting it at insert time
     * @param fetchedAt  when the poller retrieved it
     */
    public record Item(SignalSource source, String externalId, String topic, String url, int score,
                       JsonNode rawPayload, Instant fetchedAt) {
    }
}
