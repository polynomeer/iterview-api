package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.interview.service.InterviewLlmApiTransport
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = [
        "app.interview.llm.api-key=test-key",
        "app.interview.llm.model=gpt-5-mini",
        "app.interview.llm.prompt-version=interview-follow-up-v1",
    ],
)
@org.springframework.context.annotation.Import(AiInterviewFollowUpApiIntegrationTest.FakeInterviewLlmConfig::class)
@Testcontainers(disabledWithoutDocker = true)
class AiInterviewFollowUpApiIntegrationTest {
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
            VALUES (1, 'ai-session-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "ai-session-user@example.com")}"
    }

    @Test
    fun `resume mock session creates ai opening and ai follow up snapshots`() {
        val categoryId = insertCategory("AI Follow Up")
        val questionId = insertQuestion("Tell me about a payments migration", categoryId)
        val resumeVersionId = insertResumeVersion(questionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .header("X-App-Locale", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("ai_opening"))
            .andExpect(jsonPath("$.currentQuestion.questionId").isNumber)
            .andExpect(jsonPath("$.currentQuestion.bodyText").value("Focus on the migration scope, trade-offs, and how you measured success."))
            .andExpect(jsonPath("$.currentQuestion.contentLocale").value("en"))
            .andExpect(jsonPath("$.currentQuestion.resumeEvidence[0].section").value("project"))
            .andExpect(jsonPath("$.currentQuestion.resumeEvidence[0].label").value("Payments migration"))
            .andExpect(jsonPath("$.currentQuestion.generationStatus").value("ai_generated"))
            .andReturn()
            .response.contentAsString.let(objectMapper::readTree)
        val sessionId = sessionResponse.get("id").asLong()
        val sessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .header("X-App-Locale", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to sessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "I coordinated the migration and kept the rollout stable with staged checks.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextQuestion.sourceType").value("ai_follow_up"))
            .andExpect(jsonPath("$.nextQuestion.questionId").isNumber)
            .andExpect(jsonPath("$.nextQuestion.bodyText").value("Describe rollback criteria, monitoring signals, and communication flow."))
            .andExpect(jsonPath("$.nextQuestion.tags[0]").value("payments"))
            .andExpect(jsonPath("$.nextQuestion.focusSkillNames[0]").value("Risk Management"))
            .andExpect(jsonPath("$.nextQuestion.resumeEvidence[0].section").value("project"))
            .andExpect(jsonPath("$.nextQuestion.resumeEvidence[0].sourceRecordType").value("resume_project_snapshot"))
            .andExpect(jsonPath("$.nextQuestion.generationStatus").value("ai_generated"))
            .andExpect(jsonPath("$.nextQuestion.contentLocale").value("en"))
            .andExpect(jsonPath("$.nextQuestion.llmModel").value("gpt-5-mini"))
            .andExpect(jsonPath("$.nextQuestion.llmPromptVersion").value("interview-follow-up-v1"))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader).header("X-App-Locale", "en"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[1].sourceType").value("ai_follow_up"))
            .andExpect(jsonPath("$.questions[1].resumeContextSummary").value("Resume mentions a payments platform migration with latency targets."))
            .andExpect(jsonPath("$.questions[1].resumeEvidence[0].snippet").value("Led phased rollout of the payments migration with rollback safeguards."))
            .andExpect(jsonPath("$.questions[1].generationRationale").value("The answer mentioned the migration outcome but did not defend cutover risk controls."))
            .andExpect(jsonPath("$.questions[1].contentLocale").value("en"))
    }

    private fun insertCategory(name: String): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO categories (name, created_at)
            VALUES (?, now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            name,
        )

