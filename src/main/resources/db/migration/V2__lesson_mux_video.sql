ALTER TABLE lessons
    ADD COLUMN mux_upload_id VARCHAR(100),
    ADD COLUMN mux_asset_id VARCHAR(100),
    ADD COLUMN mux_playback_id VARCHAR(100),
    ADD COLUMN mux_status VARCHAR(30);
