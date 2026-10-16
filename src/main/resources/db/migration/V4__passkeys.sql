ALTER TABLE users ADD COLUMN passkey_handle VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD UNIQUE KEY uq_user_passkey_handle(passkey_handle);

CREATE TABLE passkey_credentials (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_id VARCHAR(1400) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    public_key_cose TEXT NOT NULL,
    signature_count BIGINT NOT NULL,
    label VARCHAR(80) NOT NULL,
    transports VARCHAR(255) NOT NULL,
    backup_eligible BIT NOT NULL,
    backed_up BIT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_passkey_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uq_passkey_credential(credential_id),
    INDEX idx_passkey_user(user_id)
);
