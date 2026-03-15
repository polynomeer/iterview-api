package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InterviewRecordApiIntegrationTest {
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
            VALUES (1, 'record-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO resumes (id, user_id, title, is_primary, created_at, updated_at)
            VALUES (10, 1, 'Imported Resume', true, now(), now())
            """.trimIndent(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO resume_versions (
                id, resume_id, version_no, file_url, file_name, file_type, storage_key, file_size_bytes,
                checksum_sha256, raw_text, parsed_json, summary_text, parsing_status, parse_started_at,
                parse_completed_at, parse_error_message, is_active, uploaded_at, created_at
            ) VALUES (
                20, 10, 1, '/uploads/resume-files/u/resume.pdf', 'resume.pdf', 'application/pdf', 'u/r.pdf', 100,
                'abc', 'raw', '{}', 'summary', 'completed', now(), now(), null, true, now(), now()
            )
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "record-user@example.com")}"
    }

    @Test
    fun `upload interview record structures transcript and exposes review APIs`() {
        val audio = MockMultipartFile("file", "real-interview.m4a", "audio/mp4", "fake-audio".toByteArray())

        val created = mockMvc.perform(
            multipart("/api/interview-records")
                .file(audio)
                .param("companyName", "Example Corp")
                .param("roleName", "Backend Engineer")
                .param("interviewDate", "2026-03-14")
                .param("interviewType", "onsite")
                .param("linkedResumeVersionId", "20")
                .param(
                    "transcriptText",
                    """
                    면접관: Redis를 적용한 이유가 무엇인가요?
                    지원자: DB 부하를 줄이고 응답 속도를 안정화하려고 했습니다. p95 지연 시간을 30% 줄였습니다.
                    면접관: 캐시 무효화는 어떻게 처리하셨나요?
                    지원자: TTL과 명시적 삭제를 병행했고 정합성이 중요한 흐름은 캐시를 우회했습니다.
                    """.trimIndent(),
                )
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.companyName").value("Example Corp"))
            .andExpect(jsonPath("$.roleName").value("Backend Engineer"))
            .andExpect(jsonPath("$.transcriptStatus").value("confirmed"))
            .andExpect(jsonPath("$.analysisStatus").value("completed"))
            .andExpect(jsonPath("$.linkedResumeVersionId").value(20))
            .andExpect(jsonPath("$.structuringStage").value("deterministic"))
            .andExpect(jsonPath("$.deterministicSummary").isString)
            .andExpect(jsonPath("$.questionCount").value(2))
            .andExpect(jsonPath("$.answerCount").value(2))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val recordId = created.get("id").asLong()

        mockMvc.perform(get("/api/interview-records").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(recordId))
            .andExpect(jsonPath("$[0].questionCount").value(2))

        mockMvc.perform(get("/api/interview-records/$recordId/transcript").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.segments.length()").value(4))
            .andExpect(jsonPath("$.segments[0].speakerType").value("interviewer"))
            .andExpect(jsonPath("$.segments[1].speakerType").value("candidate"))

        val questions = mockMvc.perform(get("/api/interview-records/$recordId/questions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].linkedQuestionId").isNumber)
            .andExpect(jsonPath("$.items[0].questionType").value("technical_depth"))
            .andExpect(jsonPath("$.items[0].structuringSource").value("deterministic"))
            .andExpect(jsonPath("$.items[0].answer.strengthTags[0]").value("quantified"))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val firstQuestionId = questions["items"][0]["id"].asLong()
        assertEquals(firstQuestionId, questions["items"][1]["parentQuestionId"].asLong())
        assertTrue(questions["items"][1]["answer"]["summary"].asText().contains("TTL"))

        mockMvc.perform(get("/api/interview-records/$recordId/analysis").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalQuestions").value(2))
            .andExpect(jsonPath("$.followUpCount").value(1))
            .andExpect(jsonPath("$.structuringStage").value("deterministic"))
            .andExpect(jsonPath("$.topicTags[0]").exists())

        mockMvc.perform(get("/api/interview-records/$recordId/interviewer-profile").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.structuringSource").value("deterministic"))
            .andExpect(jsonPath("$.pressureLevel").exists())

        mockMvc.perform(get("/api/interview-records/$recordId/review").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("deterministic"))
            .andExpect(jsonPath("$.requiresConfirmation").value(true))
            .andExpect(jsonPath("$.questionSourceCounts.deterministic").value(2))
            .andExpect(jsonPath("$.answerSourceCounts.deterministic").value(2))

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/interview-records/$recordId/confirm")
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("confirmed"))
            .andExpect(jsonPath("$.confirmedAt").exists())

        mockMvc.perform(get("/api/interview-records/$recordId/review").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("confirmed"))
            .andExpect(jsonPath("$.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.questionSourceCounts.confirmed").value(2))
            .andExpect(jsonPath("$.answerSourceCounts.confirmed").value(2))
    }

    @Test
    fun `updating transcript segment rebuilds structured questions`() {
        val audio = MockMultipartFile("file", "real-interview.wav", "audio/wav", "fake-audio".toByteArray())
        val created = mockMvc.perform(
            multipart("/api/interview-records")
                .file(audio)
                .param(
                    "transcriptText",
                    """
                    interviewer: Tell me about your migration project?
                    candidate: I migrated core APIs.
                    """.trimIndent(),
                )
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val recordId = created.get("id").asLong()
        val transcript = mockMvc.perform(get("/api/interview-records/$recordId/transcript").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)
        val segmentId = transcript.get("segments")[0].get("id").asLong()

        mockMvc.perform(
            patch("/api/interview-records/$recordId/transcript/segments/$segmentId")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "speakerType" to "interviewer",
                            "confirmedText" to "Tell me about the migration project and rollout metrics?",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.segments[0].confirmedText").value("Tell me about the migration project and rollout metrics?"))

        mockMvc.perform(get("/api/interview-records/$recordId/questions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].text").value("Tell me about the migration project and rollout metrics?"))
            .andExpect(jsonPath("$.items[0].structuringSource").value("confirmed"))
            .andExpect(jsonPath("$.items[0].topicTags[0]").exists())

        mockMvc.perform(get("/api/interview-records/$recordId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("confirmed"))
            .andExpect(jsonPath("$.deterministicSummary").isString)
    }

    @Test
    fun `adjacent same speaker transcript lines are merged into one answer segment`() {
        val audio = MockMultipartFile("file", "real-interview.wav", "audio/wav", "fake-audio".toByteArray())
        val created = mockMvc.perform(
            multipart("/api/interview-records")
                .file(audio)
                .param(
                    "transcriptText",
                    """
                    interviewer: Describe the rollout.
                    candidate: We migrated the APIs in phases.
                    candidate: We watched error rate and latency during the rollout.
                    interviewer: What metric moved the most?
                    candidate: p95 latency dropped by 30 percent.
                    """.trimIndent(),
                )
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val recordId = created["id"].asLong()
        val transcript = mockMvc.perform(get("/api/interview-records/$recordId/transcript").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)
        assertEquals(4, transcript["segments"].size())

        val questions = mockMvc.perform(get("/api/interview-records/$recordId/questions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)
        assertNotNull(questions["items"][1]["parentQuestionId"])
        assertTrue(questions["items"][0]["answer"]["summary"].asText().contains("error rate"))
    }
}
