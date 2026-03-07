package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.auth.service.TokenService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
class ResumeApiIntegrationTest {
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
        jdbcTemplate.update("DELETE FROM resume_versions")
        jdbcTemplate.update("DELETE FROM resumes")
        jdbcTemplate.update("DELETE FROM user_target_companies")
        jdbcTemplate.update("DELETE FROM user_settings")
        jdbcTemplate.update("DELETE FROM user_profiles")
        jdbcTemplate.update("DELETE FROM users WHERE id = 1")
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'resume-dev@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "resume-dev@example.com")}"
    }

    @Test
    fun `create resume then add and activate version`() {
        val createResumePayload = objectMapper.writeValueAsString(
            mapOf(
                "title" to "Platform Resume",
                "isPrimary" to true,
            ),
        )
        val createResumeResult = mockMvc.perform(
            post("/api/resumes")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createResumePayload),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Platform Resume"))
            .andReturn()

        val resumeId = objectMapper.readTree(createResumeResult.response.contentAsString).get("id").asLong()

        val createVersionPayload = objectMapper.writeValueAsString(
            mapOf(
                "fileUrl" to "https://files.example.com/resume-v1.pdf",
                "rawText" to "Version one text",
                "parsedJson" to "{\"skills\":[\"kotlin\"]}",
                "summaryText" to "First version",
            ),
        )
        val createVersionResult = mockMvc.perform(
            post("/api/resumes/$resumeId/versions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVersionPayload),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.versionNo").value(1))
            .andExpect(jsonPath("$.isActive").value(false))
            .andReturn()

        val versionId = objectMapper.readTree(createVersionResult.response.contentAsString).get("id").asLong()

        mockMvc.perform(post("/api/resume-versions/$versionId/activate").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeId").value(resumeId))
            .andExpect(jsonPath("$.versionId").value(versionId))
            .andExpect(jsonPath("$.versionNo").value(1))

        mockMvc.perform(get("/api/resumes").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(resumeId))
            .andExpect(jsonPath("$[0].versions.length()").value(1))
            .andExpect(jsonPath("$[0].versions[0].isActive").value(true))
    }
}
