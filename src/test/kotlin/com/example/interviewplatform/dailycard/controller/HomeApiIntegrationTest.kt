package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.auth.service.TokenService
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
    fun `home returns expected payload and separates daily and retry questions`() {
        val mainQuestionId = insertQuestion("Main Daily Question", "MEDIUM")
        val retryQuestionId = insertQuestion("Retry Question", "HARD")

        val mainCardId = jdbcTemplate.queryForObject(
            """
            INSERT INTO daily_cards (user_id, question_id, card_date, card_type, source_reason, status, delivered_at, opened_at, created_at)
            VALUES (1, ?, current_date, 'daily', 'recommendation', 'new', now(), NULL, now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            mainQuestionId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO daily_cards (user_id, question_id, card_date, card_type, source_reason, status, delivered_at, opened_at, created_at)
            VALUES (1, ?, current_date, 'retry', 'retry_queue', 'new', now(), NULL, now())
            """.trimIndent(),
            retryQuestionId,
        )

        val attemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at)
            VALUES (1, ?, NULL, NULL, 1, 'text', 'previous answer', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            retryQuestionId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO review_queue (user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at)
            VALUES (1, ?, ?, 'low_score', 90, now() - interval '1 hour', 'pending', now(), now())
            """.trimIndent(),
            retryQuestionId,
            attemptId,
        )

        val materialId = jdbcTemplate.queryForObject(
            """
            INSERT INTO learning_materials (title, material_type, content_text, content_url, source_name, created_at, updated_at)
            VALUES ('Retry guide', 'article', NULL, 'https://example.com/retry', 'docs', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )
        jdbcTemplate.update(
            "INSERT INTO question_learning_materials (question_id, learning_material_id, relevance_score, created_at) VALUES (?, ?, 0.95, now())",
            retryQuestionId,
            materialId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, NULL, 82,
                2, 0, 'archived', now(), now(), NULL,
                'advanced', now(), now()
            )
            """.trimIndent(),
            mainQuestionId,
        )

        mockMvc.perform(get("/api/home").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.todayQuestion.dailyCardId").value(mainCardId))
            .andExpect(jsonPath("$.todayQuestion.questionId").value(mainQuestionId))
            .andExpect(jsonPath("$.retryQuestions[0].questionId").value(retryQuestionId))
            .andExpect(jsonPath("$.retryQuestions[0].priority").value(90))
            .andExpect(jsonPath("$.learningMaterials[0].title").value("Retry guide"))
            .andExpect(jsonPath("$.summaryStats.dailyQuestionCount").value(1))
            .andExpect(jsonPath("$.summaryStats.retryQuestionCount").value(1))
            .andExpect(jsonPath("$.summaryStats.pendingReviewCount").value(1))
            .andExpect(jsonPath("$.summaryStats.archivedQuestionCount").value(1))
    }

    private fun insertQuestion(title: String, difficulty: String): Long {
        val categoryId = jdbcTemplate.queryForObject(
            "SELECT id FROM categories WHERE name = 'System Design'",
            Long::class.java,
        )

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                author_user_id, category_id, title, body, question_type, difficulty_level,
                source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (
                NULL, ?, ?, 'Body', 'technical', ?,
                'catalog', 'approved', 'public', 300, true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
            difficulty,
        )
    }
}
