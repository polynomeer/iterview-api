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
        ('Behavioral Storytelling', 'article', 'https://example.com/storytelling', 'Interview Notes')
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

INSERT INTO question_tags (question_id, tag_id, created_at)
SELECT q.id, t.id, now()
FROM questions q
JOIN tags t ON (
    (q.title = 'Design a resilient queue' AND t.name = 'scalability') OR
    (q.title = 'Explain cache invalidation' AND t.name = 'scalability') OR
    (q.title = 'Tell me about a time you took ownership' AND t.name = 'ownership')
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
        ('Tell me about a time you took ownership', 'Backend Engineer', 0.70)
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
        ('Tell me about a time you took ownership', 'Behavioral Storytelling', 0.90)
) AS v(question_title, material_title, relevance_score)
JOIN questions q ON q.title = v.question_title
JOIN learning_materials lm ON lm.title = v.material_title
ON CONFLICT (question_id, learning_material_id) DO UPDATE
SET relevance_score = EXCLUDED.relevance_score;

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
        ('Explain cache invalidation', 54.0, 58.0, 52.0, 49.0, 55.0, 47.0, 56.0, 'FAIL')
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
        ('Explain cache invalidation', 'improvement', 'medium', 'Add concrete examples', 'Use a production example with TTL, event propagation, or write-through behavior.', 2)
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
        ('Explain cache invalidation', 'retry_pending', NULL::timestamptz, now() + interval '1 day', 'beginner')
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

COMMIT;
