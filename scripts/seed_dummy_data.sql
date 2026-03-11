BEGIN;

INSERT INTO users (email, password_hash, provider, provider_user_id, status, created_at, updated_at)
SELECT
    'demo@example.com',
    '$2y$10$0Gw1AAHYmWInfa2CdRcJdObVOPNM3DD9SFY2zho/pvvu7hf/aO1aq',
    'local',
    NULL,
    'ACTIVE',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE email = 'demo@example.com'
);

INSERT INTO user_profiles (
    user_id,
    nickname,
    job_role_id,
    years_of_experience,
    avg_score,
    archived_question_count,
    answer_visibility_default,
    created_at,
    updated_at
)
SELECT
    u.id,
    'demo-user',
    jr.id,
    5,
    78.50,
    1,
    'private',
    now(),
    now()
FROM users u
JOIN job_roles jr ON jr.name = 'Backend Engineer'
WHERE u.email = 'demo@example.com'
ON CONFLICT (user_id) DO UPDATE
SET nickname = EXCLUDED.nickname,
    job_role_id = EXCLUDED.job_role_id,
    years_of_experience = EXCLUDED.years_of_experience,
    avg_score = EXCLUDED.avg_score,
    archived_question_count = EXCLUDED.archived_question_count,
    answer_visibility_default = EXCLUDED.answer_visibility_default,
    updated_at = now();

INSERT INTO user_settings (
    user_id,
    target_score_threshold,
    pass_score_threshold,
    retry_enabled,
    daily_question_count,
    created_at,
    updated_at
)
SELECT
    u.id,
    85,
    65,
    TRUE,
    1,
    now(),
    now()
FROM users u
WHERE u.email = 'demo@example.com'
ON CONFLICT (user_id) DO UPDATE
SET target_score_threshold = EXCLUDED.target_score_threshold,
    pass_score_threshold = EXCLUDED.pass_score_threshold,
    retry_enabled = EXCLUDED.retry_enabled,
    daily_question_count = EXCLUDED.daily_question_count,
    updated_at = now();

INSERT INTO user_target_companies (user_id, company_id, priority_order, created_at)
SELECT u.id, c.id, v.priority_order, now()
FROM users u
JOIN (
    VALUES
        ('Amazon', 1),
        ('Google', 2)
) AS v(company_name, priority_order) ON TRUE
JOIN companies c ON c.name = v.company_name
WHERE u.email = 'demo@example.com'
ON CONFLICT (user_id, company_id) DO UPDATE
SET priority_order = EXCLUDED.priority_order;

INSERT INTO resumes (user_id, title, is_primary, created_at, updated_at)
SELECT u.id, 'Demo Resume', TRUE, now(), now()
FROM users u
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM resumes r
      WHERE r.user_id = u.id
        AND r.title = 'Demo Resume'
  );

INSERT INTO resume_versions (
    resume_id,
    version_no,
    file_url,
    raw_text,
    parsed_json,
    summary_text,
    is_active,
    uploaded_at,
    created_at
)
SELECT
    r.id,
    1,
    'https://files.example.com/demo-resume.pdf',
    'Experienced backend engineer focused on Kotlin, Spring Boot, PostgreSQL, and distributed systems.',
    '{"skills":["Kotlin","Spring Boot","PostgreSQL","Distributed Systems"]}',
    'Backend engineer with strong system design and delivery experience.',
    TRUE,
    now(),
    now()
FROM resumes r
JOIN users u ON u.id = r.user_id
WHERE u.email = 'demo@example.com'
  AND r.title = 'Demo Resume'
ON CONFLICT (resume_id, version_no) DO UPDATE
SET file_url = EXCLUDED.file_url,
    raw_text = EXCLUDED.raw_text,
    parsed_json = EXCLUDED.parsed_json,
    summary_text = EXCLUDED.summary_text,
    is_active = TRUE,
    uploaded_at = EXCLUDED.uploaded_at;

UPDATE resume_versions
SET is_active = FALSE
WHERE resume_id IN (
    SELECT r.id
    FROM resumes r
    JOIN users u ON u.id = r.user_id
    WHERE u.email = 'demo@example.com'
      AND r.title = 'Demo Resume'
)
  AND version_no <> 1;

INSERT INTO learning_materials (
    title,
    material_type,
    content_text,
    content_url,
    source_name,
    created_at,
    updated_at
)
SELECT
    v.title,
    v.material_type,
    NULL,
    v.content_url,
    v.source_name,
    now(),
    now()
