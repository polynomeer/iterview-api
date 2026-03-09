package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DailyCardApiIntegrationTest {
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
            VALUES
                (1, 'daily-open@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now()),
                (2, 'daily-other@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "daily-open@example.com")}"
    }

    @Test
    fun `open daily card marks card opened and is idempotent`() {
        val dailyCardId = insertDailyCard(userId = 1)

        val firstResponse = mockMvc.perform(post("/api/daily-cards/$dailyCardId/open").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(dailyCardId))
            .andExpect(jsonPath("$.status").value("opened"))
            .andExpect(jsonPath("$.openedAt").isNotEmpty)
            .andReturn()

        val firstOpenedAt = objectMapper.readTree(firstResponse.response.contentAsString).get("openedAt").asText()

        mockMvc.perform(post("/api/daily-cards/$dailyCardId/open").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("opened"))
            .andExpect(jsonPath("$.openedAt").value(firstOpenedAt))

        val statusValue = jdbcTemplate.queryForObject(
            "SELECT status FROM daily_cards WHERE id = ?",
            String::class.java,
            dailyCardId,
        )
        assertEquals("opened", statusValue)
    }

    @Test
    fun `open daily card rejects access to another users card`() {
        val dailyCardId = insertDailyCard(userId = 2)

        mockMvc.perform(post("/api/daily-cards/$dailyCardId/open").header("Authorization", authHeader))
            .andExpect(status().isNotFound)
    }

    private fun insertDailyCard(userId: Long): Long {
        val categoryId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'System Design'", Long::class.java)
        val questionId = jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                author_user_id, category_id, title, body, question_type, difficulty_level,
                source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (
                NULL, ?, 'Open this card', 'Body', 'technical', 'MEDIUM',
                'catalog', 'approved', 'public', 300, true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
        )

        return jdbcTemplate.queryForObject(
            """
            INSERT INTO daily_cards (user_id, question_id, card_date, card_type, source_reason, status, delivered_at, opened_at, created_at)
            VALUES (?, ?, current_date, 'daily', 'recommendation', 'new', now(), NULL, now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            userId,
            questionId,
        )
    }
}
