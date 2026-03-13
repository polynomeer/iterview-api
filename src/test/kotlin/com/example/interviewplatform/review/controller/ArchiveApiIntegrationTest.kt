package com.example.interviewplatform.review.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
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
class ArchiveApiIntegrationTest {
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
            VALUES (1, 'archive-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "archive-user@example.com")}"
    }

    @Test
    fun `archive endpoint returns archived questions only`() {
        val archivedQuestionId = insertQuestion("Archived Question", "HARD")
        val activeQuestionId = insertQuestion("Active Question", "EASY")

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 88, 91,
                4, 0, 'archived', now(), now(), NULL,
                'advanced', now(), now()
            )
            """.trimIndent(),
            archivedQuestionId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 52, 60,
                2, 0, 'in_progress', NULL, now(), now() + interval '1 day',
                'beginner', now(), now()
            )
            """.trimIndent(),
            activeQuestionId,
        )

        mockMvc.perform(get("/api/archive").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].questionId").value(archivedQuestionId))
            .andExpect(jsonPath("$[0].title").value("Archived Question"))
            .andExpect(jsonPath("$[0].bestScore").value(91))
            .andExpect(jsonPath("$[0].sourceType").value("practice"))
            .andExpect(jsonPath("$[0].sourceLabel").value("Practice"))
            .andExpect(jsonPath("$[0].isFollowUp").value(false))
            .andExpect(jsonPath("$[1]").doesNotExist())
    }

    @Test
    fun `archive endpoint omits archived status rows without archived timestamp`() {
        val questionId = insertQuestion("Missing ArchivedAt", "MEDIUM")

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 75, 79,
                2, 0, 'archived', NULL, now(), NULL,
                'intermediate', now(), now()
            )
            """.trimIndent(),
            questionId,
        )

        mockMvc.perform(get("/api/archive").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").doesNotExist())
    }

    @Test
    fun `archive endpoint exposes interview source metadata`() {
        val questionId = insertQuestion("Interview Archived Question", "MEDIUM")
        jdbcTemplate.update(
            """
            INSERT INTO interview_sessions (
                id, user_id, resume_version_id, session_type, status, started_at, ended_at, created_at, updated_at
            ) VALUES (
                77, 1, NULL, 'resume_mock', 'completed', now() - interval '10 minute', now(), now(), now()
            )
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO interview_session_questions (
                id, interview_session_id, question_id, parent_session_question_id, prompt_text, question_source_type,
                order_index, is_follow_up, depth, category_name, tags_json, answer_attempt_id, created_at, updated_at
            ) VALUES (
                88, 77, ?, NULL, 'Interview prompt', 'catalog_seed',
                1, true, 1, 'System Design', '[]', NULL, now(), now()
            )
            """.trimIndent(),
            questionId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, source_type, source_label, source_session_id, source_session_question_id, is_follow_up,
                created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 92, 92,
                3, 0, 'archived', now(), now(), NULL,
                'advanced', 'interview', 'Interview', 77, 88, true,
                now(), now()
            )
            """.trimIndent(),
            questionId,
        )

        mockMvc.perform(get("/api/archive").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].sourceType").value("interview"))
            .andExpect(jsonPath("$[0].sourceLabel").value("Interview"))
            .andExpect(jsonPath("$[0].sourceSessionId").value(77))
            .andExpect(jsonPath("$[0].sourceSessionQuestionId").value(88))
            .andExpect(jsonPath("$[0].isFollowUp").value(true))
    }

    @Test
    fun `archive endpoint includes asked interview session turns even without mastered progress rows`() {
        val firstQuestionId = insertQuestion("Interview opener", "MEDIUM")
        val secondQuestionId = insertQuestion("Interview follow up", "HARD")
        jdbcTemplate.update(
            """
            INSERT INTO interview_sessions (
                id, user_id, resume_version_id, session_type, status, started_at, ended_at, created_at, updated_at
            ) VALUES (
                91, 1, NULL, 'resume_mock', 'completed', now() - interval '20 minute', now(), now(), now()
            )
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO interview_session_questions (
                id, interview_session_id, question_id, parent_session_question_id, prompt_text, question_source_type,
                order_index, is_follow_up, depth, category_name, tags_json, answer_attempt_id, created_at, updated_at
            ) VALUES
                (101, 91, ?, NULL, 'Interview opener prompt', 'ai_opening', 1, false, 0, 'Behavioral', '[]', NULL, now() - interval '5 minute', now() - interval '5 minute'),
                (102, 91, ?, 101, 'Interview follow up prompt', 'ai_follow_up', 2, true, 1, 'Behavioral', '[]', NULL, now() - interval '4 minute', now() - interval '4 minute')
            """.trimIndent(),
            firstQuestionId,
            secondQuestionId,
        )

        mockMvc.perform(get("/api/archive").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].questionId").value(secondQuestionId))
            .andExpect(jsonPath("$[0].sourceType").value("interview"))
            .andExpect(jsonPath("$[0].sourceSessionId").value(91))
            .andExpect(jsonPath("$[0].sourceSessionQuestionId").value(102))
            .andExpect(jsonPath("$[0].isFollowUp").value(true))
            .andExpect(jsonPath("$[1].questionId").value(firstQuestionId))
            .andExpect(jsonPath("$[1].sourceType").value("interview"))
            .andExpect(jsonPath("$[1].sourceSessionId").value(91))
            .andExpect(jsonPath("$[1].sourceSessionQuestionId").value(101))
            .andExpect(jsonPath("$[1].isFollowUp").value(false))
    }

    private fun insertQuestion(title: String, difficulty: String): Long {
        val categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'System Design'", Long::class.java)
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