FROM (
    VALUES
        ('Queue Design Guide', 'article', 'https://example.com/queue-guide', 'Engineering Blog'),
        ('Caching Patterns', 'video', 'https://example.com/caching-patterns', 'Tech Channel'),
        ('Behavioral Storytelling', 'article', 'https://example.com/storytelling', 'Interview Notes'),
        ('Async Integration Testing Guide', 'article', 'https://example.com/async-testing', 'Quality Handbook'),
        ('PostgreSQL Query Plan Clinic', 'article', 'https://example.com/postgres-plans', 'DB Notes'),
        ('Practical Data Structures for APIs', 'video', 'https://example.com/data-structures', 'Tech Channel'),
        ('API Versioning Playbook', 'article', 'https://example.com/api-versioning', 'Architecture Notes')
) AS v(title, material_type, content_url, source_name)
WHERE NOT EXISTS (
    SELECT 1
    FROM learning_materials lm
    WHERE lm.title = v.title
);

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
    'catalog',
    'approved',
    'public',
    v.expected_answer_seconds,
    TRUE,
    now(),
    now()
FROM (
    VALUES
        ('System Design', 'Design a resilient queue', 'How would you design a durable queue with retries, backpressure, and idempotent consumers?', 'technical', 'HARD', 300),
        ('System Design', 'Explain cache invalidation', 'Describe how you would handle cache eviction, freshness, and consistency in a read-heavy service.', 'technical', 'MEDIUM', 240),
        ('Behavioral', 'Tell me about a time you took ownership', 'Walk through a concrete example where you stepped in, aligned stakeholders, and delivered a result.', 'behavioral', 'MEDIUM', 180)
) AS v(category_name, title, body, question_type, difficulty_level, expected_answer_seconds)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM questions q
    WHERE q.title = v.title
);

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
    'catalog',
    'approved',
    'public',
    v.expected_answer_seconds,
    TRUE,
    now(),
    now()
FROM (
    VALUES
        ('Testing', 'How do you structure integration tests for asynchronous services?', 'Explain how you test message consumption, retries, dead-letter handling, and eventual consistency in a backend service.', 'technical', 'MEDIUM', 240),
        ('Database', 'How do you model indexes and query plans in PostgreSQL?', 'Walk through how you choose indexes, validate plans, and balance writes against read performance.', 'technical', 'HARD', 240),
        ('Data Structures', 'How would you compare arrays, linked lists, stacks, and queues in practice?', 'Focus on real backend tradeoffs rather than textbook definitions.', 'technical', 'MEDIUM', 180),
        ('Backend Engineering', 'How do you version REST APIs safely?', 'Explain compatibility, rollout strategy, and how clients migrate without breaking changes.', 'technical', 'MEDIUM', 180),
        ('Architecture', 'How do you define service boundaries in a distributed backend?', 'Describe how you balance ownership, coupling, latency, and failure isolation.', 'technical', 'HARD', 240)
) AS v(category_name, title, body, question_type, difficulty_level, expected_answer_seconds)
JOIN categories c ON c.name = v.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM questions q
    WHERE q.title = v.title
);

INSERT INTO question_tags (question_id, tag_id, created_at)
SELECT q.id, t.id, now()
FROM questions q
JOIN tags t ON (
    (q.title = 'Design a resilient queue' AND t.name = 'scalability') OR
    (q.title = 'Explain cache invalidation' AND t.name = 'scalability') OR
    (q.title = 'Tell me about a time you took ownership' AND t.name = 'ownership')
)
ON CONFLICT (question_id, tag_id) DO NOTHING;

INSERT INTO question_tags (question_id, tag_id, created_at)
SELECT q.id, t.id, now()
FROM questions q
JOIN tags t ON (
    (q.title = 'How do you structure integration tests for asynchronous services?' AND t.name = 'scalability') OR
    (q.title = 'How do you model indexes and query plans in PostgreSQL?' AND t.name = 'scalability') OR
    (q.title = 'How would you compare arrays, linked lists, stacks, and queues in practice?' AND t.name = 'algorithms') OR
    (q.title = 'How do you version REST APIs safely?' AND t.name = 'collaboration') OR
    (q.title = 'How do you define service boundaries in a distributed backend?' AND t.name = 'scalability')
)
ON CONFLICT (question_id, tag_id) DO NOTHING;

INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
SELECT q.id, c.id, v.relevance_score, v.is_past_frequent, v.is_trending_recent, now()
FROM (
    VALUES
        ('Design a resilient queue', 'Amazon', 0.95, TRUE, TRUE),
        ('Design a resilient queue', 'Google', 0.80, FALSE, TRUE),
        ('Explain cache invalidation', 'Google', 0.92, TRUE, FALSE),
        ('Tell me about a time you took ownership', 'Amazon', 0.75, TRUE, FALSE)
) AS v(question_title, company_name, relevance_score, is_past_frequent, is_trending_recent)
JOIN questions q ON q.title = v.question_title
JOIN companies c ON c.name = v.company_name
ON CONFLICT (question_id, company_id) DO UPDATE
SET relevance_score = EXCLUDED.relevance_score,
    is_past_frequent = EXCLUDED.is_past_frequent,
    is_trending_recent = EXCLUDED.is_trending_recent;

