package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.auth.service.TokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class HomeApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var authHeader: String

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
        jdbcTemplate.update("DELETE FROM learning_materials")
        jdbcTemplate.update("DELETE FROM resume_versions")
        jdbcTemplate.update("DELETE FROM resumes")
        jdbcTemplate.update("DELETE FROM user_target_companies")
        jdbcTemplate.update("DELETE FROM questions")
        jdbcTemplate.update("DELETE FROM users WHERE id = 1")

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'home-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "home-user@example.com")}"
    }

    @Test
    fun `retry-first behavior selects retry item as todayQuestion`() {
        val retryQuestionId = insertQuestion("Retry First", "MEDIUM", true)
        val fallbackQuestionId = insertQuestion("Fallback", "EASY", true)

        val attemptId = insertAttempt(retryQuestionId)
        val queueId = insertPendingReview(retryQuestionId, attemptId, 95, "now() - interval '1 hour'")

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todayQuestion.questionId").value(retryQuestionId))
            .andExpect(jsonPath("$.todayQuestion.cardType").value("retry"))
            .andExpect(jsonPath("$.retryQuestions[0].questionId").doesNotExist())

        val cardCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_cards WHERE user_id = 1 AND card_date = current_date",
            Int::class.java,
        )
        assertEquals(1, cardCount)
        assertTrue(queueId > 0)
        assertTrue(fallbackQuestionId > 0)
    }

    @Test
    fun `home payload has no duplicate questions between today and retry sections`() {
        val q1 = insertQuestion("Retry Main", "MEDIUM", true)
        val q2 = insertQuestion("Retry Follow-up", "HARD", true)

        val a1 = insertAttempt(q1)
        val a2 = insertAttempt(q2)
        insertPendingReview(q1, a1, 100, "now() - interval '2 hour'")
        insertPendingReview(q2, a2, 80, "now() - interval '1 hour'")

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todayQuestion.questionId").value(q1))
            .andExpect(jsonPath("$.retryQuestions[0].questionId").value(q2))
            .andExpect(jsonPath("$.retryQuestions[1]").doesNotExist())
    }

    @Test
    fun `archived questions are excluded from retry selection`() {
        val archivedRetryId = insertQuestion("Archived Retry", "HARD", true)
        val normalRetryId = insertQuestion("Normal Retry", "MEDIUM", true)

        val archivedAttempt = insertAttempt(archivedRetryId)
        val normalAttempt = insertAttempt(normalRetryId)

        insertPendingReview(archivedRetryId, archivedAttempt, 100, "now() - interval '3 hour'")
        insertPendingReview(normalRetryId, normalAttempt, 90, "now() - interval '2 hour'")

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 80, 85,
                2, 0, 'archived', now(), now(), NULL,
                'advanced', now(), now()
            )
            """.trimIndent(),
            archivedRetryId,
        )

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todayQuestion.questionId").value(normalRetryId))
            .andExpect(jsonPath("$.retryQuestions[0]").doesNotExist())
    }

    @Test
    fun `fallback selection used when no retry items exist`() {
        val companyId = jdbcTemplate.queryForObject(
            "SELECT id FROM companies WHERE normalized_name = 'amazon'",
            Long::class.java,
        )

        val targetQuestion = insertQuestion("Company Matched", "MEDIUM", true)
        val otherQuestion = insertQuestion("General Question", "EASY", true)

        jdbcTemplate.update(
            "INSERT INTO user_target_companies (user_id, company_id, priority_order, created_at) VALUES (1, ?, 1, now())",
            companyId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.90, true, false, now())
            """.trimIndent(),
            targetQuestion,
            companyId,
        )

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todayQuestion.questionId").value(targetQuestion))
            .andExpect(jsonPath("$.todayQuestion.cardType").value("daily"))
            .andExpect(jsonPath("$.retryQuestions[0]").doesNotExist())
            .andExpect(jsonPath("$.summaryStats.retryQuestionCount").value(0))

        val ids = jdbcTemplate.queryForList(
            "SELECT question_id FROM daily_cards WHERE user_id = 1 AND card_date = current_date ORDER BY created_at ASC",
            Long::class.java,
        )
        assertEquals(listOf(targetQuestion), ids)
        assertTrue(otherQuestion > 0)
    }

    @Test
    fun `home summary stats include pending and archived counts`() {
        val qPending = insertQuestion("Pending Retry", "MEDIUM", true)
        val qArchived = insertQuestion("Archived Entry", "EASY", true)

        insertPendingReview(qPending, insertAttempt(qPending), 90, "now() - interval '1 hour'")
        insertPendingReview(qArchived, insertAttempt(qArchived), 70, "now() - interval '1 hour'")

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 90, 92,
                3, 0, 'archived', now(), now(), NULL,
                'advanced', now(), now()
            )
            """.trimIndent(),
            qArchived,
        )

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summaryStats.pendingReviewCount").value(1))
            .andExpect(jsonPath("$.summaryStats.archivedQuestionCount").value(1))
    }

    private fun insertQuestion(title: String, difficulty: String, active: Boolean): Long {
        val categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'System Design'", Long::class.java)
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                author_user_id, category_id, title, body, question_type, difficulty_level,
                source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (
                NULL, ?, ?, 'Body', 'technical', ?,
                'catalog', 'approved', 'public', 300, ?, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
            difficulty,
            active,
        )
    }

    private fun insertAttempt(questionId: Long): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO answer_attempts (user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at)
        VALUES (1, ?, NULL, NULL, 1, 'text', 'answer', now(), now())
        RETURNING id
        """.trimIndent(),
        Long::class.java,
        questionId,
    )

    private fun insertPendingReview(questionId: Long, attemptId: Long, priority: Int, scheduledExpr: String): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO review_queue (user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at)
            VALUES (1, ?, ?, 'low_score', ?, $scheduledExpr, 'pending', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
            attemptId,
            priority,
        )
}
