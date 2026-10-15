ALTER TABLE lesson_progress
    ADD COLUMN position_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN duration_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN position_updated_at BIGINT NOT NULL DEFAULT 0;