INSERT INTO question_roles (question_id, job_role_id, relevance_score, created_at)
SELECT q.id, jr.id, v.relevance_score, now()
FROM (
    VALUES
        ('Design a resilient queue', 'Backend Engineer', 0.95),
        ('Explain cache invalidation', 'Backend Engineer', 0.88),
        ('Tell me about a time you took ownership', 'Backend Engineer', 0.70),
        ('How do you structure integration tests for asynchronous services?', 'Backend Engineer', 0.90),
        ('How do you model indexes and query plans in PostgreSQL?', 'Backend Engineer', 0.94),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'Backend Engineer', 0.72),
        ('How do you version REST APIs safely?', 'Backend Engineer', 0.89),
        ('How do you define service boundaries in a distributed backend?', 'Backend Engineer', 0.93)
) AS v(question_title, role_name, relevance_score)
JOIN questions q ON q.title = v.question_title
JOIN job_roles jr ON jr.name = v.role_name
ON CONFLICT (question_id, job_role_id) DO UPDATE
SET relevance_score = EXCLUDED.relevance_score;

INSERT INTO question_learning_materials (question_id, learning_material_id, relevance_score, created_at)
SELECT q.id, lm.id, v.relevance_score, now()
FROM (
    VALUES
        ('Design a resilient queue', 'Queue Design Guide', 0.96),
        ('Explain cache invalidation', 'Caching Patterns', 0.94),
        ('Tell me about a time you took ownership', 'Behavioral Storytelling', 0.90),
        ('How do you structure integration tests for asynchronous services?', 'Async Integration Testing Guide', 0.95),
        ('How do you model indexes and query plans in PostgreSQL?', 'PostgreSQL Query Plan Clinic', 0.96),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'Practical Data Structures for APIs', 0.90),
        ('How do you version REST APIs safely?', 'API Versioning Playbook', 0.93)
) AS v(question_title, material_title, relevance_score)
JOIN questions q ON q.title = v.question_title
JOIN learning_materials lm ON lm.title = v.material_title
ON CONFLICT (question_id, learning_material_id) DO UPDATE
SET relevance_score = EXCLUDED.relevance_score;

INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at)
SELECT q.id, s.id, v.weight, now()
FROM (
    VALUES
        ('How do you structure integration tests for asynchronous services?', 'Integration Testing', 1.00),
        ('How do you structure integration tests for asynchronous services?', 'Kafka', 0.40),
        ('How do you structure integration tests for asynchronous services?', 'Distributed Systems', 0.50),
        ('How do you model indexes and query plans in PostgreSQL?', 'PostgreSQL', 1.00),
        ('How do you model indexes and query plans in PostgreSQL?', 'REST API', 0.30),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'Data Structures', 1.00),
        ('How do you version REST APIs safely?', 'REST API', 1.00),
        ('How do you version REST APIs safely?', 'Spring Boot', 0.60),
        ('How do you define service boundaries in a distributed backend?', 'Distributed Systems', 0.90),
        ('How do you define service boundaries in a distributed backend?', 'Spring Boot', 0.30),
        ('How do you keep queue consumers idempotent?', 'Distributed Systems', 0.40),
        ('How do you handle poison messages?', 'Distributed Systems', 0.50)
) AS v(question_title, skill_name, weight)
JOIN questions q ON q.title = v.question_title
JOIN skills s ON s.name = v.skill_name
ON CONFLICT (question_id, skill_id) DO UPDATE
SET weight = EXCLUDED.weight;

INSERT INTO daily_cards (
    user_id,
    question_id,
    card_date,
    card_type,
    source_reason,
    status,
    delivered_at,
    opened_at,
    created_at
)
SELECT
    u.id,
    q.id,
    current_date,
    'daily',
    'recommendation',
    'opened',
    now(),
    now(),
    now()
FROM users u
JOIN questions q ON q.title = 'Design a resilient queue'
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM daily_cards dc
      WHERE dc.user_id = u.id
        AND dc.question_id = q.id
        AND dc.card_date = current_date
  );

