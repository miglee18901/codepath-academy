ALTER TABLE users ADD COLUMN password_login_enabled BIT NOT NULL DEFAULT b'1';

CREATE TABLE social_identities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_email VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_social_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_social_subject(provider, subject),
    UNIQUE KEY uq_social_user_provider(user_id, provider)
);
