CREATE TABLE business_outbox (
 id VARCHAR(36) PRIMARY KEY, event_type VARCHAR(40) NOT NULL, user_id BIGINT NOT NULL,
 payload JSON NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 published_at DATETIME(6), available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 lease_owner VARCHAR(36), lease_until DATETIME(6), attempts INT NOT NULL DEFAULT 0,
 last_error VARCHAR(80), INDEX idx_outbox_pending(published_at,available_at,lease_until)
);
CREATE TABLE business_inbox (
 consumer VARCHAR(30) NOT NULL, event_id VARCHAR(36) NOT NULL,
 processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(consumer,event_id)
);
CREATE TABLE course_completions (
 user_id BIGINT NOT NULL, course_id BIGINT NOT NULL, completed_at DATETIME(6) NOT NULL,
 historical BIT NOT NULL DEFAULT 0, PRIMARY KEY(user_id,course_id)
);
CREATE TABLE notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(36) NOT NULL UNIQUE,
 user_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL, message VARCHAR(500) NOT NULL,
 target VARCHAR(200) NOT NULL, created_at DATETIME(6) NOT NULL, read_at DATETIME(6),
 INDEX idx_notification_user(user_id,id), INDEX idx_notification_unread(user_id,read_at)
);
CREATE TABLE business_email_jobs (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
 message VARCHAR(500) NOT NULL, target VARCHAR(200) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0, available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 lease_owner VARCHAR(36), lease_until DATETIME(6), last_error VARCHAR(80),
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), sent_at DATETIME(6),
 INDEX idx_email_pending(status,available_at,lease_until)
);
CREATE TABLE business_report_facts (
 kind VARCHAR(20) NOT NULL, fact_key VARCHAR(100) NOT NULL, course_id BIGINT NOT NULL DEFAULT 0,
 occurred_at DATETIME(6) NOT NULL, report_date DATE NOT NULL, amount DECIMAL(18,0) NOT NULL DEFAULT 0,
 historical BIT NOT NULL DEFAULT 0, updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(kind,fact_key), INDEX idx_report_date(report_date,course_id,kind)
);
CREATE TABLE business_failures (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, consumer VARCHAR(30) NOT NULL, event_id VARCHAR(36) NOT NULL,
 source_topic VARCHAR(200) NOT NULL, payload LONGTEXT NOT NULL, error_code VARCHAR(80) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'FAILED', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 retry_at DATETIME(6), retry_by BIGINT, lease_owner VARCHAR(36), lease_until DATETIME(6),
 UNIQUE KEY uq_failure(consumer,event_id), INDEX idx_failure_status(status,id)
);
CREATE TABLE business_retry_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, failure_id BIGINT NOT NULL, admin_id BIGINT NOT NULL,
 requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE business_backfill (
 job VARCHAR(30) PRIMARY KEY, cursor_id BIGINT NOT NULL DEFAULT 0, done BIT NOT NULL DEFAULT 0,
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
INSERT INTO business_backfill(job) VALUES ('orders'),('enrollments'),('completions'),('completion_facts');