INSERT INTO answer_attempts (
    user_id,
    question_id,
    resume_version_id,
    source_daily_card_id,
    attempt_no,
    answer_mode,
    content_text,
    submitted_at,
    created_at
)
SELECT
    u.id,
    q.id,
    rv.id,
    dc.id,
    1,
    'text',
    'First, I would clarify throughput targets and retention needs. Then I would use idempotent consumers, backpressure, and retry policies to keep the system reliable under failure.',
    now() - interval '2 days',
    now() - interval '2 days'
FROM users u
JOIN questions q ON q.title = 'Design a resilient queue'
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
LEFT JOIN daily_cards dc ON dc.user_id = u.id AND dc.question_id = q.id AND dc.card_date = current_date
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM answer_attempts aa
      WHERE aa.user_id = u.id
        AND aa.question_id = q.id
        AND aa.attempt_no = 1
  );

INSERT INTO answer_attempts (
    user_id,
    question_id,
    resume_version_id,
    source_daily_card_id,
    attempt_no,
    answer_mode,
    content_text,
    submitted_at,
    created_at
)
SELECT
    u.id,
    q.id,
    rv.id,
    NULL,
    1,
    'text',
    v.content_text,
    now() - v.submitted_offset,
    now() - v.submitted_offset
FROM (
    VALUES
        ('How do you keep queue consumers idempotent?', interval '20 hours', 'I would persist an idempotency key at the write boundary, make the consumer side effects conditional on first-seen processing, and keep retries safe by separating acknowledgement from state mutation.'),
        ('How do you handle poison messages?', interval '18 hours', 'I would cap retries, move poison messages to a dead-letter queue, attach failure metadata, and make replay tooling explicit so operators can inspect and safely reprocess them.'),
        ('How do you structure integration tests for asynchronous services?', interval '14 hours', 'I would test the happy path first and then maybe run the consumer locally, but I have not built a consistent strategy for retries, lag, or dead-letter validation.'),
        ('How do you model indexes and query plans in PostgreSQL?', interval '12 hours', 'I usually add an index on the filtered column, check if things feel faster, and only look at EXPLAIN later if there is still a problem.'),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', interval '10 hours', 'Arrays are fast, linked lists are dynamic, stacks are LIFO, and queues are FIFO. I would choose based on the problem.'),
        ('How do you version REST APIs safely?', interval '8 hours', 'I avoid breaking changes by adding fields, versioning only when semantics change, documenting deprecations, and rolling out migrations with monitoring so clients have a clear upgrade path.'),
        ('How do you define service boundaries in a distributed backend?', interval '6 hours', 'I look at ownership, change cadence, failure isolation, and transaction boundaries. I split services only when that reduces coordination cost more than it increases latency and operational complexity.')
) AS v(question_title, submitted_offset, content_text)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM answer_attempts aa
    WHERE aa.user_id = u.id
      AND aa.question_id = q.id
      AND aa.attempt_no = 1
);

INSERT INTO answer_attempts (
    user_id,
    question_id,
    resume_version_id,
    source_daily_card_id,
    attempt_no,
    answer_mode,
    content_text,
    submitted_at,
    created_at
)
SELECT
    u.id,
    q.id,
    rv.id,
    NULL,
    1,
    'text',
    'I would define cache ownership, TTL policy, and invalidation events. Then I would measure stale-read risk and tune write-through or async invalidation paths based on the consistency requirements.',
    now() - interval '1 day',
    now() - interval '1 day'
FROM users u
JOIN questions q ON q.title = 'Explain cache invalidation'
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM answer_attempts aa
      WHERE aa.user_id = u.id
        AND aa.question_id = q.id
        AND aa.attempt_no = 1
  );

INSERT INTO answer_scores (
    answer_attempt_id,
    total_score,
    structure_score,
    specificity_score,
    technical_accuracy_score,
    role_fit_score,
    company_fit_score,
    communication_score,
    evaluation_result,
    evaluated_at
)
SELECT
    aa.id,
    v.total_score,
    v.structure_score,
    v.specificity_score,
    v.technical_accuracy_score,
    v.role_fit_score,
    v.company_fit_score,
    v.communication_score,
    v.evaluation_result,
    now()
