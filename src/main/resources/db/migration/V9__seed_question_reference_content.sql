INSERT INTO learning_materials (
    title,
    material_type,
    content_text,
    content_url,
    source_name,
    description,
    difficulty_level,
    estimated_minutes,
    is_official,
    display_order_hint,
    created_at,
    updated_at
)
SELECT
    seed.title,
    seed.material_type,
    seed.content_text,
    seed.content_url,
    seed.source_name,
    seed.description,
    seed.difficulty_level,
    seed.estimated_minutes,
    seed.is_official,
    seed.display_order_hint,
    now(),
    now()
FROM (
    VALUES
        (
            'Idempotency Keys in Distributed Systems',
            'article',
            'Use client-generated keys, durable deduplication, and expiry windows to keep retries safe.',
            'https://example.com/idempotency-keys',
            'Iterview Editorial',
            'Practical guide to deduplicating retried work in queue consumers.',
            'intermediate',
            10,
            true,
            1
        ),
        (
            'Dead-Letter Queue Operating Guide',
            'guide',
            'Cover retry policies, poison message thresholds, alerting, and replay strategy.',
            'https://example.com/dlq-guide',
            'Iterview Editorial',
            'Operational checklist for poison-message handling and replay.',
            'intermediate',
            12,
            true,
            2
        ),
        (
            'Cache-Aside Failure Modes',
            'article',
            'Compare stale reads, thundering herds, write skew, and mitigation patterns.',
            'https://example.com/cache-aside-failures',
            'Iterview Editorial',
            'Tradeoff summary for cache-aside and invalidation strategies.',
            'advanced',
            14,
            true,
            1
        )
) AS seed(
    title,
    material_type,
    content_text,
    content_url,
    source_name,
    description,
    difficulty_level,
    estimated_minutes,
    is_official,
    display_order_hint
)
WHERE NOT EXISTS (
    SELECT 1
    FROM learning_materials lm
    WHERE lm.title = seed.title
);

INSERT INTO question_learning_materials (
    question_id,
    learning_material_id,
    relevance_score,
    display_order,
    relationship_type,
    label_override,
    created_at
)
SELECT q.id, lm.id, seed.relevance_score, seed.display_order, seed.relationship_type, seed.label_override, now()
FROM (
    VALUES
        ('How do you keep queue consumers idempotent?', 'Idempotency Keys in Distributed Systems', 0.95, 1, 'prerequisite', 'Start with idempotency fundamentals'),
        ('How do you handle poison messages?', 'Dead-Letter Queue Operating Guide', 0.96, 1, 'deep_dive', 'DLQ operations guide'),
        ('What are cache-aside tradeoffs?', 'Cache-Aside Failure Modes', 0.93, 1, 'deep_dive', 'Cache-aside tradeoff guide')
) AS seed(question_title, material_title, relevance_score, display_order, relationship_type, label_override)
JOIN questions q ON q.title = seed.question_title
JOIN learning_materials lm ON lm.title = seed.material_title
WHERE NOT EXISTS (
    SELECT 1
    FROM question_learning_materials qlm
    WHERE qlm.question_id = q.id
      AND qlm.learning_material_id = lm.id
);

INSERT INTO question_reference_answers (
    question_id,
    title,
    answer_text,
    answer_format,
    source_type,
    target_role_id,
    company_id,
    is_official,
    display_order,
    created_at,
    updated_at
)
SELECT
    q.id,
    seed.title,
    seed.answer_text,
    seed.answer_format,
    seed.source_type,
    jr.id,
    c.id,
    seed.is_official,
    seed.display_order,
    now(),
    now()
FROM (
    VALUES
        (
            'How do you keep queue consumers idempotent?',
            'Strong backend answer',
            'I would first define the failure mode: the broker can redeliver the same message after a timeout, crash, or ambiguous acknowledgement. To keep the consumer idempotent, I would assign each business operation an idempotency key, persist that key in durable storage with the final outcome, and make the handler check that store before applying side effects. For writes, I would prefer a unique constraint or upsert so the deduplication rule is enforced by the database, not only in memory. I would then explain retention strategy, replay windows, and how I would monitor duplicate-key hits so we can spot upstream retry storms.',
            'full_answer',
            'editorial',
            'Backend Engineer',
            NULL,
            true,
            1
        ),
        (
            'How do you handle poison messages?',
            'Operationally grounded answer',
            'I would separate transient failures from poison messages. A transient failure should retry with backoff and jitter, but once a message exceeds a threshold or fails due to a deterministic schema or validation error, I would move it to a dead-letter queue. The answer should explain what metadata is preserved for debugging, what alerts are emitted, who owns replay, and how we prevent an automated replay loop from reintroducing the same bad message.',
            'full_answer',
            'editorial',
            'Backend Engineer',
            NULL,
            true,
            1
        ),
        (
            'What are cache-aside tradeoffs?',
            'Tradeoff-focused answer',
            'I would start by saying cache-aside is simple and works well for read-heavy traffic, but it shifts consistency responsibility to the application. The upside is flexibility and easy cache eviction. The downside is stale reads, cold-cache latency, and thundering herd risk during expiration. A strong answer should compare it with write-through or write-behind, then explain mitigations such as request coalescing, jittered TTLs, and explicit invalidation on writes.',
            'full_answer',
            'editorial',
            'Backend Engineer',
            NULL,
            true,
            1
        )
) AS seed(question_title, title, answer_text, answer_format, source_type, role_name, company_name, is_official, display_order)
JOIN questions q ON q.title = seed.question_title
LEFT JOIN job_roles jr ON jr.name = seed.role_name
LEFT JOIN companies c ON c.name = seed.company_name
WHERE NOT EXISTS (
    SELECT 1
    FROM question_reference_answers qra
    WHERE qra.question_id = q.id
      AND qra.title = seed.title
);
