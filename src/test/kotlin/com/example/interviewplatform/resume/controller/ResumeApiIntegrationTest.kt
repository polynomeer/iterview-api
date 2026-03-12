package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream

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

    @Test
    fun `latest resume and extracted resume intelligence endpoints return version snapshots`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")
        seedSkill("Redis", "BACKEND")

        val resumeId = createResume("Parser Ready Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/parser-ready-resume.pdf",
            summaryText = "Built backend APIs with Spring Boot and improved Redis cache latency by 40%.",
            rawText = "Built backend APIs with Spring Boot. Improved Redis cache latency by 40%.",
            parsedJson = "{\"skills\":[\"Spring Boot\",\"Redis\"]}",
        )

        mockMvc.perform(get("/api/resumes/latest").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(resumeId))
            .andExpect(jsonPath("$.versions[0].id").value(versionId))
            .andExpect(jsonPath("$.versions[0].parsingStatus").value("completed"))

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].skillName").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/experiences").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.items.length()").value(3))

        mockMvc.perform(get("/api/resume-versions/$versionId/risks").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.items.length()").value(2))
    }

    @Test
    fun `uploading pdf resume creates parsed version with downloadable file`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")
        seedSkill("PostgreSQL", "BACKEND")

        val resumeId = createResume("Uploaded Resume")
        val pdf = MockMultipartFile(
            "file",
            "candidate-resume.pdf",
            "application/pdf",
            createPdf(
                listOf(
                    "Built backend APIs with Spring Boot and PostgreSQL.",
                    "Improved checkout latency by 35% with query tuning.",
                ),
            ),
        )

        val result = mockMvc.perform(
            multipart("/api/resumes/$resumeId/versions/upload")
                .file(pdf)
                .param("summaryText", "Senior backend resume")
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.versionNo").value(1))
            .andExpect(jsonPath("$.fileName").value("candidate-resume.pdf"))
            .andExpect(jsonPath("$.fileType").value("application/pdf"))
            .andExpect(jsonPath("$.parsingStatus").value("completed"))
            .andExpect(jsonPath("$.parseCompletedAt").isNotEmpty)
            .andExpect(jsonPath("$.fileUrl").exists())
            .andReturn()

        val root = objectMapper.readTree(result.response.contentAsString)
        val versionId = root.get("id").asLong()
        assertTrue(root.get("fileSizeBytes").asLong() > 0)

        mockMvc.perform(get("/api/resume-versions/$versionId/file").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect { mvcResult ->
                assertEquals("application/pdf", mvcResult.response.contentType)
                assertTrue(mvcResult.response.contentAsByteArray.isNotEmpty())
            }

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))

        mockMvc.perform(get("/api/resume-versions/$versionId/risks").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
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

    private fun createResumeVersion(
        resumeId: Long,
        fileUrl: String,
        summaryText: String,
        rawText: String = "resume text",
        parsedJson: String = "{\"skills\":[\"kotlin\"]}",
    ): Long {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "fileUrl" to fileUrl,
                "rawText" to rawText,
                "parsedJson" to parsedJson,
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

    private fun seedSkillCategory(code: String, name: String, displayOrder: Int) {
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
    }

    private fun seedSkill(name: String, categoryCode: String) {
        jdbcTemplate.update(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            SELECT sc.id, ?, ?, now(), now()
            FROM skill_categories sc
            WHERE sc.code = ?
            ON CONFLICT (name) DO UPDATE
            SET skill_category_id = EXCLUDED.skill_category_id,
                description = EXCLUDED.description,
                updated_at = now()
            """.trimIndent(),
            name,
            "$name description",
            categoryCode,
        )
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

    private fun createPdf(lines: List<String>): ByteArray {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        PDPageContentStream(document, page).use { stream ->
            stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            stream.beginText()
            stream.newLineAtOffset(50f, 700f)
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    stream.newLineAtOffset(0f, -18f)
                }
                stream.showText(line)
            }
            stream.endText()
        }

        val output = ByteArrayOutputStream()
        document.use { it.save(output) }
        return output.toByteArray()
    }
}
