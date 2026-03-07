package com.example.interviewplatform.review.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ReviewQueueApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM answer_feedback_items")
        jdbcTemplate.update("DELETE FROM answer_scores")
        jdbcTemplate.update("DELETE FROM review_queue")
        jdbcTemplate.update("DELETE FROM user_question_progress")
        jdbcTemplate.update("DELETE FROM answer_attempts")
        jdbcTemplate.update("DELETE FROM daily_cards")
        jdbcTemplate.update("DELETE FROM question_learning_materials")
        jdbcTemplate.update("DELETE FROM question_roles")
        jdbcTemplate.update("DELETE FROM question_companies")
        jdbcTemplate.update("DELETE FROM question_tags")
        jdbcTemplate.update("DELETE FROM questions")
        jdbcTemplate.update("DELETE FROM users WHERE id = 1")

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'review-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
    }

    @Test
    fun `review queue returns pending ordered and supports skip done transitions`() {
        val q1 = insertQuestion("Queue Q1")
        val q2 = insertQuestion("Queue Q2")
        val q3 = insertQuestion("Queue Q3")

        val a1 = insertAttempt(q1)
        val a2 = insertAttempt(q2)
        val a3 = insertAttempt(q3)

        val firstQueueId = insertQueue(q1, a1, 10, "now() - interval '2 hour'")
        val secondQueueId = insertQueue(q2, a2, 80, "now() - interval '1 hour'")
        insertQueue(q3, a3, 40, "now() - interval '1 hour'")

        mockMvc.perform(get("/api/review-queue"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(firstQueueId))
            .andExpect(jsonPath("$[1].id").value(secondQueueId))

        mockMvc.perform(post("/api/review-queue/$firstQueueId/skip"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(firstQueueId))
            .andExpect(jsonPath("$.status").value("skipped"))

        mockMvc.perform(post("/api/review-queue/$secondQueueId/done"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(secondQueueId))
            .andExpect(jsonPath("$.status").value("done"))

        assertStatus(firstQueueId, "skipped")
        assertStatus(secondQueueId, "done")
    }

    private fun assertStatus(queueId: Long, expectedStatus: String) {
        val status = jdbcTemplate.queryForObject(
            "SELECT status FROM review_queue WHERE id = ?",
            String::class.java,
            queueId,
        )
        assertEquals(expectedStatus, status)
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

    private fun insertAttempt(questionId: Long): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO answer_attempts (user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at)
        VALUES (1, ?, NULL, NULL, 1, 'text', 'attempt', now(), now())
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        questionId,
    )

    private fun insertQueue(questionId: Long, answerAttemptId: Long, priority: Int, scheduledForExpr: String): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO review_queue (user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at)
        VALUES (1, ?, ?, 'low_score', ?, $scheduledForExpr, 'pending', now(), now())
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        questionId,
        answerAttemptId,
        priority,
    )
}
