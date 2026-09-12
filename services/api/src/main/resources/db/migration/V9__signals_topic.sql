-- `topic` held the item's *headline* ("Shipping Go services"), while the panel has always
-- used "topic" for the category ("Runtimes") and "title" for the headline. Now that topics
-- are a real entity the old name is actively misleading.
ALTER TABLE signals RENAME COLUMN topic TO title;

-- Nullable: a signal polled before topics existed, or from a source that is not topic-scoped,
-- has no topic behind it. ON DELETE SET NULL so removing a topic orphans its signals rather
-- than deleting content the user may still be drafting from.
ALTER TABLE signals ADD COLUMN topic_id UUID;
ALTER TABLE signals ADD CONSTRAINT fk_signals_topic
    FOREIGN KEY (topic_id) REFERENCES topics (id) ON DELETE SET NULL;

-- The source's own popularity count, before normalization: HN points, dev.to reactions,
-- GitHub stars. `score` flattens these onto 0-100 and is what the radar ranks by; this is
-- what the panel's "Engagement" column shows, and the two are not interchangeable.
--
-- DEFAULT 0 backfills rows polled before this column existed. They are corrected by the next
-- radar pass, which refreshes every signal it sees again.
ALTER TABLE signals ADD COLUMN native_score INTEGER NOT NULL DEFAULT 0;

-- The panel's per-topic ranking: "the best signals for this topic, highest first".
CREATE INDEX idx_signals_topic_score ON signals (topic_id, score DESC);
