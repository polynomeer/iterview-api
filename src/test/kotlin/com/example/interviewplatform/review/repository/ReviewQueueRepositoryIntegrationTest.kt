package com.example.interviewplatform.review.repository

import com.example.interviewplatform.support.TestDatabaseCleaner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ReviewQueueRepositoryIntegrationTest {
    @Autowired
    private lateinit var reviewQueueRepository: ReviewQueueRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'repo-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
    }

    @Test
    fun `pending review query orders by scheduled time then priority and excludes future rows`() {
        val q1 = insertQuestion("Earlier lower priority")
        val q2 = insertQuestion("Later higher priority")
        val q3 = insertQuestion("Same time higher priority")
        val q4 = insertQuestion("Future question")

        insertQueue(q1, 10, "'2026-03-09T08:00:00Z'")
        insertQueue(q2, 20, "'2026-03-09T09:00:00Z'")
        insertQueue(q3, 90, "'2026-03-09T09:00:00Z'")
        insertQueue(q4, 99, "'2026-03-09T11:00:00Z'")

        val results = reviewQueueRepository
            .findByUserIdAndStatusAndScheduledForLessThanEqualOrderByScheduledForAscPriorityDesc(
                1L,
                "pending",
                Instant.parse("2026-03-09T10:00:00Z"),
            )

        assertEquals(listOf(q1, q3, q2), results.map { it.questionId })
    }

    @Test
    fun `update pending retry refreshes reason priority and schedule`() {
        val questionId = insertQuestion("Update pending retry")
        val queueId = insertQueue(questionId, 60, "now() - interval '3 hour'")
        val replacementAttemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at)
            VALUES (1, ?, NULL, NULL, 2, 'text', 'replacement attempt', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
        )
        val updatedAt = Instant.parse("2026-03-09T00:00:00Z")
        val scheduledFor = Instant.parse("2026-03-12T00:00:00Z")

        val updatedRows = reviewQueueRepository.updatePendingRetry(
            userId = 1L,
            questionId = questionId,
            status = "pending",
            triggerAnswerAttemptId = replacementAttemptId,
            reasonType = "low_total",
            priority = 100,
            scheduledFor = scheduledFor,
            updatedAt = updatedAt,
        )

        assertEquals(1, updatedRows)

        val persisted = reviewQueueRepository.findById(queueId).orElseThrow()
        assertEquals(replacementAttemptId, persisted.triggerAnswerAttemptId)
        assertEquals("low_total", persisted.reasonType)
        assertEquals(100, persisted.priority)
        assertEquals(scheduledFor, persisted.scheduledFor)
        assertEquals(updatedAt, persisted.updatedAt)
    }

    private fun insertQuestion(title: String): Long {
        val categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'System Design'", Long::class.java)
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                author_user_id, category_id, title, body, question_type, difficulty_level,
                source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (
                NULL, ?, ?, 'Body', 'technical', 'MEDIUM',
                'catalog', 'approved', 'public', 300, true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
        )
    }

    private fun insertQueue(questionId: Long, priority: Int, scheduledExpr: String): Long {
        val attemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at)
            VALUES (1, ?, NULL, NULL, 1, 'text', 'attempt', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
        )

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO review_queue (user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at)
            VALUES (1, ?, ?, 'weak_dimension', ?, $scheduledExpr, 'pending', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
            attemptId,
            priority,
        )
    }
}
