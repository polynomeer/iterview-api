package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.support.TestDatabaseCleaner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class QuestionSearchRepositoryIntegrationTest {
    @Autowired
    private lateinit var questionSearchRepository: QuestionSearchRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)
    }

    @Test
    fun `search applies combined filters and excludes inactive questions`() {
        val categoryId = idByName("categories", "name", "System Design")
        val tagId = idByName("tags", "name", "scalability")
        val companyId = idByName("companies", "normalized_name", "amazon")
        val roleId = idByName("job_roles", "name", "Backend Engineer")

        val matchingQuestionId = insertQuestion(
            categoryId = categoryId,
            title = "Design a resilient queue",
            body = "Explain throughput, durability, and backpressure",
            difficulty = "HARD",
            qualityStatus = "approved",
            isActive = true,
        )
        val inactiveQuestionId = insertQuestion(
            categoryId = categoryId,
            title = "Inactive resilient queue",
            body = "Should be filtered out",
            difficulty = "HARD",
            qualityStatus = "approved",
            isActive = false,
        )
        insertQuestion(
            categoryId = categoryId,
            title = "Behavioral conflict story",
            body = "Tell me about a disagreement",
            difficulty = "EASY",
            qualityStatus = "approved",
            isActive = true,
        )

        jdbcTemplate.update("INSERT INTO question_tags (question_id, tag_id, created_at) VALUES (?, ?, now())", matchingQuestionId, tagId)
        jdbcTemplate.update("INSERT INTO question_tags (question_id, tag_id, created_at) VALUES (?, ?, now())", inactiveQuestionId, tagId)
        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.92, true, true, now())
            """.trimIndent(),
            matchingQuestionId,
            companyId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_roles (question_id, job_role_id, relevance_score, created_at) VALUES (?, ?, 0.85, now())",
            matchingQuestionId,
            roleId,
        )

        val results = questionSearchRepository.search(
            QuestionSearchFilter(
                categoryId = categoryId,
                tag = "Scalability",
                companyId = companyId,
                roleId = roleId,
                difficulty = "hard",
                status = "APPROVED",
                search = "throughput",
            ),
        )

        assertEquals(listOf(matchingQuestionId), results.map { it.id })
        assertTrue(results.none { it.id == inactiveQuestionId })
    }

    @Test
    fun `search orders newest first for matching active questions`() {
        val categoryId = idByName("categories", "name", "System Design")

        val olderId = insertQuestion(
            categoryId = categoryId,
            title = "Older queue question",
            body = "Queue design body",
            difficulty = "MEDIUM",
            qualityStatus = "approved",
            isActive = true,
            createdAtExpression = "now() - interval '2 days'",
        )
        val newerId = insertQuestion(
            categoryId = categoryId,
            title = "Newer queue question",
            body = "Queue design body",
            difficulty = "MEDIUM",
            qualityStatus = "approved",
            isActive = true,
            createdAtExpression = "now() - interval '1 day'",
        )

        val results = questionSearchRepository.search(
            QuestionSearchFilter(
                categoryId = categoryId,
                search = "queue design",
            ),
        )

        assertEquals(listOf(newerId, olderId), results.map { it.id })
    }

    private fun idByName(table: String, column: String, value: String): Long = jdbcTemplate.queryForObject(
        "SELECT id FROM $table WHERE $column = ?",
        Long::class.java,
        value,
    )

    private fun insertQuestion(
        categoryId: Long,
        title: String,
        body: String,
        difficulty: String,
        qualityStatus: String,
        isActive: Boolean,
        createdAtExpression: String = "now()",
    ): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO questions (
            author_user_id, category_id, title, body, question_type, difficulty_level,
            source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
        ) VALUES (
            NULL, ?, ?, ?, 'technical', ?,
            'catalog', ?, 'public', 300, ?, $createdAtExpression, $createdAtExpression
        ) RETURNING id
        """.trimIndent(),
        Long::class.java,
        categoryId,
        title,
        body,
        difficulty,
        qualityStatus,
        isActive,
    )
}