FROM (
    VALUES
        ('Design a resilient queue', 91.0, 88.0, 90.0, 94.0, 85.0, 82.0, 89.0, 'PASS'),
        ('Explain cache invalidation', 54.0, 58.0, 52.0, 49.0, 55.0, 47.0, 56.0, 'FAIL'),
        ('How do you keep queue consumers idempotent?', 88.0, 85.0, 86.0, 90.0, 82.0, 75.0, 84.0, 'PASS'),
        ('How do you handle poison messages?', 84.0, 82.0, 81.0, 86.0, 78.0, 70.0, 80.0, 'PASS'),
        ('How do you structure integration tests for asynchronous services?', 43.0, 45.0, 38.0, 44.0, 40.0, 35.0, 48.0, 'FAIL'),
        ('How do you model indexes and query plans in PostgreSQL?', 51.0, 54.0, 45.0, 52.0, 50.0, 42.0, 55.0, 'FAIL'),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 57.0, 60.0, 43.0, 55.0, 52.0, 41.0, 62.0, 'FAIL'),
        ('How do you version REST APIs safely?', 86.0, 84.0, 82.0, 88.0, 83.0, 79.0, 85.0, 'PASS'),
        ('How do you define service boundaries in a distributed backend?', 83.0, 80.0, 79.0, 85.0, 81.0, 74.0, 82.0, 'PASS')
) AS v(question_title, total_score, structure_score, specificity_score, technical_accuracy_score, role_fit_score, company_fit_score, communication_score, evaluation_result)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
ON CONFLICT (answer_attempt_id) DO UPDATE
SET total_score = EXCLUDED.total_score,
    structure_score = EXCLUDED.structure_score,
    specificity_score = EXCLUDED.specificity_score,
    technical_accuracy_score = EXCLUDED.technical_accuracy_score,
    role_fit_score = EXCLUDED.role_fit_score,
    company_fit_score = EXCLUDED.company_fit_score,
    communication_score = EXCLUDED.communication_score,
    evaluation_result = EXCLUDED.evaluation_result,
    evaluated_at = EXCLUDED.evaluated_at;

INSERT INTO answer_feedback_items (
    answer_attempt_id,
    feedback_type,
    severity,
    title,
    body,
    display_order,
    created_at
)
SELECT
    aa.id,
    v.feedback_type,
    v.severity,
    v.title,
    v.body,
    v.display_order,
    now()
FROM (
    VALUES
        ('Design a resilient queue', 'strength', 'info', 'Strong structure', 'You laid out constraints, architecture, and operational protections clearly.', 1),
        ('Design a resilient queue', 'next_step', 'low', 'Push deeper on tradeoffs', 'Call out queue retention, poison message handling, and monitoring choices more explicitly.', 2),
        ('Explain cache invalidation', 'improvement', 'high', 'Need stronger consistency framing', 'Explain cache ownership, invalidation triggers, and acceptable stale-read windows.', 1),
        ('Explain cache invalidation', 'improvement', 'medium', 'Add concrete examples', 'Use a production example with TTL, event propagation, or write-through behavior.', 2),
        ('How do you keep queue consumers idempotent?', 'strength', 'info', 'Good idempotency framing', 'You connected write boundaries, retries, and side-effect safety well.', 1),
        ('How do you keep queue consumers idempotent?', 'next_step', 'low', 'Add storage tradeoffs', 'Call out how you expire keys and handle exactly-once assumptions.', 2),
        ('How do you handle poison messages?', 'strength', 'info', 'Operationally grounded answer', 'You covered retry caps, dead-letter queues, and replay visibility.', 1),
        ('How do you handle poison messages?', 'next_step', 'low', 'Push on alerting detail', 'Add a short explanation of alert thresholds and replay safeguards.', 2),
        ('How do you structure integration tests for asynchronous services?', 'improvement', 'high', 'Coverage is too shallow', 'You need a repeatable strategy for retries, lag assertions, and dead-letter validation.', 1),
        ('How do you structure integration tests for asynchronous services?', 'improvement', 'medium', 'Ground the answer in tooling', 'Mention fixtures, containerized dependencies, or event polling assertions.', 2),
        ('How do you model indexes and query plans in PostgreSQL?', 'improvement', 'high', 'Need stronger query-plan reasoning', 'Talk through EXPLAIN, selectivity, and the write cost of additional indexes.', 1),
        ('How do you model indexes and query plans in PostgreSQL?', 'improvement', 'medium', 'Add a concrete indexing example', 'Explain one composite or partial index decision with tradeoffs.', 2),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'improvement', 'high', 'Too textbook-oriented', 'Anchor the answer in concrete backend use cases and memory/access tradeoffs.', 1),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'improvement', 'medium', 'Connect to real workloads', 'Mention queues for buffering, stacks for traversal, and arrays for cache-friendly reads.', 2),
        ('How do you version REST APIs safely?', 'strength', 'info', 'Clear compatibility framing', 'You balanced additive change, deprecation, and rollout well.', 1),
        ('How do you version REST APIs safely?', 'next_step', 'low', 'Add contract-testing detail', 'Mention schema checks or consumer contract validation for safety.', 2),
        ('How do you define service boundaries in a distributed backend?', 'strength', 'info', 'Good boundary heuristics', 'You used ownership, latency, and failure isolation as sound decision criteria.', 1),
        ('How do you define service boundaries in a distributed backend?', 'next_step', 'low', 'Add one migration example', 'Briefly explain how you would split or merge a boundary over time.', 2)
) AS v(question_title, feedback_type, severity, title, body, display_order)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM answer_feedback_items afi
    WHERE afi.answer_attempt_id = aa.id
      AND afi.display_order = v.display_order
);

