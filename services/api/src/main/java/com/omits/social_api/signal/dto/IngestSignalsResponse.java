package com.omits.social_api.signal.dto;

/**
 * What one ingest batch did. The poller logs these counts, which is the cheapest way to
 * see a source going stale: a run that is all {@code updated} and no {@code created} means
 * nothing new is arriving.
 */
public record IngestSignalsResponse(int received, int created, int updated) {
}
