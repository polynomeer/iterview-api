package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
class InterviewSessionApiIntegrationTest {
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
        TestDatabaseCleaner.reset(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'session-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "session-user@example.com")}"
    }

    @Test
    fun `resume mock session selects resume matched questions`() {
        val categoryId = insertCategory("Backend")
        val questionId = insertQuestion("Explain Spring transaction boundaries", categoryId)
        val skillCategoryId = insertSkillCategory("backend_engineering", "Backend Engineering")
        val skillId = insertSkill(skillCategoryId, "Spring Boot")
        val resumeVersionId = insertResumeVersion()
        jdbcTemplate.update(
            """
            INSERT INTO resume_skill_snapshots (
                resume_version_id, skill_id, skill_name, source_text, confidence_score, is_confirmed, created_at, updated_at
            ) VALUES (?, ?, 'Spring Boot', 'Built APIs with Spring Boot', 91.0, true, now(), now())
            """.trimIndent(),
            resumeVersionId,
            skillId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at)
            VALUES (?, ?, 0.95, now())
            """.trimIndent(),
            questionId,
            skillId,
        )

        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionType").value("resume_mock"))
            .andExpect(jsonPath("$.resumeVersionId").value(resumeVersionId))
            .andExpect(jsonPath("$.currentQuestion.questionId").value(questionId))
            .andExpect(jsonPath("$.questions[0].status").value("current"))
    }

    @Test
    fun `review mock session prioritizes pending review questions`() {
        val categoryId = insertCategory("Distributed Systems")
        val questionId = insertQuestion("Explain retry backoff", categoryId)
        val answerAttemptId = insertAnswerAttempt(questionId)
        jdbcTemplate.update(
            """
            INSERT INTO review_queue (
                user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at
            ) VALUES (1, ?, ?, 'low_score', 9, now() - interval '1 day', 'pending', now(), now())
            """.trimIndent(),
            questionId,
            answerAttemptId,
        )

        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "review_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.questionId").value(questionId))
            .andExpect(jsonPath("$.questions[0].status").value("current"))
    }

    @Test
    fun `session answers progress and complete session`() {
        val categoryId = insertCategory("Architecture")
        val firstQuestionId = insertQuestion("Design idempotent jobs", categoryId)
        val secondQuestionId = insertQuestion("Explain queue backpressure", categoryId)

        val sessionResponse = createTopicSession(listOf(firstQuestionId, secondQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()
        val secondSessionQuestionId = sessionResponse.get("questions")[1].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to firstSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "I improved reliability by 30 percent because we added idempotency keys and explicit dedupe checks.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.nextQuestion.id").value(secondSessionQuestionId))
            .andExpect(jsonPath("$.summary.answeredQuestions").value(1))

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.currentQuestion.id").value(secondSessionQuestionId))

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to secondSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "We controlled producer rate, bounded queue depth, and scaled consumers based on lag metrics.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.nextQuestion").value(nullValue()))
            .andExpect(jsonPath("$.summary.answeredQuestions").value(2))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.currentQuestion").value(nullValue()))
            .andExpect(jsonPath("$.questions[0].status").value("answered"))
            .andExpect(jsonPath("$.questions[1].status").value("answered"))
            .andExpect(jsonPath("$.summary.averageScore").isNumber)
    }

    @Test
    fun `create session rejects unsupported type`() {
        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "voice_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `session answer validates required session question id`() {
        val categoryId = insertCategory("Validation")
        val questionId = insertQuestion("Explain validation boundaries", categoryId)
        val sessionResponse = createTopicSession(listOf(questionId))
        val sessionId = sessionResponse.get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to "Missing session question id",
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    private fun createTopicSession(questionIds: List<Long>): JsonNode {
        val response = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "topic_mock",
                            "questionCount" to questionIds.size,
                            "seedQuestionIds" to questionIds,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)
    }

    private fun insertCategory(name: String): Long {
        return jdbcTemplate.queryForObject(
            "INSERT INTO categories (name, created_at) VALUES (?, now()) RETURNING id",
            Long::class.java,
            "$name-${System.nanoTime()}",
        )
    }

    private fun insertQuestion(title: String, categoryId: Long): Long {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                category_id, title, body, question_type, difficulty_level, source_type, quality_status, visibility,
                expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, 'behavioral', 'MEDIUM', 'seed', 'approved', 'private', 180, true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
            "$title body",
        )
    }

    private fun insertSkillCategory(code: String, name: String): Long {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO skill_categories (code, name, display_order, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            "$code-${System.nanoTime()}",
            "$name-${System.nanoTime()}",
            999,
        )
    }

    private fun insertSkill(skillCategoryId: Long, name: String): Long {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            VALUES (?, ?, 'Skill for tests', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            skillCategoryId,
            "$name-${System.nanoTime()}",
        )
    }

    private fun insertResumeVersion(): Long {
        val resumeId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resumes (id, user_id, title, is_primary, created_at, updated_at)
            VALUES (DEFAULT, 1, 'Session Resume', true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (
                resume_id, version_no, file_url, raw_text, parsed_json, summary_text, is_active, uploaded_at, created_at,
                file_name, file_type, parsing_status
            ) VALUES (?, 1, 'https://files.example.com/session.pdf', 'resume text', '{}', 'summary', true, now(), now(),
                'session.pdf', 'application/pdf', 'completed')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        )
    }

    private fun insertAnswerAttempt(questionId: Long): Long {
        val answerAttemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (
                user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at
            ) VALUES (1, ?, NULL, NULL, 1, 'text', 'baseline answer', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_scores (
                answer_attempt_id, total_score, structure_score, specificity_score, technical_accuracy_score, role_fit_score,
                company_fit_score, communication_score, evaluation_result, evaluated_at
            ) VALUES (?, 45, 45, 45, 45, 45, 45, 45, 'FAIL', now())
            """.trimIndent(),
            answerAttemptId,
        )
        return answerAttemptId
    }
}
