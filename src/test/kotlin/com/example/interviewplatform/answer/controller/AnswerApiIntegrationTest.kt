package com.example.interviewplatform.answer.controller

import com.example.interviewplatform.auth.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.nullValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
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
class AnswerApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
        jdbcTemplate.update("DELETE FROM question_learning_materials")
        jdbcTemplate.update("DELETE FROM question_roles")
        jdbcTemplate.update("DELETE FROM question_companies")
        jdbcTemplate.update("DELETE FROM question_tags")
        jdbcTemplate.update("DELETE FROM questions")
        jdbcTemplate.update("DELETE FROM users WHERE id = 1")

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'answer-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "answer-user@example.com")}"
    }

    @Test
    fun `submit creates attempt score feedback and progress`() {
        val questionId = insertQuestion(title = "Explain caching strategy")

        val response = submitAnswer(questionId, "I improved API performance by 22 percent because we reduced remote calls.\n".repeat(4))

        val answerAttemptId = objectMapper.readTree(response).get("answerAttemptId").asLong()

        assertCount("SELECT COUNT(*) FROM answer_attempts WHERE id = ?", 1, answerAttemptId)
        assertCount("SELECT COUNT(*) FROM answer_scores WHERE answer_attempt_id = ?", 1, answerAttemptId)
        assertCount("SELECT COUNT(*) FROM answer_feedback_items WHERE answer_attempt_id = ?", 2, answerAttemptId)
        assertCount("SELECT COUNT(*) FROM user_question_progress WHERE user_id = 1 AND question_id = ?", 1, questionId)

        mockMvc.perform(get("/api/answer-attempts/$answerAttemptId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answerAttempt.id").value(answerAttemptId))
            .andExpect(jsonPath("$.score.totalScore").isNumber)
            .andExpect(jsonPath("$.feedback[0].id").isNumber)
            .andExpect(jsonPath("$.progressSummary.totalAttemptCount").value(1))
    }

    @Test
    fun `progress updates latest and best attempt`() {
        val questionId = insertQuestion(title = "Design retry strategy")

        submitAnswer(questionId, "too short")
        submitAnswer(questionId, "I improved the scheduler by 40 percent because we added score-based backoff and retry windows.\n".repeat(5))

        val latestAttemptNo = jdbcTemplate.queryForObject(
            "SELECT a.attempt_no FROM user_question_progress p JOIN answer_attempts a ON p.latest_answer_attempt_id = a.id WHERE p.user_id = 1 AND p.question_id = ?",
            Int::class.java,
            questionId,
        )
        val bestAttemptNo = jdbcTemplate.queryForObject(
            "SELECT a.attempt_no FROM user_question_progress p JOIN answer_attempts a ON p.best_answer_attempt_id = a.id WHERE p.user_id = 1 AND p.question_id = ?",
            Int::class.java,
            questionId,
        )

        assertEquals(2, latestAttemptNo)
        assertEquals(2, bestAttemptNo)
    }

    @Test
    fun `low score submission creates retry queue entry`() {
        val questionId = insertQuestion(title = "Explain CAP theorem")

        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to "short",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.progressStatus").value("retry_pending"))
            .andExpect(jsonPath("$.nextReviewAt").isNotEmpty)

        assertCount("SELECT COUNT(*) FROM review_queue WHERE user_id = 1 AND question_id = ? AND status = 'pending'", 1, questionId)
    }

    @Test
    fun `archive decision path marks progress archived and clears pending retry`() {
        val questionId = insertQuestion(title = "Design event processing")

        submitAnswer(questionId, "short")

        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to "I improved throughput by 30 percent because we introduced batching and idempotent consumers.\n".repeat(12),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.archiveDecision").value(true))
            .andExpect(jsonPath("$.progressStatus").value("archived"))
            .andExpect(jsonPath("$.nextReviewAt").value(nullValue()))

        assertCount("SELECT COUNT(*) FROM user_question_progress WHERE user_id = 1 AND question_id = ? AND current_status = 'archived'", 1, questionId)
        assertCount("SELECT COUNT(*) FROM review_queue WHERE user_id = 1 AND question_id = ? AND status = 'pending'", 0, questionId)
        assertCount("SELECT COUNT(*) FROM review_queue WHERE user_id = 1 AND question_id = ? AND status = 'done'", 1, questionId)
    }

    @Test
    fun `attempt number increments per user question`() {
        val questionId = insertQuestion(title = "Explain eventual consistency")

        submitAnswer(questionId, "first pass answer")
        submitAnswer(questionId, "second pass answer with more details")

        val attempts = jdbcTemplate.queryForList(
            "SELECT attempt_no FROM answer_attempts WHERE user_id = 1 AND question_id = ? ORDER BY attempt_no ASC",
            Int::class.java,
            questionId,
        )
        assertEquals(listOf(1, 2), attempts)

        mockMvc.perform(get("/api/questions/$questionId/answers").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].attemptNo").value(2))
            .andExpect(jsonPath("$[1].attemptNo").value(1))
    }

    @Test
    fun `retry reschedule updates pending reason type and unanswered count`() {
        val questionId = insertQuestion(title = "Retry reason refresh")

        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "skip",
                            "contentText" to "skipped by user",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.progressStatus").value("retry_pending"))

        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to "short",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.progressStatus").value("retry_pending"))

        val reasonType = jdbcTemplate.queryForObject(
            "SELECT reason_type FROM review_queue WHERE user_id = 1 AND question_id = ? AND status = 'pending'",
            String::class.java,
            questionId,
        )
        val unansweredCount = jdbcTemplate.queryForObject(
            "SELECT unanswered_count FROM user_question_progress WHERE user_id = 1 AND question_id = ?",
            Int::class.java,
            questionId,
        )

        assertEquals("low_total", reasonType)
        assertEquals(1, unansweredCount)
    }

    @Test
    fun `submit rejects resume version owned by another user`() {
        val questionId = insertQuestion(title = "Ownership validation")
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (2, 'resume-owner@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        val resumeId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resumes (user_id, title, is_primary, created_at, updated_at)
            VALUES (2, 'Other user resume', true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )!!
        val resumeVersionId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (resume_id, version_no, file_url, raw_text, parsed_json, summary_text, is_active, uploaded_at, created_at)
            VALUES (?, 1, 'https://files.example.com/other.pdf', 'raw', '{}', 'summary', true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        )

        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "resumeVersionId" to resumeVersionId,
                            "answerMode" to "text",
                            "contentText" to "Ownership check",
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.message").value("Invalid resumeVersionId: $resumeVersionId"))
    }

    private fun submitAnswer(questionId: Long, contentText: String): String {
        val result = mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to contentText,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answerAttemptId").isNumber)
            .andReturn()

        return result.response.contentAsString
    }

    private fun insertQuestion(title: String): Long {
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
                NULL, ?, ?, 'Answer this question', 'technical', 'MEDIUM',
                'catalog', 'approved', 'public', 300, true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
        )
    }

    private fun assertCount(sql: String, expected: Int, vararg args: Any) {
        val actual = jdbcTemplate.queryForObject(sql, Int::class.java, *args)
        assertEquals(expected, actual)
    }
}