INSERT INTO user_question_progress (
    user_id,
    question_id,
    latest_answer_attempt_id,
    best_answer_attempt_id,
    latest_score,
    best_score,
    total_attempt_count,
    unanswered_count,
    current_status,
    archived_at,
    last_answered_at,
    next_review_at,
    mastery_level,
    created_at,
    updated_at
)
SELECT
    u.id,
    q.id,
    aa.id,
    aa.id,
    s.total_score,
    s.total_score,
    1,
    0,
    v.current_status,
    v.archived_at,
    aa.submitted_at,
    v.next_review_at,
    v.mastery_level,
    now(),
    now()
FROM (
    VALUES
        ('Design a resilient queue', 'archived', now() - interval '2 days', NULL::timestamptz, 'advanced'),
        ('Explain cache invalidation', 'retry_pending', NULL::timestamptz, now() + interval '1 day', 'beginner'),
        ('How do you keep queue consumers idempotent?', 'archived', now() - interval '20 hours', NULL::timestamptz, 'advanced'),
        ('How do you handle poison messages?', 'in_progress', NULL::timestamptz, NULL::timestamptz, 'intermediate'),
        ('How do you structure integration tests for asynchronous services?', 'retry_pending', NULL::timestamptz, now() + interval '8 hour', 'beginner'),
        ('How do you model indexes and query plans in PostgreSQL?', 'retry_pending', NULL::timestamptz, now() + interval '12 hour', 'beginner'),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'retry_pending', NULL::timestamptz, now() + interval '16 hour', 'beginner'),
        ('How do you version REST APIs safely?', 'archived', now() - interval '8 hours', NULL::timestamptz, 'advanced'),
        ('How do you define service boundaries in a distributed backend?', 'in_progress', NULL::timestamptz, NULL::timestamptz, 'intermediate')
) AS v(question_title, current_status, archived_at, next_review_at, mastery_level)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
JOIN answer_scores s ON s.answer_attempt_id = aa.id
ON CONFLICT (user_id, question_id) DO UPDATE
SET latest_answer_attempt_id = EXCLUDED.latest_answer_attempt_id,
    best_answer_attempt_id = EXCLUDED.best_answer_attempt_id,
    latest_score = EXCLUDED.latest_score,
    best_score = EXCLUDED.best_score,
    total_attempt_count = EXCLUDED.total_attempt_count,
    unanswered_count = EXCLUDED.unanswered_count,
    current_status = EXCLUDED.current_status,
    archived_at = EXCLUDED.archived_at,
    last_answered_at = EXCLUDED.last_answered_at,
    next_review_at = EXCLUDED.next_review_at,
    mastery_level = EXCLUDED.mastery_level,
    updated_at = now();

INSERT INTO review_queue (
    user_id,
    question_id,
    trigger_answer_attempt_id,
    reason_type,
    priority,
    scheduled_for,
    status,
    created_at,
    updated_at
)
SELECT
    u.id,
    q.id,
    aa.id,
    'low_total',
    100,
    now() - interval '2 hour',
    'pending',
    now(),
    now()
FROM users u
JOIN questions q ON q.title = 'Explain cache invalidation'
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
WHERE u.email = 'demo@example.com'
  AND NOT EXISTS (
      SELECT 1
      FROM review_queue rq
      WHERE rq.user_id = u.id
        AND rq.question_id = q.id
        AND rq.status = 'pending'
  );

INSERT INTO review_queue (
    user_id,
    question_id,
    trigger_answer_attempt_id,
    reason_type,
    priority,
    scheduled_for,
    status,
    created_at,
    updated_at
)
SELECT
    u.id,
    q.id,
    aa.id,
    v.reason_type,
    v.priority,
    v.scheduled_for,
    'pending',
    now(),
    now()
FROM (
    VALUES
        ('How do you structure integration tests for asynchronous services?', 'low_confidence', 98, now() - interval '90 minutes'),
        ('How do you model indexes and query plans in PostgreSQL?', 'low_total', 95, now() - interval '70 minutes'),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 'low_total', 88, now() - interval '50 minutes')
) AS v(question_title, reason_type, priority, scheduled_for)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM review_queue rq
    WHERE rq.user_id = u.id
      AND rq.question_id = q.id
      AND rq.status = 'pending'
);

