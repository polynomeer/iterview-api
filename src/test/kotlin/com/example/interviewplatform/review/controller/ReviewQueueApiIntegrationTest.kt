package com.example.interviewplatform.review.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
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

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var authHeader: String

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'review-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "review-user@example.com")}"
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

        mockMvc.perform(get("/api/review-queue").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(firstQueueId))
            .andExpect(jsonPath("$[1].id").value(secondQueueId))

        mockMvc.perform(post("/api/review-queue/$firstQueueId/skip").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(firstQueueId))
            .andExpect(jsonPath("$.status").value("skipped"))

        mockMvc.perform(post("/api/review-queue/$secondQueueId/done").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(secondQueueId))
            .andExpect(jsonPath("$.status").value("done"))

        assertStatus(firstQueueId, "skipped")
        assertStatus(secondQueueId, "done")
    }

    @Test
    fun `review queue recalculates priority from analysis confidence and resume risk`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Redis", "BACKEND")

        val questionId = insertQuestion("Resume risk review question")
        val answerAttemptId = insertAttempt(questionId)
        val queueId = insertQueue(questionId, answerAttemptId, 10, "now() - interval '1 hour'")
        val resumeId = jdbcTemplate.queryForObject(
            "INSERT INTO resumes (user_id, title, is_primary, created_at, updated_at) VALUES (1, 'Review Resume', true, now(), now()) RETURNING id",
            Long::class.java,
        )!!
        val resumeVersionId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (
                resume_id, version_no, file_url, file_name, file_type, raw_text, parsed_json, summary_text, parsing_status, is_active, uploaded_at, created_at
            ) VALUES (
                ?, 1, 'https://files.example.com/review.pdf', 'review.pdf', 'pdf', 'raw', '{}', 'summary', 'completed', true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_scores (
                answer_attempt_id, total_score, structure_score, specificity_score, technical_accuracy_score,
                role_fit_score, company_fit_score, communication_score, evaluation_result, evaluated_at
            ) VALUES (?, 52, 52, 52, 52, 52, 52, 52, 'FAIL', now())
            """.trimIndent(),
            answerAttemptId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_analyses (
                answer_attempt_id, overall_score, depth_score, clarity_score, accuracy_score, example_score,
                tradeoff_score, confidence_score, strength_summary, weakness_summary, recommended_next_step, created_at
            ) VALUES (?, 52, 40, 50, 48, 35, 30, 20, 'strength', 'weakness', 'next', now())
            """.trimIndent(),
            answerAttemptId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO resume_risk_items (
                resume_version_id, resume_experience_snapshot_id, linked_question_id, risk_type, title, description, severity, created_at, updated_at
            ) VALUES (?, NULL, ?, 'impact_claim', 'Risk', 'Description', 'HIGH', now(), now())
            """.trimIndent(),
            resumeVersionId,
            questionId,
        )

        mockMvc.perform(get("/api/review-queue").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(queueId))
            .andExpect(jsonPath("$[0].priority").value(org.hamcrest.Matchers.greaterThan(10)))
            .andExpect(jsonPath("$[0].reasonType").value("resume_risk"))
    }

    @Test
    fun `review queue returns not found for missing item`() {
        mockMvc.perform(post("/api/review-queue/999999/skip").header("Authorization", authHeader))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `review queue returns conflict when item already transitioned`() {
        val questionId = insertQuestion("Conflict Question")
        val attemptId = insertAttempt(questionId)
        val queueId = insertQueue(questionId, attemptId, 50, "now() - interval '1 hour'")

        mockMvc.perform(post("/api/review-queue/$queueId/done").header("Authorization", authHeader))
            .andExpect(status().isOk)

        mockMvc.perform(post("/api/review-queue/$queueId/skip").header("Authorization", authHeader))
            .andExpect(status().isConflict)
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

    private fun seedSkillCategory(code: String, name: String, displayOrder: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO skill_categories (code, name, display_order, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            ON CONFLICT (code) DO UPDATE
            SET name = EXCLUDED.name,
                display_order = EXCLUDED.display_order,
                updated_at = now()
            """.trimIndent(),
            code,
            name,
            displayOrder,
        )
    }

    private fun seedSkill(name: String, categoryCode: String) {
        jdbcTemplate.update(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            SELECT sc.id, ?, ?, now(), now()
            FROM skill_categories sc
            WHERE sc.code = ?
            ON CONFLICT (name) DO UPDATE
            SET skill_category_id = EXCLUDED.skill_category_id,
                description = EXCLUDED.description,
                updated_at = now()
            """.trimIndent(),
            name,
            "$name description",
            categoryCode,
        )
    }
}
