ALTER TABLE user_profiles
    ADD COLUMN profile_image_url TEXT,
    ADD COLUMN profile_image_file_name VARCHAR(255),
    ADD COLUMN profile_image_content_type VARCHAR(100),
    ADD COLUMN profile_image_uploaded_at TIMESTAMPTZ;