INSERT INTO resume_skill_snapshots (
    resume_version_id,
    skill_id,
    skill_name,
    source_text,
    confidence_score,
    is_confirmed,
    created_at,
    updated_at
)
SELECT
    rv.id,
    s.id,
    v.skill_name,
    v.source_text,
    v.confidence_score,
    TRUE,
    now(),
    now()
FROM (
    VALUES
        ('Spring Boot', 'Built backend APIs with Spring Boot and Kotlin', 0.98),
        ('PostgreSQL', 'Optimized PostgreSQL queries and data access patterns', 0.93),
        ('Redis', 'Introduced Redis caching to reduce response times', 0.94),
        ('Kafka', 'Operated Kafka-based asynchronous event flows', 0.88)
) AS v(skill_name, source_text, confidence_score)
JOIN users u ON u.email = 'demo@example.com'
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
LEFT JOIN skills s ON s.name = v.skill_name
WHERE NOT EXISTS (
    SELECT 1
    FROM resume_skill_snapshots rss
    WHERE rss.resume_version_id = rv.id
      AND rss.skill_name = v.skill_name
);

INSERT INTO resume_experience_snapshots (
    resume_version_id,
    project_name,
    summary_text,
    impact_text,
    source_text,
    risk_level,
    display_order,
    is_confirmed,
    created_at,
    updated_at
)
SELECT
    rv.id,
    v.project_name,
    v.summary_text,
    v.impact_text,
    v.source_text,
    v.risk_level,
    v.display_order,
    TRUE,
    now(),
    now()
FROM (
    VALUES
        ('Queue Reliability Platform', 'Built a resilient queue processing pipeline for failure-heavy workloads.', 'Reduced duplicate processing incidents by introducing idempotent handlers and dead-letter policies.', 'Built Kafka-based asynchronous processing with retries and backpressure control.', 'medium', 1),
        ('API Performance Improvement', 'Improved read-heavy API latency through layered caching.', 'Reduced p95 latency by roughly 40 percent during peak traffic windows.', 'Introduced Redis caching to improve response time by 40 percent.', 'high', 2)
) AS v(project_name, summary_text, impact_text, source_text, risk_level, display_order)
JOIN users u ON u.email = 'demo@example.com'
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM resume_experience_snapshots res
    WHERE res.resume_version_id = rv.id
      AND res.display_order = v.display_order
);

INSERT INTO resume_risk_items (
    resume_version_id,
    resume_experience_snapshot_id,
    linked_question_id,
    risk_type,
    title,
    description,
    severity,
    created_at,
    updated_at
)
SELECT
    rv.id,
    res.id,
    q.id,
    v.risk_type,
    v.title,
    v.description,
    v.severity,
    now(),
    now()
FROM (
    VALUES
        (2, 'impact_claim', 'Latency improvement claim', 'You claim a 40 percent latency improvement and should be ready to defend the measurement method.', 'HIGH', 'How did you measure the 40 percent latency improvement?'),
        (1, 'architecture_claim', 'Queue reliability claim', 'You mention retries and backpressure, which is likely to trigger deeper follow-up questions on failure handling.', 'MEDIUM', 'How do you handle poison messages?')
) AS v(display_order, risk_type, title, description, severity, linked_question_title)
JOIN users u ON u.email = 'demo@example.com'
JOIN resumes r ON r.user_id = u.id AND r.title = 'Demo Resume'
JOIN resume_versions rv ON rv.resume_id = r.id AND rv.version_no = 1
JOIN resume_experience_snapshots res ON res.resume_version_id = rv.id AND res.display_order = v.display_order
LEFT JOIN questions q ON q.title = v.linked_question_title
WHERE NOT EXISTS (
    SELECT 1
    FROM resume_risk_items rri
    WHERE rri.resume_version_id = rv.id
      AND rri.title = v.title
);

INSERT INTO answer_analyses (
    answer_attempt_id,
    overall_score,
    depth_score,
    clarity_score,
    accuracy_score,
    example_score,
    tradeoff_score,
    confidence_score,
    strength_summary,
    weakness_summary,
    recommended_next_step,
    created_at
)
SELECT
    aa.id,
    v.overall_score,
    v.depth_score,
    v.clarity_score,
    v.accuracy_score,
    v.example_score,
    v.tradeoff_score,
    v.confidence_score,
    v.strength_summary,
    v.weakness_summary,
    v.recommended_next_step,
    now()
