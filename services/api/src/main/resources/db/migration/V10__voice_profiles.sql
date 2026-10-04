-- What a voice profile is *for*. The model can produce a competent post from a headline
-- without any of this; what it cannot do is sound like the person whose account it is posting
-- from. A profile is the author's own description of how they write — the third input to
-- drafting named in PLAN.md, alongside the signal and the target platform, and the only one
-- that has never existed in the schema.
--
-- A row rather than an application property because it is edited far more often than the
-- service is deployed, and because the author keeps several and picks one per draft.
CREATE TABLE voice_profiles (
    id           UUID         PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    -- The prompt fragment itself, in the author's own words ("dry, concrete, first person,
    -- never hashtags"). Deliberately one free-text field rather than structured columns for
    -- tone, length and emoji policy: every structured scheme considered either omitted
    -- something the author wanted to say or needed a new column to say it. The text reaches
    -- the system prompt verbatim, so anything expressible in English works — and pasting two
    -- or three of their own posts in here is a few-shot example set that costs no schema.
    instructions TEXT         NOT NULL,
    -- Mirrors topics.enabled: the off switch that keeps the configuration around. A disabled
    -- profile drops out of the selector but keeps its text, so a voice can be retired without
    -- losing what it said.
    enabled      BOOLEAN      NOT NULL,
    -- Pre-selects this profile in the generate dialog.
    is_default   BOOLEAN      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

-- Deliberately absent: a `platform` column. Voice is "how I sound"; how long a post may be is
-- the platform's business, and the drafting slice already derives that from
-- Platform.maxPostLength(). A platform column here would force a near-duplicate profile per
-- platform differing only in a number the prompt already supplies. If a per-platform override
-- is ever wanted it belongs in a child table keyed (voice_profile_id, platform), shaped like
-- topic_queries — not in a nullable column every read has to reason about.

-- Case-insensitive, matching uq_topics_name: "Technical" and "technical" are the same profile
-- to the person reading the selector. VoiceProfileService checks the same way, so the service
-- and the constraint agree rather than the database rejecting something the service allowed.
CREATE UNIQUE INDEX uq_voice_profiles_name ON voice_profiles (lower(name));

-- At most one default, enforced here and not only in the service, because "the default" has
-- to resolve to exactly one row or the generate endpoint is non-deterministic. A partial index
-- is the cheapest way to say that in Postgres: it indexes only the rows where is_default is
-- true, so any number of non-default profiles coexist under it.
--
-- Note for the service: switching the default must clear the old one and then set the new one
-- within a single transaction. Setting first leaves two true rows mid-statement and the index
-- rejects the write.
CREATE UNIQUE INDEX uq_voice_profiles_default ON voice_profiles (is_default) WHERE is_default;

-- No seed row. Topics ship empty and let the empty state teach that the radar polls nothing
-- until one exists; voice profiles do the same. A seeded profile with invented instructions
-- would sit in the author's list pretending to be theirs, which is worse than an empty list
-- that says what to do about it.