    private fun insertQuestion(title: String, categoryId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO questions (
                category_id, title, body, question_type, difficulty_level, source_type, quality_status, visibility,
                expected_answer_seconds, is_active, created_at, updated_at
            ) VALUES (?, ?, ?, 'behavioral', 'MEDIUM', 'seed', 'approved', 'private', 180, true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            categoryId,
            title,
            "$title body",
        )

    private fun insertResumeVersion(questionId: Long): Long {
        val resumeId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resumes (id, user_id, title, is_primary, created_at, updated_at)
            VALUES (DEFAULT, 1, 'AI Session Resume', true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (
                resume_id, version_no, file_name, file_type, storage_key, file_size_bytes,
                raw_text, parsed_json, summary_text, parsing_status, parse_error_message, parse_started_at,
                parse_completed_at, uploaded_at, is_active, created_at, llm_extraction_status, llm_extraction_error_message,
                llm_extraction_started_at, llm_extraction_completed_at, llm_extraction_confidence, llm_model, llm_prompt_version
            ) VALUES (
                ?, 1, 'resume.pdf', 'application/pdf', '/tmp/resume.pdf', 1024,
                'Payments migration resume raw text', NULL, 'Backend engineer with payments migration work.', 'completed', NULL, now(),
                now(), now(), true, now(), 'completed', NULL,
                now(), now(), 0.91, 'gpt-5-mini', 'resume-extract-v1'
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        ).also { versionId ->
            jdbcTemplate.update(
                """
                INSERT INTO resume_project_snapshots (
                    resume_version_id, resume_experience_snapshot_id, title, organization_name, role_name, summary_text,
                    content_text, project_category_code, project_category_name, tech_stack_text, started_on, ended_on,
                    source_text, display_order, created_at, updated_at
                ) VALUES (
                    ?, NULL, 'Payments migration', 'Iterview', 'Backend Engineer', 'Migrated the payments platform',
                    'Led phased rollout with traffic shifting', 'payments', 'Payments', 'Kotlin, Spring Boot, Postgres',
                    NULL, NULL, 'Payments migration details', 1, now(), now()
                )
                """.trimIndent(),
                versionId,
            )
            jdbcTemplate.update(
                """
                INSERT INTO resume_risk_items (
                    resume_version_id, risk_type, title, description, severity, linked_question_id, created_at, updated_at
                ) VALUES (
                    ?, 'impact_claim', 'Payments migration latency target', 'Need deeper defense for rollout controls and monitoring.', 'HIGH', ?,
                    now(), now()
                )
                """.trimIndent(),
                versionId,
                questionId,
            )
        }
    }

    @TestConfiguration
    class FakeInterviewLlmConfig {
        @Bean
        @Primary
        fun interviewLlmApiTransport(): InterviewLlmApiTransport = object : InterviewLlmApiTransport {
            override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
                val english = body.contains("Output language: English (en)")
                val outputText = if (body.contains("\"name\":\"interview_opening\"")) {
                    if (english) {
                        "{\"promptText\":\"Tell me about the payments migration you led and the trade-offs you had to manage.\",\"bodyText\":\"Focus on the migration scope, trade-offs, and how you measured success.\",\"tags\":[\"payments\",\"migration\"],\"focusSkillNames\":[\"System Design\",\"Ownership\"],\"resumeContextSummary\":\"Resume highlights ownership of a payments platform migration.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Led phased rollout of the payments migration with rollback safeguards.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":1,\"confidence\":0.94,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"The resume shows a high-impact migration, so the opener should start from the strongest concrete project.\"}"
                    } else {
                        "{\"promptText\":\"주도했던 결제 마이그레이션과 그 과정의 트레이드오프를 설명해 주세요.\",\"bodyText\":\"마이그레이션 범위, 트레이드오프, 그리고 성공을 어떻게 측정했는지 중심으로 설명해 주세요.\",\"tags\":[\"결제\",\"마이그레이션\"],\"focusSkillNames\":[\"시스템 설계\",\"오너십\"],\"resumeContextSummary\":\"이력서에는 결제 플랫폼 마이그레이션을 주도한 경험이 강조되어 있습니다.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Led phased rollout of the payments migration with rollback safeguards.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":1,\"confidence\":0.94,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"영향도가 큰 프로젝트가 보여서 가장 강한 근거부터 여는 질문으로 선택했습니다.\"}"
                    }
                } else {
                    if (english) {
                        "{\"promptText\":\"How did you de-risk the cutover window?\",\"bodyText\":\"Describe rollback criteria, monitoring signals, and communication flow.\",\"tags\":[\"payments\",\"migration\"],\"focusSkillNames\":[\"Risk Management\",\"Observability\"],\"resumeContextSummary\":\"Resume mentions a payments platform migration with latency targets.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Led phased rollout of the payments migration with rollback safeguards.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":1,\"confidence\":0.91,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"The answer mentioned the migration outcome but did not defend cutover risk controls.\"}"
                    } else {
                        "{\"promptText\":\"컷오버 구간의 위험을 어떻게 낮췄나요?\",\"bodyText\":\"롤백 기준, 모니터링 신호, 커뮤니케이션 흐름까지 설명해 주세요.\",\"tags\":[\"결제\",\"마이그레이션\"],\"focusSkillNames\":[\"리스크 관리\",\"관측성\"],\"resumeContextSummary\":\"이력서에는 지연 시간 목표가 있는 결제 플랫폼 마이그레이션 경험이 언급됩니다.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Led phased rollout of the payments migration with rollback safeguards.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":1,\"confidence\":0.91,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"답변에서 마이그레이션 결과는 언급했지만 컷오버 리스크 통제는 충분히 방어하지 못했습니다.\"}"
                    }
                }
                return """
                    {
                      "model": "gpt-5-mini",
                      "output_text": "${outputText.replace("\"", "\\\"")}"
                    }
                """.trimIndent()
            }
        }
    }
}
