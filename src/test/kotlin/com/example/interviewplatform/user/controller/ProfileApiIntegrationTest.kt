package com.example.interviewplatform.user.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.mock.web.MockMultipartFile

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProfileApiIntegrationTest {
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
            VALUES (1, 'dev@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "dev@example.com")}"
    }

    @Test
    fun `get me returns aggregated payload`() {
        mockMvc.perform(get("/api/me").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profile.nickname").doesNotExist())
            .andExpect(jsonPath("$.settings.targetScoreThreshold").value(80))
            .andExpect(jsonPath("$.settings.passScoreThreshold").value(60))
            .andExpect(jsonPath("$.settings.preferredLanguage").value("ko"))
            .andExpect(jsonPath("$.activeResumeVersionSummary").isEmpty)
            .andExpect(jsonPath("$.targetCompanies").isArray)
    }

    @Test
    fun `patch profile and settings persist values`() {
        val jobRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM job_roles WHERE name = 'Backend Engineer'",
            Long::class.java,
        )!!

        val profileBody = objectMapper.writeValueAsString(
            mapOf(
                "nickname" to "hammac",
                "jobRoleId" to jobRoleId,
                "yearsOfExperience" to 5,
            ),
        )

        mockMvc.perform(
            patch("/api/me/profile")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nickname").value("hammac"))
            .andExpect(jsonPath("$.jobRoleId").value(jobRoleId))
            .andExpect(jsonPath("$.yearsOfExperience").value(5))

        val settingsBody = objectMapper.writeValueAsString(
            mapOf(
                "targetScoreThreshold" to 85,
                "passScoreThreshold" to 65,
                "retryEnabled" to true,
                "dailyQuestionCount" to 2,
                "preferredLanguage" to "en",
            ),
        )

        mockMvc.perform(
            patch("/api/me/settings")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsBody),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetScoreThreshold").value(85))
            .andExpect(jsonPath("$.passScoreThreshold").value(65))
            .andExpect(jsonPath("$.dailyQuestionCount").value(2))
            .andExpect(jsonPath("$.preferredLanguage").value("en"))
    }

    @Test
    fun `patch settings rejects unsupported preferred language`() {
        val settingsBody = objectMapper.writeValueAsString(
            mapOf(
                "preferredLanguage" to "ja",
            ),
        )

        mockMvc.perform(
            patch("/api/me/settings")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsBody),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.message").value("preferredLanguage must be one of: ko, en"))
    }

    @Test
    fun `upload profile image stores metadata on profile`() {
        val file = MockMultipartFile(
            "file",
            "avatar.png",
            "image/png",
            "fake-png-content".toByteArray(),
        )

        mockMvc.perform(
            multipart("/api/me/profile-image")
                .file(file)
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.containsString("/uploads/profile-images/")))
            .andExpect(jsonPath("$.fileName").value(org.hamcrest.Matchers.endsWith(".png")))
            .andExpect(jsonPath("$.contentType").value("image/png"))

        mockMvc.perform(get("/api/me").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.profile.profileImageUrl").value(org.hamcrest.Matchers.containsString("/uploads/profile-images/")))
            .andExpect(jsonPath("$.profile.profileImageContentType").value("image/png"))
    }

    @Test
    fun `put target companies replaces user target list`() {
        val amazonId = jdbcTemplate.queryForObject(
            "SELECT id FROM companies WHERE normalized_name = 'amazon'",
            Long::class.java,
        )!!
        val googleId = jdbcTemplate.queryForObject(
            "SELECT id FROM companies WHERE normalized_name = 'google'",
            Long::class.java,
        )!!

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "companies" to listOf(
                    mapOf("companyId" to googleId, "priorityOrder" to 1),
                    mapOf("companyId" to amazonId, "priorityOrder" to 2),
                ),
            ),
        )

        mockMvc.perform(
            put("/api/me/target-companies")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.companies.length()").value(2))
            .andExpect(jsonPath("$.companies[0].companyId").value(googleId))
            .andExpect(jsonPath("$.companies[0].priorityOrder").value(1))

        mockMvc.perform(get("/api/me").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.targetCompanies.length()").value(2))
            .andExpect(jsonPath("$.targetCompanies[0].companyId").value(googleId))
    }

    @Test
    fun `put target companies rejects duplicate company ids`() {
        val amazonId = jdbcTemplate.queryForObject(
            "SELECT id FROM companies WHERE normalized_name = 'amazon'",
            Long::class.java,
        )!!

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "companies" to listOf(
                    mapOf("companyId" to amazonId, "priorityOrder" to 1),
                    mapOf("companyId" to amazonId, "priorityOrder" to 2),
                ),
            ),
        )

        mockMvc.perform(
            put("/api/me/target-companies")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.message").value("Duplicate companyId entries are not allowed"))
    }
}
