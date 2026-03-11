INSERT INTO categories (name, parent_id, created_at)
VALUES
    ('Backend Engineering', NULL, now()),
    ('Database', NULL, now()),
    ('Testing', NULL, now()),
    ('Architecture', NULL, now()),
    ('Computer Science', NULL, now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO skill_categories (code, name, display_order, created_at, updated_at)
VALUES
    ('CS', 'Computer Science', 1, now(), now()),
    ('BACKEND', 'Backend', 2, now(), now()),
    ('DATABASE', 'Database', 3, now(), now()),
    ('SYSTEM_DESIGN', 'System Design', 4, now(), now()),
    ('ARCHITECTURE', 'Architecture', 5, now(), now()),
    ('TESTING', 'Testing', 6, now(), now())
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    display_order = EXCLUDED.display_order,
    updated_at = now();

INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
SELECT sc.id, v.name, v.description, now(), now()
FROM (
    VALUES
        ('BACKEND', 'Spring Boot', 'Build production backend APIs and services'),
        ('BACKEND', 'REST API', 'Design stable HTTP interfaces and contracts'),
        ('BACKEND', 'Kafka', 'Reason about message delivery and event pipelines'),
        ('DATABASE', 'PostgreSQL', 'Model data and query relational systems effectively'),
        ('DATABASE', 'Redis', 'Use caching and fast data structures safely'),
        ('SYSTEM_DESIGN', 'Message Queue', 'Design async workflows with retries and backpressure'),
        ('SYSTEM_DESIGN', 'Caching', 'Choose invalidation and freshness strategies'),
        ('ARCHITECTURE', 'Distributed Systems', 'Reason about service boundaries and reliability'),
        ('TESTING', 'Integration Testing', 'Verify production-like backend behavior'),
        ('CS', 'Data Structures', 'Choose core algorithmic structures well')
) AS v(category_code, name, description)
JOIN skill_categories sc ON sc.code = v.category_code
ON CONFLICT (name) DO UPDATE
SET skill_category_id = EXCLUDED.skill_category_id,
    description = EXCLUDED.description,
    updated_at = now();

INSERT INTO questions (
    author_user_id,
    category_id,
    title,
    body,
    question_type,
    difficulty_level,
    source_type,
    quality_status,
    visibility,
    expected_answer_seconds,
    is_active,
    created_at,
    updated_at
)
SELECT
    NULL,
    c.id,
    v.title,
    v.body,
    v.question_type,
    v.difficulty_level,
    v.source_type,
    'approved',
    'public',
    v.expected_answer_seconds,
    TRUE,
    now(),
    now()
FROM (
    VALUES
        ('System Design', 'How do you keep queue consumers idempotent?', 'Explain duplicate delivery handling, idempotency keys, and write guarantees.', 'technical', 'MEDIUM', 'follow_up', 180),
        ('System Design', 'How do you handle poison messages?', 'Describe dead-letter queues, retry thresholds, and operational visibility.', 'technical', 'HARD', 'follow_up', 210),
        ('System Design', 'What are cache-aside tradeoffs?', 'Compare cache-aside with write-through and explain failure modes.', 'technical', 'MEDIUM', 'follow_up', 180),
        ('System Design', 'When would you choose write-through or write-behind caching?', 'Discuss freshness, durability, consistency, and operational complexity.', 'technical', 'HARD', 'follow_up', 210),
        ('System Design', 'How did you measure the 40 percent latency improvement?', 'Walk through the metrics, baseline, sampling window, and confounders you handled.', 'technical', 'MEDIUM', 'resume_probe', 180)
) AS v(category_name, title, body, question_type, difficulty_level, source_type, expected_answer_seconds)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM questions q
    WHERE q.title = v.title
);

INSERT INTO question_relationships (parent_question_id, child_question_id, relationship_type, depth, display_order, created_at)
SELECT parent_q.id, child_q.id, v.relationship_type, v.depth, v.display_order, now()
FROM (
    VALUES
        ('Design a resilient queue', 'How do you keep queue consumers idempotent?', 'follow_up', 1, 1),
        ('Design a resilient queue', 'How do you handle poison messages?', 'follow_up', 1, 2),
        ('Explain cache invalidation', 'What are cache-aside tradeoffs?', 'follow_up', 1, 1),
        ('Explain cache invalidation', 'When would you choose write-through or write-behind caching?', 'follow_up', 1, 2),
        ('Explain cache invalidation', 'How did you measure the 40 percent latency improvement?', 'resume_probe', 1, 3)
) AS v(parent_title, child_title, relationship_type, depth, display_order)
JOIN questions parent_q ON parent_q.title = v.parent_title
JOIN questions child_q ON child_q.title = v.child_title
ON CONFLICT (parent_question_id, child_question_id, relationship_type) DO NOTHING;

INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at)
SELECT q.id, s.id, v.weight, now()
FROM (
    VALUES
        ('Design a resilient queue', 'Message Queue', 1.00),
        ('Design a resilient queue', 'Distributed Systems', 0.80),
        ('How do you keep queue consumers idempotent?', 'Message Queue', 0.90),
        ('How do you keep queue consumers idempotent?', 'PostgreSQL', 0.50),
        ('How do you handle poison messages?', 'Message Queue', 0.90),
        ('Explain cache invalidation', 'Caching', 1.00),
        ('Explain cache invalidation', 'Redis', 0.80),
        ('What are cache-aside tradeoffs?', 'Caching', 0.90),
        ('When would you choose write-through or write-behind caching?', 'Caching', 0.90),
        ('How did you measure the 40 percent latency improvement?', 'REST API', 0.40),
        ('How did you measure the 40 percent latency improvement?', 'Distributed Systems', 0.60)
    ) AS v(question_title, skill_name, weight)
JOIN questions q ON q.title = v.question_title
JOIN skills s ON s.name = v.skill_name
ON CONFLICT (question_id, skill_id) DO UPDATE
SET weight = EXCLUDED.weight;

INSERT INTO career_benchmarks (job_role_id, experience_band_code, skill_category_id, benchmark_score, created_at, updated_at)
SELECT jr.id, v.experience_band_code, sc.id, v.benchmark_score, now(), now()
FROM (
    VALUES
        ('Backend Engineer', 'MID', 'CS', 72.0),
        ('Backend Engineer', 'MID', 'BACKEND', 78.0),
        ('Backend Engineer', 'MID', 'DATABASE', 68.0),
        ('Backend Engineer', 'MID', 'SYSTEM_DESIGN', 62.0),
        ('Backend Engineer', 'MID', 'ARCHITECTURE', 58.0),
        ('Backend Engineer', 'MID', 'TESTING', 66.0),
        ('Backend Engineer', 'SENIOR', 'CS', 78.0),
        ('Backend Engineer', 'SENIOR', 'BACKEND', 84.0),
        ('Backend Engineer', 'SENIOR', 'DATABASE', 76.0),
        ('Backend Engineer', 'SENIOR', 'SYSTEM_DESIGN', 74.0),
        ('Backend Engineer', 'SENIOR', 'ARCHITECTURE', 72.0),
        ('Backend Engineer', 'SENIOR', 'TESTING', 70.0)
) AS v(role_name, experience_band_code, skill_category_code, benchmark_score)
JOIN job_roles jr ON jr.name = v.role_name
JOIN skill_categories sc ON sc.code = v.skill_category_code
ON CONFLICT (job_role_id, experience_band_code, skill_category_id) DO UPDATE
SET benchmark_score = EXCLUDED.benchmark_score,
    updated_at = now();
