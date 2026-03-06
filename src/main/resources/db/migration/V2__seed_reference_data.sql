INSERT INTO companies (name, normalized_name, domain, created_at, updated_at)
VALUES
    ('Google', 'google', 'google.com', now(), now()),
    ('Amazon', 'amazon', 'amazon.com', now(), now()),
    ('Meta', 'meta', 'meta.com', now(), now()),
    ('Netflix', 'netflix', 'netflix.com', now(), now())
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO job_roles (name, parent_role_id, created_at)
VALUES
    ('Backend Engineer', NULL, now()),
    ('Frontend Engineer', NULL, now()),
    ('Fullstack Engineer', NULL, now()),
    ('Data Engineer', NULL, now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO categories (name, parent_id, created_at)
VALUES
    ('Behavioral', NULL, now()),
    ('System Design', NULL, now()),
    ('Data Structures', NULL, now()),
    ('Leadership', NULL, now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO tags (name, tag_type, created_at)
VALUES
    ('ownership', 'behavioral', now()),
    ('scalability', 'technical', now()),
    ('algorithms', 'technical', now()),
    ('collaboration', 'behavioral', now())
ON CONFLICT (name) DO NOTHING;
