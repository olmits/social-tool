-- What the radar is *for*. Without topics the poller stores whatever is globally popular;
-- a topic is the user's statement of what they write about, and it scopes both the queries
-- the poller sends upstream and the ranking the panel shows back.
CREATE TABLE topics (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- Case-insensitive, because "Rust" and "rust" are the same topic to the person reading the
-- list. TopicService checks the same way, so the service and the constraint agree rather
-- than the database rejecting something the service allowed.
CREATE UNIQUE INDEX uq_topics_name ON topics (lower(name));

-- The per-source query for a topic: dev.to takes a tag, GitHub takes search qualifiers,
-- Hacker News takes Algolia search terms. A topic with no row for a source is simply not
-- polled there, which is why this is a table rather than columns on `topics`.
CREATE TABLE topic_queries (
    topic_id UUID         NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    source   VARCHAR(20)  NOT NULL,
    query    VARCHAR(255) NOT NULL,
    PRIMARY KEY (topic_id, source)
);