FROM (
    VALUES
        ('Design a resilient queue', 89.0, 86.0, 88.0, 92.0, 83.0, 84.0, 82.0, 'Strong reliability framing with good structure.', 'Could still explain retention and poison-message tradeoffs in more detail.', 'Practice a deeper follow-up on dead-letter queues and replay policies.'),
        ('Explain cache invalidation', 51.0, 46.0, 58.0, 49.0, 44.0, 43.0, 41.0, 'The answer names key concepts like TTL and invalidation events.', 'The answer lacks a concrete consistency model and production example.', 'Re-answer with one concrete cache-aside or write-through example and failure handling.'),
        ('How do you keep queue consumers idempotent?', 87.0, 85.0, 84.0, 89.0, 81.0, 83.0, 80.0, 'You connected idempotency keys and side-effect safety clearly.', 'The storage-expiration tradeoffs could be more explicit.', 'Practice one follow-up on duplicate suppression and key lifecycle.'),
        ('How do you handle poison messages?', 83.0, 81.0, 79.0, 85.0, 76.0, 82.0, 77.0, 'The answer is practical and operator-aware.', 'You can still deepen the alerting and replay safeguards.', 'Add one concrete dead-letter replay workflow.'),
        ('How do you structure integration tests for asynchronous services?', 42.0, 39.0, 47.0, 41.0, 35.0, 38.0, 36.0, 'You acknowledge that asynchronous behavior needs dedicated testing.', 'The answer lacks a concrete test harness and validation strategy.', 'Re-answer with containers, polling assertions, and retry/dead-letter cases.'),
        ('How do you model indexes and query plans in PostgreSQL?', 50.0, 48.0, 53.0, 51.0, 40.0, 44.0, 39.0, 'You at least mention indexes and EXPLAIN as tools.', 'The reasoning is too reactive and does not weigh read/write tradeoffs well.', 'Practice one concrete composite-index example with EXPLAIN output.'),
        ('How would you compare arrays, linked lists, stacks, and queues in practice?', 56.0, 54.0, 60.0, 52.0, 45.0, 43.0, 46.0, 'The definitions are directionally correct.', 'The answer stays abstract and misses real backend tradeoffs.', 'Re-answer with one API or job-processing example per structure.'),
        ('How do you version REST APIs safely?', 85.0, 83.0, 81.0, 87.0, 80.0, 78.0, 79.0, 'Strong compatibility and rollout framing.', 'Contract-testing detail could be stronger.', 'Add one note on schema validation or consumer contracts.'),
        ('How do you define service boundaries in a distributed backend?', 82.0, 80.0, 78.0, 84.0, 79.0, 77.0, 75.0, 'You used sound heuristics for ownership and failure isolation.', 'A concrete migration example would make the answer stronger.', 'Practice one example of splitting a service boundary over time.')
) AS v(question_title, overall_score, depth_score, clarity_score, accuracy_score, example_score, tradeoff_score, confidence_score, strength_summary, weakness_summary, recommended_next_step)
JOIN users u ON u.email = 'demo@example.com'
JOIN questions q ON q.title = v.question_title
JOIN answer_attempts aa ON aa.user_id = u.id AND aa.question_id = q.id AND aa.attempt_no = 1
WHERE NOT EXISTS (
    SELECT 1
    FROM answer_analyses an
    WHERE an.answer_attempt_id = aa.id
);

INSERT INTO skill_category_scores (
    user_id,
    skill_category_id,
    score,
    answered_question_count,
    weak_question_count,
    benchmark_score,
    gap_score,
    calculated_at,
    created_at,
    updated_at
)
SELECT
    u.id,
    sc.id,
    v.score,
    v.answered_question_count,
    v.weak_question_count,
    v.benchmark_score,
    v.gap_score,
    now(),
    now(),
    now()
FROM (
    VALUES
        ('CS', 57.0, 1, 1, 78.0, 21.0),
        ('BACKEND', 84.0, 4, 0, 84.0, 0.0),
        ('DATABASE', 49.0, 2, 2, 76.0, 27.0),
        ('SYSTEM_DESIGN', 81.0, 4, 1, 74.0, -7.0),
        ('ARCHITECTURE', 79.0, 3, 0, 72.0, -7.0),
        ('TESTING', 43.0, 1, 1, 70.0, 27.0)
) AS v(skill_category_code, score, answered_question_count, weak_question_count, benchmark_score, gap_score)
JOIN users u ON u.email = 'demo@example.com'
JOIN skill_categories sc ON sc.code = v.skill_category_code
ON CONFLICT (user_id, skill_category_id) DO UPDATE
SET score = EXCLUDED.score,
    answered_question_count = EXCLUDED.answered_question_count,
    weak_question_count = EXCLUDED.weak_question_count,
    benchmark_score = EXCLUDED.benchmark_score,
    gap_score = EXCLUDED.gap_score,
    calculated_at = EXCLUDED.calculated_at,
    updated_at = now();

COMMIT;
