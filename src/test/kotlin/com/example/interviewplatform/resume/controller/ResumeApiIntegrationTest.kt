package com.example.interviewplatform.resume.controller

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
        TestDatabaseCleaner.reset(jdbcTemplate)
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

    @Test
    fun `activating newer version preserves previous answer attempt resume linkage`() {
        val resumeId = createResume("Versioned Resume")
        val version1Id = createResumeVersion(resumeId, "https://files.example.com/resume-v1.pdf", "Version one")
        val version2Id = createResumeVersion(resumeId, "https://files.example.com/resume-v2.pdf", "Version two")

        activateVersion(version1Id)
        val questionId = insertQuestion("Resume linkage question")
        submitAnswer(questionId, version1Id, "Answer using V1")

        activateVersion(version2Id)
        submitAnswer(questionId, version2Id, "Answer using V2")

        mockMvc.perform(get("/api/resumes").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].versions.length()").value(2))
            .andExpect(jsonPath("$[0].versions[0].isActive").value(false))
            .andExpect(jsonPath("$[0].versions[1].isActive").value(true))

        val resumeVersionIds = jdbcTemplate.queryForList(
            "SELECT resume_version_id FROM answer_attempts WHERE user_id = 1 AND question_id = ? ORDER BY attempt_no ASC",
            Long::class.java,
            questionId,
        )
        assertEquals(listOf(version1Id, version2Id), resumeVersionIds)
    }

    private fun createResume(title: String): Long {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "title" to title,
                "isPrimary" to true,
            ),
        )
        val result = mockMvc.perform(
            post("/api/resumes")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asLong()
    }

    private fun createResumeVersion(resumeId: Long, fileUrl: String, summaryText: String): Long {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "fileUrl" to fileUrl,
                "rawText" to "resume text",
                "parsedJson" to "{\"skills\":[\"kotlin\"]}",
                "summaryText" to summaryText,
            ),
        )
        val result = mockMvc.perform(
            post("/api/resumes/$resumeId/versions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("id").asLong()
    }

    private fun activateVersion(versionId: Long) {
        mockMvc.perform(post("/api/resume-versions/$versionId/activate").header("Authorization", authHeader))
            .andExpect(status().isOk)
    }

    private fun submitAnswer(questionId: Long, resumeVersionId: Long, contentText: String) {
        mockMvc.perform(
            post("/api/questions/$questionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "resumeVersionId" to resumeVersionId,
                            "answerMode" to "text",
                            "contentText" to contentText,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
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
}
