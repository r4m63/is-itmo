-- Adds MinIO file metadata columns for import log.
ALTER TABLE vehicle_import_operation
    ADD COLUMN IF NOT EXISTS file_object_key TEXT,
    ADD COLUMN IF NOT EXISTS file_name TEXT,
    ADD COLUMN IF NOT EXISTS file_content_type TEXT,
    ADD COLUMN IF NOT EXISTS file_size BIGINT;

