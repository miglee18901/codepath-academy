CREATE TABLE business_email_retry_audit (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, email_id VARCHAR(36) NOT NULL, admin_id BIGINT NOT NULL,
 requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX idx_email_audit(email_id,id)
);
