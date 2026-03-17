package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.sun.net.httpserver.HttpServer
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.nullValue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

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
            rawText = """
                Kim Resume
                Contact : 010-1234-5678
                Mail : resume@example.com
                GitHub : https://github.com/resume-dev
                보유 역량
                • Backend leadership. Built backend APIs with Spring Boot and improved Redis cache latency by 40%.
                • System ownership. Reduced deployment time by 20%.
            """.trimIndent(),
            parsedJson = "{\"skills\":[\"Spring Boot\",\"Redis\"]}",
        )

        mockMvc.perform(get("/api/resumes/latest").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(resumeId))
            .andExpect(jsonPath("$.versions[0].id").value(versionId))
            .andExpect(jsonPath("$.versions[0].parsingStatus").value("completed"))
            .andExpect(jsonPath("$.versions[0].llmExtractionStatus").value("skipped"))

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
            .andExpect(jsonPath("$.items.length()").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/extraction").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.rawParsingStatus").value("completed"))
            .andExpect(jsonPath("$.llmExtractionStatus").value("skipped"))

        mockMvc.perform(get("/api/resume-versions/$versionId/profile").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.item.fullName").value("Kim Resume"))

        mockMvc.perform(get("/api/resume-versions/$versionId/contacts").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))

        mockMvc.perform(get("/api/resume-versions/$versionId/competencies").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))

        assertCountAtLeast("resume_profile_snapshots", versionId, 1)
        assertCountAtLeast("resume_competency_items", versionId, 1)
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
                    "Resume Kim",
                    "Contact : 010-9876-5432",
                    "Mail : pdf@example.com",
                    "GitHub : https://github.com/pdf-resume",
                    "Core strengths",
                    "- Backend optimization. Improved checkout latency by 35%.",
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
            .andExpect(jsonPath("$.llmExtractionStatus").value("skipped"))
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

        mockMvc.perform(get("/api/resume-versions/$versionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(versionId))
            .andExpect(jsonPath("$.fileUrl").value("/api/resume-versions/$versionId/file"))
            .andExpect(jsonPath("$.parsingStatus").value("completed"))
            .andExpect(jsonPath("$.llmExtractionStatus").value("skipped"))

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/profile").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.fullName").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/contacts").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(3))

        mockMvc.perform(get("/api/resume-versions/$versionId/risks").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").isNotEmpty)

        assertCountAtLeast("resume_profile_snapshots", versionId, 1)
        assertCountAtLeast("resume_contact_points", versionId, 1)
    }

    @Test
    fun `re extract endpoint refreshes extraction metadata and snapshots`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")

        val resumeId = createResume("Reextract Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/reextract.pdf",
            summaryText = "Built backend APIs with Spring Boot",
            rawText = """
                Resume Reextract
                Mail : reextract@example.com
                보유 역량
                • Backend APIs. Built backend APIs with Spring Boot and improved latency by 20%.
            """.trimIndent(),
            parsedJson = null,
        )

        mockMvc.perform(post("/api/resume-versions/$versionId/re-extract").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.rawParsingStatus").value("completed"))
            .andExpect(jsonPath("$.llmExtractionStatus").value("skipped"))

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/profile").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.fullName").value("Resume Reextract"))

        mockMvc.perform(get("/api/resume-versions/$versionId/contacts").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))

        assertCountAtLeast("resume_profile_snapshots", versionId, 1)
        assertCountAtLeast("resume_contact_points", versionId, 1)
    }

    @Test
    fun `resume projects endpoint returns content category and tags`() {
        val resumeId = createResume("Project Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/project-resume.pdf",
            summaryText = "Backend engineer with payments experience",
            rawText = """
                Resume Project
                Mail : projects@example.com
                💼 경력
                Acme Corp - Backend Engineer 2024.01 ~ 현재
                Led commerce backend platform work.
                📜 프로젝트
                Payments Platform Revamp 2024.02 ~ 2024.12
                기술스택 Kotlin, Spring Boot, PostgreSQL
                Built payment APIs and billing workflows for checkout.
                Improved checkout latency by 35% with cache optimization.
            """.trimIndent(),
            parsedJson = null,
        )

        mockMvc.perform(get("/api/resume-versions/$versionId/projects").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Payments Platform Revamp"))
            .andExpect(jsonPath("$.items[0].contentText").isNotEmpty)
            .andExpect(jsonPath("$.items[0].projectCategoryCode").value("payments"))
            .andExpect(jsonPath("$.items[0].projectCategoryName").value("Payments"))
            .andExpect(jsonPath("$.items[0].tags.length()").isNotEmpty)
            .andExpect(jsonPath("$.items[0].tags[0].tagName").isNotEmpty)

        assertCountAtLeast("resume_project_snapshots", versionId, 1)
        assertProjectTagCountAtLeast(versionId, 1)
    }

    @Test
    fun `resume extraction stores projects awards certifications and education while suppressing verbose raw source fields`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")
        seedSkill("Kotlin", "BACKEND")

        val resumeId = createResume("Structured Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/structured-resume.pdf",
            summaryText = "This summary should not be persisted into profile snapshot.",
            rawText = """
                함승훈
                Mail : resume@example.com
                GitHub : https://github.com/polynomeer
                보유 역량
                • Kotlin, Spring Boot 기반 백엔드 개발
                💼 경력
                Dreamus - Backend Engineer 2023.01 ~ 현재
                커머스 백엔드와 결제 시스템을 개발했습니다.
                📜 프로젝트
                커머스 결제 고도화 프로젝트
                기술스택 Kotlin, Spring Boot, PostgreSQL, Redis
                결제 API와 정산 플로우를 개선했습니다.
                교육 및 활동
                멋쟁이사자처럼 백엔드 스쿨 수료 2022.03 ~ 2022.08
                수상이력
                2022.11 우수상 | 멋쟁이사자처럼 해커톤
                자격사항
                SQLD | 한국데이터산업진흥원 | 2023.07
            """.trimIndent(),
            parsedJson = "{\"skills\":[\"Kotlin\",\"Spring Boot\"]}",
        )

        mockMvc.perform(get("/api/resume-versions/$versionId/projects").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("커머스 결제 고도화 프로젝트"))
            .andExpect(jsonPath("$.items[0].contentText").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/awards").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("우수상"))

        mockMvc.perform(get("/api/resume-versions/$versionId/certifications").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("SQLD"))

        mockMvc.perform(get("/api/resume-versions/$versionId/education").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].institutionName").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].sourceText").value(nullValue()))
            .andExpect(jsonPath("$.items[1].sourceText").value(nullValue()))

        mockMvc.perform(get("/api/resume-versions/$versionId/profile").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.item.fullName").value("함승훈"))
            .andExpect(jsonPath("$.item.summaryText").value(nullValue()))

        assertCountAtLeast("resume_project_snapshots", versionId, 1)
        assertCountAtLeast("resume_award_items", versionId, 1)
        assertCountAtLeast("resume_certification_items", versionId, 1)
        assertCountAtLeast("resume_education_items", versionId, 1)
        assertNullCount("resume_skill_snapshots", versionId, "source_text")
        assertNullCount("resume_profile_snapshots", versionId, "summary_text")
        assertNullCount("resume_profile_snapshots", versionId, "source_text")
    }

    @Test
    fun `invalid pdf upload stores failed version status`() {
        val resumeId = createResume("Broken Resume")
        val invalidPdf = MockMultipartFile(
            "file",
            "broken-resume.pdf",
            "application/pdf",
            "not-a-real-pdf".toByteArray(StandardCharsets.UTF_8),
        )

        val result = mockMvc.perform(
            multipart("/api/resumes/$resumeId/versions/upload")
                .file(invalidPdf)
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parsingStatus").value("failed"))
            .andExpect(jsonPath("$.parseErrorMessage").isNotEmpty)
            .andReturn()

        val versionId = objectMapper.readTree(result.response.contentAsString).get("id").asLong()

        mockMvc.perform(get("/api/resume-versions/$versionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.parsingStatus").value("failed"))
            .andExpect(jsonPath("$.parseErrorMessage").isNotEmpty)

        mockMvc.perform(get("/api/resume-versions/$versionId/skills").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `job posting and resume analysis endpoints persist tailored analysis suggestions`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")
        seedSkill("Redis", "BACKEND")
        seedSkill("Kafka", "BACKEND")

        val resumeId = createResume("Tailored Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/tailored-resume.pdf",
            summaryText = "Built Spring Boot APIs and improved Redis cache latency by 40%.",
            rawText = """
                Kim Resume
                Backend engineer
                Built Spring Boot APIs and improved Redis cache latency by 40%.
                Led checkout migration project and reduced latency by 40%.
            """.trimIndent(),
            parsedJson = """{"skills":["Spring Boot","Redis"]}""",
        )

        val jobPosting = mockMvc.perform(
            post("/api/job-postings")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "inputType" to "text",
                            "companyName" to "Example Corp",
                            "roleName" to "Backend Platform Engineer",
                            "rawText" to """
                                Responsibilities
                                - Build backend APIs with Spring Boot
                                - Improve cache and messaging throughput with Redis and Kafka
                                Preferred
                                - Kafka operations experience
                            """.trimIndent(),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.companyName").value("Example Corp"))
            .andExpect(jsonPath("$.roleName").value("Backend Platform Engineer"))
            .andExpect(jsonPath("$.fetchStatus").value("provided"))
            .andExpect(jsonPath("$.parsedKeywords").isArray)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val jobPostingId = jobPosting["id"].asLong()

        val analysis = mockMvc.perform(
            post("/api/resume-versions/$versionId/analyses")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("jobPostingId" to jobPostingId))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.jobPostingId").value(jobPostingId))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.overallScore").isNumber)
            .andExpect(jsonPath("$.generationSource").value("deterministic"))
            .andExpect(jsonPath("$.strongMatches").isArray)
            .andExpect(jsonPath("$.tailoredDocument.title").isString)
            .andExpect(jsonPath("$.tailoredDocument.sections.length()").value(org.hamcrest.Matchers.greaterThan(1)))
            .andExpect(jsonPath("$.suggestions.length()").value(org.hamcrest.Matchers.greaterThan(1)))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val analysisId = analysis["id"].asLong()
        val suggestionId = analysis["suggestions"][0]["id"].asLong()

        mockMvc.perform(get("/api/resume-versions/$versionId/analyses").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(analysisId))
            .andExpect(jsonPath("$[0].jobPostingId").value(jobPostingId))

        mockMvc.perform(get("/api/resume-versions/$versionId/analyses/$analysisId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.matchSummary").isString)
            .andExpect(jsonPath("$.recommendedFormatType").isString)
            .andExpect(jsonPath("$.exports.length()").value(0))

        mockMvc.perform(
            patch("/api/resume-versions/$versionId/analyses/$analysisId/suggestions/$suggestionId")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("accepted" to true))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.suggestions[0].accepted").value(true))

        val export = mockMvc.perform(
            post("/api/resume-versions/$versionId/analyses/$analysisId/exports")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("exportType" to "pdf"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exportType").value("pdf"))
            .andExpect(jsonPath("$.fileUrl").value("/api/resume-versions/$versionId/analyses/$analysisId/exports/1/file"))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val exportId = export["id"].asLong()
        mockMvc.perform(get("/api/resume-versions/$versionId/analyses/$analysisId/exports").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(exportId))

        mockMvc.perform(get("/api/resume-versions/$versionId/analyses/$analysisId/exports/$exportId/file").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect {
                assertEquals("application/pdf", it.response.contentType)
                assertTrue(it.response.contentAsByteArray.isNotEmpty())
                assertEquals("%PDF", it.response.contentAsByteArray.copyOfRange(0, 4).toString(StandardCharsets.UTF_8))
            }
    }

    @Test
    fun `resume question heatmap aggregates linked interview questions and supports manual remap`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Redis", "BACKEND")

        val resumeId = createResume("Heatmap Resume")
        val versionId = createResumeVersion(
            resumeId = resumeId,
            fileUrl = "https://files.example.com/heatmap-resume.pdf",
            summaryText = "Backend engineer with payments and platform experience",
            rawText = """
                Heatmap Kim
                Mail : heatmap@example.com
                경력
                Acme Corp - Backend Engineer 2023.01 ~ 현재
                Led platform and backend API work for commerce systems.
                프로젝트
                Payments Platform Revamp 2024.02 ~ 2024.12
                기술스택 Kotlin, Spring Boot, Redis
                Rebuilt payment APIs, cache strategy, and settlement workflow.
            """.trimIndent(),
            parsedJson = """{"skills":["Redis"]}""",
        )

        val projectId = jdbcTemplate.queryForObject(
            "SELECT id FROM resume_project_snapshots WHERE resume_version_id = ? ORDER BY display_order ASC, id ASC LIMIT 1",
            Long::class.java,
            versionId,
        )!!
        val experienceId = jdbcTemplate.queryForObject(
            "SELECT id FROM resume_experience_snapshots WHERE resume_version_id = ? ORDER BY display_order ASC, id ASC LIMIT 1",
            Long::class.java,
            versionId,
        )!!
        val recordId = insertInterviewRecord(versionId)
        val mainQuestionId = insertInterviewRecordQuestion(
            interviewRecordId = recordId,
            text = "Payments Platform Revamp에서 Redis 캐시 전략을 왜 그렇게 설계했나요?",
            questionType = "verification",
            intentTagsJson = """["verification","deep_dive"]""",
            derivedFromResumeSection = "project",
            derivedFromResumeRecordType = "project",
            derivedFromResumeRecordId = projectId,
            orderIndex = 0,
        )
        val followUpQuestionId = insertInterviewRecordQuestion(
            interviewRecordId = recordId,
            text = "그 선택이 정산 지연이나 장애 대응에는 어떤 trade-off를 만들었나요?",
            questionType = "follow_up",
            intentTagsJson = """["tradeoff"]""",
            derivedFromResumeSection = "project",
            derivedFromResumeRecordType = "project",
            derivedFromResumeRecordId = projectId,
            parentQuestionId = mainQuestionId,
            orderIndex = 1,
        )
        insertInterviewRecordAnswer(
            interviewRecordQuestionId = mainQuestionId,
            text = "캐시 적중률을 올렸지만 invalidation 설계 설명이 약했습니다.",
            weaknessTagsJson = """["missing_metric","weak_tradeoff"]""",
            strengthTagsJson = """["structured"]""",
            orderIndex = 0,
        )
        insertInterviewRecordAnswer(
            interviewRecordQuestionId = followUpQuestionId,
            text = "정산 지연과 캐시 최신성 trade-off를 설명했습니다.",
            weaknessTagsJson = "[]",
            strengthTagsJson = """["tradeoff_aware"]""",
            orderIndex = 1,
        )
        insertInterviewRecordFollowUpEdge(recordId, mainQuestionId, followUpQuestionId)

        mockMvc.perform(get("/api/resume-versions/$versionId/question-heatmap").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(versionId))
            .andExpect(jsonPath("$.scope").value("all"))
            .andExpect(jsonPath("$.summary.totalAnchors").value(1))
            .andExpect(jsonPath("$.summary.totalLinkedQuestions").value(2))
            .andExpect(jsonPath("$.summary.hottestAnchorLabel").value("Payments Platform Revamp"))
            .andExpect(jsonPath("$.items[0].anchorType").value("project"))
            .andExpect(jsonPath("$.items[0].anchorRecordId").value(projectId))
            .andExpect(jsonPath("$.items[0].directQuestionCount").value(2))
            .andExpect(jsonPath("$.items[0].followUpCount").value(1))
            .andExpect(jsonPath("$.items[0].pressureQuestionCount").value(1))
            .andExpect(jsonPath("$.items[0].weaknessCount").value(1))
            .andExpect(jsonPath("$.items[0].normalizedHeatLevel").value("high"))
            .andExpect(jsonPath("$.items[0].linkedQuestions[0].linkSource").value("inferred"))
            .andExpect(jsonPath("$.items[0].linkedQuestions[0].confidenceScore").value(0.9))

        mockMvc.perform(get("/api/resume-versions/$versionId/question-heatmap?scope=follow_up").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.totalLinkedQuestions").value(1))
            .andExpect(jsonPath("$.items[0].directQuestionCount").value(1))

        val linkId = mockMvc.perform(
            post("/api/resume-versions/$versionId/question-heatmap/links")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "interviewRecordQuestionId" to mainQuestionId,
                            "anchorType" to "experience",
                            "anchorRecordId" to experienceId,
                            "confidenceScore" to "0.9700",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.anchorType").value("experience"))
            .andExpect(jsonPath("$.anchorRecordId").value(experienceId))
            .andExpect(jsonPath("$.linkSource").value("manual"))
            .andExpect(jsonPath("$.confidenceScore").value(0.97))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)
            .get("id")
            .asLong()

        mockMvc.perform(get("/api/resume-versions/$versionId/question-heatmap").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.totalAnchors").value(2))
            .andExpect(jsonPath("$.summary.hottestAnchorLabel").value("Backend Engineer @ Acme Corp"))
            .andExpect(jsonPath("$.items[0].anchorType").value("experience"))
            .andExpect(jsonPath("$.items[0].anchorRecordId").value(experienceId))
            .andExpect(jsonPath("$.items[0].directQuestionCount").value(1))
            .andExpect(jsonPath("$.items[0].linkedQuestions[0].linkSource").value("manual"))
            .andExpect(jsonPath("$.items[0].linkedQuestions[0].confidenceScore").value(0.97))

        mockMvc.perform(
            patch("/api/resume-versions/$versionId/question-heatmap/links/$linkId")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"active":false}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(false))

        mockMvc.perform(get("/api/resume-versions/$versionId/question-heatmap").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.totalAnchors").value(1))
            .andExpect(jsonPath("$.summary.hottestAnchorLabel").value("Payments Platform Revamp"))
            .andExpect(jsonPath("$.items[0].anchorType").value("project"))
            .andExpect(jsonPath("$.items[0].directQuestionCount").value(2))
    }

    @Test
    fun `link job posting input fetches source text before parsing`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Spring Boot", "BACKEND")
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/jd") { exchange ->
            val body = """
                <html>
                <head><title>Fetch Corp - Senior Backend Engineer</title></head>
                <body>
                <h1>Senior Backend Engineer</h1>
                <p>Build backend APIs with Spring Boot and Redis.</p>
                <p>Preferred: Kafka operations experience.</p>
                </body>
                </html>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val sourceUrl = "http://127.0.0.1:${server.address.port}/jd"
            mockMvc.perform(
                post("/api/job-postings")
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "inputType" to "link",
                                "sourceUrl" to sourceUrl,
                            ),
                        ),
                    ),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.sourceUrl").value(sourceUrl))
                .andExpect(jsonPath("$.fetchStatus").value("fetched"))
                .andExpect(jsonPath("$.fetchedTitle").value("Fetch Corp - Senior Backend Engineer"))
                .andExpect(jsonPath("$.rawText").isNotEmpty)
                .andExpect(jsonPath("$.parsedKeywords").isArray)
        } finally {
            server.stop(0)
        }
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
        parsedJson: String? = "{\"skills\":[\"kotlin\"]}",
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

    private fun insertInterviewRecord(linkedResumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_records (
                user_id, company_name, role_name, interview_date, interview_type,
                raw_transcript, cleaned_transcript, confirmed_transcript, transcript_status,
                analysis_status, linked_resume_version_id, structuring_stage, created_at, updated_at
            ) VALUES (
                1, 'Acme Corp', 'Backend Engineer', DATE '2026-03-10', 'onsite',
                'transcript', 'transcript', 'transcript', 'confirmed',
                'completed', ?, 'confirmed', now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            linkedResumeVersionId,
        )!!

    private fun insertInterviewRecordQuestion(
        interviewRecordId: Long,
        text: String,
        questionType: String,
        intentTagsJson: String,
        derivedFromResumeSection: String?,
        derivedFromResumeRecordType: String?,
        derivedFromResumeRecordId: Long?,
        orderIndex: Int,
        parentQuestionId: Long? = null,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_record_questions (
                interview_record_id, text, normalized_text, question_type, topic_tags_json, intent_tags_json,
                derived_from_resume_section, derived_from_resume_record_type, derived_from_resume_record_id,
                parent_question_id, structuring_source, order_index, created_at, updated_at
            ) VALUES (
                ?, ?, lower(?), ?, '["redis","payments"]', ?,
                ?, ?, ?, ?, 'confirmed', ?, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            interviewRecordId,
            text,
            text,
            questionType,
            intentTagsJson,
            derivedFromResumeSection,
            derivedFromResumeRecordType,
            derivedFromResumeRecordId,
            parentQuestionId,
            orderIndex,
        )!!

    private fun insertInterviewRecordAnswer(
        interviewRecordQuestionId: Long,
        text: String,
        weaknessTagsJson: String,
        strengthTagsJson: String,
        orderIndex: Int,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_record_answers (
                interview_record_question_id, text, normalized_text, summary,
                confidence_markers_json, weakness_tags_json, strength_tags_json,
                structuring_source, order_index, created_at, updated_at
            ) VALUES (
                ?, ?, lower(?), ?, '[]', ?, ?, 'confirmed', ?, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            interviewRecordQuestionId,
            text,
            text,
            text.take(120),
            weaknessTagsJson,
            strengthTagsJson,
            orderIndex,
        )!!

    private fun insertInterviewRecordFollowUpEdge(
        interviewRecordId: Long,
        fromQuestionId: Long,
        toQuestionId: Long,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_record_follow_up_edges (
                interview_record_id, from_question_id, to_question_id, relation_type, trigger_type, created_at
            ) VALUES (
                ?, ?, ?, 'follow_up', 'answer_probe', now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            interviewRecordId,
            fromQuestionId,
            toQuestionId,
        )!!

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

    private fun assertCountAtLeast(tableName: String, resumeVersionId: Long, minimum: Int) {
        val actual = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $tableName WHERE resume_version_id = ?",
            Int::class.java,
            resumeVersionId,
        )
        assertTrue(actual >= minimum, "Expected at least $minimum rows in $tableName for resume version $resumeVersionId but found $actual")
    }

    private fun assertNullCount(tableName: String, resumeVersionId: Long, columnName: String) {
        val actual = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $tableName WHERE resume_version_id = ? AND $columnName IS NOT NULL",
            Int::class.java,
            resumeVersionId,
        )
        assertEquals(0, actual, "Expected $tableName.$columnName to be null for resume version $resumeVersionId")
    }

    private fun assertProjectTagCountAtLeast(resumeVersionId: Long, minimum: Int) {
        val actual = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM resume_project_tags rpt
            JOIN resume_project_snapshots rps ON rps.id = rpt.resume_project_snapshot_id
            WHERE rps.resume_version_id = ?
            """.trimIndent(),
            Int::class.java,
            resumeVersionId,
        )
        assertTrue(actual >= minimum, "Expected at least $minimum project tags for resume version $resumeVersionId but found $actual")
    }
}
