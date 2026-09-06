CREATE TABLE signals (
    id          UUID PRIMARY KEY,
    source      VARCHAR(20)  NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    topic       TEXT         NOT NULL,
    url         TEXT         NOT NULL,
    score       INTEGER      NOT NULL,
    raw_payload JSONB        NOT NULL,
    fetched_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

-- The radar re-polls the same listings every run, so the same item arrives repeatedly.
-- (source, external_id) is the natural identity of an item and the conflict target the
-- ingest upsert uses: a second sighting refreshes the mutable fields rather than
-- inserting a duplicate. external_id is whatever the source calls its own id (HN item
-- number, dev.to article id, GitHub "owner/repo").
CREATE UNIQUE INDEX uq_signals_source_external_id ON signals (source, external_id);

-- Ranking the radar: highest-scoring first, and recency for "what came in this run".
CREATE INDEX idx_signals_score ON signals (score DESC);
CREATE INDEX idx_signals_fetched_at ON signals (fetched_at DESC);

-- Resolves the deferred FK noted in V5__drafts.sql: signals now exists, so a draft that
-- originated from a signal can reference it. Still nullable — a draft may be authored
-- from a free-form topic with no signal behind it.
ALTER TABLE drafts ADD CONSTRAINT fk_drafts_signal
    FOREIGN KEY (signal_id) REFERENCES signals (id);
