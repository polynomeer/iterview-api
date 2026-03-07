package com.example.interviewplatform.feed.controller

import com.example.interviewplatform.auth.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertFalse
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
class FeedApiIntegrationTest {
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
        jdbcTemplate.update("DELETE FROM user_target_companies")
        jdbcTemplate.update("DELETE FROM user_question_progress")
        jdbcTemplate.update("DELETE FROM question_learning_materials")
        jdbcTemplate.update("DELETE FROM question_roles")
        jdbcTemplate.update("DELETE FROM question_companies")
        jdbcTemplate.update("DELETE FROM question_tags")
        jdbcTemplate.update("DELETE FROM questions")
        jdbcTemplate.update("DELETE FROM users WHERE id = 1")

        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'feed-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "feed-user@example.com")}"
    }

    @Test
    fun `feed returns expected section card shape`() {
        val amazonId = idByColumn("companies", "normalized_name", "amazon")
        val googleId = idByColumn("companies", "normalized_name", "google")
        val tagId = idByColumn("tags", "name", "scalability")

        val qPopular = insertQuestion("Popular Question", "MEDIUM", true)
        val qTrending = insertQuestion("Trending Question", "HARD", true)

        jdbcTemplate.update(
            "INSERT INTO user_target_companies (user_id, company_id, priority_order, created_at) VALUES (1, ?, 1, now())",
            amazonId,
        )

        jdbcTemplate.update("INSERT INTO question_tags (question_id, tag_id, created_at) VALUES (?, ?, now())", qPopular, tagId)

        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.95, true, false, now())
            """.trimIndent(),
            qPopular,
            amazonId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.70, false, true, now())
            """.trimIndent(),
            qTrending,
            googleId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 74, 81,
                7, 0, 'in_progress', NULL, now(), now() + interval '2 days',
                'intermediate', now(), now()
            )
            """.trimIndent(),
            qPopular,
        )

        mockMvc.perform(get("/api/feed").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.popular[0].questionId").value(qPopular))
            .andExpect(jsonPath("$.popular[0].title").value("Popular Question"))
            .andExpect(jsonPath("$.popular[0].category").value("System Design"))
            .andExpect(jsonPath("$.popular[0].difficulty").value("MEDIUM"))
            .andExpect(jsonPath("$.popular[0].relatedCompanies[0].name").value("Amazon"))
            .andExpect(jsonPath("$.popular[0].tags[0]").value("scalability"))
            .andExpect(jsonPath("$.popular[0].userProgressSummary.totalAttemptCount").value(7))
            .andExpect(jsonPath("$.trending[0].questionId").value(qTrending))
            .andExpect(jsonPath("$.companyRelated[0].questionId").value(qPopular))
    }

    @Test
    fun `feed excludes inactive questions from all sections`() {
        val amazonId = idByColumn("companies", "normalized_name", "amazon")
        val activeQuestionId = insertQuestion("Active Feed", "MEDIUM", true)
        val inactiveQuestionId = insertQuestion("Inactive Feed", "HARD", false)

        jdbcTemplate.update(
            "INSERT INTO user_target_companies (user_id, company_id, priority_order, created_at) VALUES (1, ?, 1, now())",
            amazonId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.75, true, true, now()), (?, ?, 0.99, true, true, now())
            """.trimIndent(),
            activeQuestionId,
            amazonId,
            inactiveQuestionId,
            amazonId,
        )

        val response = mockMvc.perform(get("/api/feed").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val root = objectMapper.readTree(response)
        val ids = mutableSetOf<Long>()
        root.get("popular").forEach { ids.add(it.get("questionId").asLong()) }
        root.get("trending").forEach { ids.add(it.get("questionId").asLong()) }
        root.get("companyRelated").forEach { ids.add(it.get("questionId").asLong()) }

        assertFalse(ids.contains(inactiveQuestionId))
        assertTrue(ids.contains(activeQuestionId))
    }

    @Test
    fun `feed keeps stable sections with limited data`() {
        val questionId = insertQuestion("Only Question", "EASY", true)

        mockMvc.perform(get("/api/feed").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.popular[0].questionId").value(questionId))
            .andExpect(jsonPath("$.trending[0].questionId").value(questionId))
            .andExpect(jsonPath("$.companyRelated[0]").doesNotExist())
    }

    private fun idByColumn(table: String, column: String, value: String): Long = jdbcTemplate.queryForObject(
        "SELECT id FROM $table WHERE $column = ?",
        Long::class.java,
        value,
    )

    private fun insertQuestion(title: String, difficulty: String, isActive: Boolean): Long {
        val categoryId = idByColumn("categories", "name", "System Design")
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                author_user_id, category_id, title, body, question_type, difficulty_level,
                source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (
                NULL, ?, ?, 'Feed body', 'technical', ?,
                'catalog', 'approved', 'public', 300, ?, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
            difficulty,
            isActive,
        )
    }
}
