package com.example.interviewplatform.skill.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
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
class SkillApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenService: TokenService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var authHeader: String

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'skill-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_profiles (user_id, nickname, job_role_id, years_of_experience, avg_score, archived_question_count, answer_visibility_default, created_at, updated_at)
            SELECT 1, 'skill-user', jr.id, 6, 70, 0, 'private', now(), now()
            FROM job_roles jr
            WHERE jr.name = 'Backend Engineer'
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "skill-user@example.com")}"
    }

    @Test
    fun `radar gaps and progress derive from persisted answer data`() {
        val backendCategoryId = insertSkillCategory("BACKEND", "Backend", 1)
        val databaseCategoryId = insertSkillCategory("DATABASE", "Database", 2)
        val redisSkillId = insertSkill("Redis", backendCategoryId)
        val postgresSkillId = insertSkill("PostgreSQL", databaseCategoryId)
        insertBenchmark("SENIOR", backendCategoryId, 84.0)
        insertBenchmark("SENIOR", databaseCategoryId, 76.0)

        val systemDesignCategoryId = idByName("categories", "name", "System Design")
        val backendQuestionId = insertQuestion(systemDesignCategoryId, "Backend skill question")
        val databaseQuestionId = insertQuestion(systemDesignCategoryId, "Database skill question")
        jdbcTemplate.update(
            "INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at) VALUES (?, ?, 1.0, now())",
            backendQuestionId,
            redisSkillId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at) VALUES (?, ?, 1.0, now())",
            databaseQuestionId,
            postgresSkillId,
        )

        insertAttemptWithAnalysis(backendQuestionId, 1, 88.0, 82.0, "archived", 2)
        insertAttemptWithAnalysis(databaseQuestionId, 2, 52.0, 41.0, "retry_pending", 1)

        val radarResponse = mockMvc.perform(get("/api/skills/radar").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val radarJson = objectMapper.readTree(radarResponse)
        val radarByCode = radarJson.get("categories").associateBy { it.get("categoryCode").asText() }
        assertEquals(84.0, radarByCode.getValue("BACKEND").get("benchmarkScore").asDouble())
        assertEquals(76.0, radarByCode.getValue("DATABASE").get("benchmarkScore").asDouble())

        val gapResponse = mockMvc.perform(get("/api/skills/gaps").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val gapsJson = objectMapper.readTree(gapResponse)
        assertTrue(gapsJson.isArray)
        assertTrue(gapsJson.first().get("gapScore").isNumber)

        val progressResponse = mockMvc.perform(get("/api/skills/progress").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val progressJson = objectMapper.readTree(progressResponse)
        val progressByCode = progressJson.associateBy { it.get("categoryCode").asText() }
        assertEquals(1, progressByCode.getValue("BACKEND").get("answeredQuestionCount").asInt())
        assertEquals(1, progressByCode.getValue("DATABASE").get("weakQuestionCount").asInt())

        val persistedRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM skill_category_scores WHERE user_id = 1",
            Int::class.java,
        )
        assertEquals(radarJson.get("categories").size(), persistedRows)
    }

    private fun insertSkillCategory(code: String, name: String, displayOrder: Int): Long {
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
        return idByName("skill_categories", "code", code)
    }

    private fun insertSkill(name: String, categoryId: Long): Long {
        jdbcTemplate.update(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            ON CONFLICT (name) DO UPDATE
            SET skill_category_id = EXCLUDED.skill_category_id,
                description = EXCLUDED.description,
                updated_at = now()
            """.trimIndent(),
            categoryId,
            name,
            "$name description",
        )
        return idByName("skills", "name", name)
    }

    private fun insertBenchmark(experienceBandCode: String, categoryId: Long, benchmarkScore: Double) {
        val jobRoleId = idByName("job_roles", "name", "Backend Engineer")
        jdbcTemplate.update(
            """
            INSERT INTO career_benchmarks (job_role_id, experience_band_code, skill_category_id, benchmark_score, created_at, updated_at)
            VALUES (?, ?, ?, ?, now(), now())
            ON CONFLICT (job_role_id, experience_band_code, skill_category_id) DO UPDATE
            SET benchmark_score = EXCLUDED.benchmark_score,
                updated_at = now()
            """.trimIndent(),
            jobRoleId,
            experienceBandCode,
            categoryId,
            benchmarkScore,
        )
    }

    private fun insertQuestion(categoryId: Long, title: String): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO questions (
            author_user_id, category_id, title, body, question_type, difficulty_level,
            source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
        ) VALUES (
            NULL, ?, ?, 'Body', 'technical', 'MEDIUM', 'catalog', 'approved', 'public', 300, true, now(), now()
        ) RETURNING id
        """.trimIndent(),
        Long::class.java,
        categoryId,
        title,
    )

    private fun insertAttemptWithAnalysis(
        questionId: Long,
        attemptNo: Int,
        totalScore: Double,
        confidenceScore: Double,
        currentStatus: String,
        daysAgo: Long,
    ) {
        val submittedAt = "now() - interval '$daysAgo day'"
        val answerAttemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (
                user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at
            ) VALUES (
                1, ?, NULL, NULL, ?, 'text', 'Answer with metrics and tradeoffs', $submittedAt, $submittedAt
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
            attemptNo,
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_scores (
                answer_attempt_id, total_score, structure_score, specificity_score, technical_accuracy_score,
                role_fit_score, company_fit_score, communication_score, evaluation_result, evaluated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, now()
            )
            """.trimIndent(),
            answerAttemptId,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            if (totalScore >= 60.0) "PASS" else "FAIL",
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_analyses (
                answer_attempt_id, overall_score, depth_score, clarity_score, accuracy_score, example_score,
                tradeoff_score, confidence_score, strength_summary, weakness_summary, recommended_next_step, created_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, 'strength', 'weakness', 'next step', now()
            )
            """.trimIndent(),
            answerAttemptId,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            totalScore,
            confidenceScore,
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score, total_attempt_count,
                unanswered_count, current_status, archived_at, last_answered_at, next_review_at, mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, ?, ?, ?, ?, 1, 0, ?, NULL, now(), now() + interval '1 day', 'intermediate', now(), now()
            )
            """.trimIndent(),
            questionId,
            answerAttemptId,
            answerAttemptId,
            totalScore,
            totalScore,
            currentStatus,
        )
    }

    private fun idByName(table: String, column: String, value: String): Long = jdbcTemplate.queryForObject(
        "SELECT id FROM $table WHERE $column = ?",
        Long::class.java,
        value,
    )
}
