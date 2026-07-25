ALTER TABLE drafts ADD COLUMN scheduled_at   TIMESTAMPTZ;
ALTER TABLE drafts ADD COLUMN remote_id      VARCHAR(255);
ALTER TABLE drafts ADD COLUMN failure_reason TEXT;

-- remote_id is an interim home: per PLAN.md the published post (remote_id, published_at)
-- belongs on a dedicated `posts` table, introduced with the scheduler/publisher slice.
