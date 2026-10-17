CREATE TABLE ai_index_jobs (
 course_id BIGINT NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 1,
 indexed_revision BIGINT NOT NULL DEFAULT 0,
 attempts INT NOT NULL DEFAULT 0,
 available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 lease_until DATETIME(6), lease_owner VARCHAR(36),
 last_error VARCHAR(100), last_success_at DATETIME(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_ai_course FOREIGN KEY(course_id) REFERENCES courses(id) ON DELETE CASCADE,
 INDEX idx_ai_pending(available_at, lease_until)
);
INSERT INTO ai_index_jobs(course_id) SELECT id FROM courses;
