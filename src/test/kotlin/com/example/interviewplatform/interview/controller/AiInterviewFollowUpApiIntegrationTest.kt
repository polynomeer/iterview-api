package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.interview.service.InterviewLlmMultipartPart
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

    @Test
    fun `replay mock session creates interviewer-profile-based ai follow up`() {
        val sourceInterviewRecordId = insertInterviewRecord()
        insertInterviewerProfile(sourceInterviewRecordId)
        insertInterviewRecordQuestion(
            sourceInterviewRecordId = sourceInterviewRecordId,
            orderIndex = 1,
            text = "How did you decide rollback timing during the outage?",
            questionType = "technical_depth",
        )
        insertInterviewRecordAnswer(
            sourceInterviewRecordId = sourceInterviewRecordId,
            interviewRecordQuestionOrderIndex = 1,
            summary = "The original interviewer kept pushing on rollback thresholds and communication timing.",
        )

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .header("X-App-Locale", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "replay_mock",
                            "questionCount" to 1,
                            "sourceInterviewRecordId" to sourceInterviewRecordId,
                            "replayMode" to "pressure_variant",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sourceInterviewRecordId").value(sourceInterviewRecordId))
            .andExpect(jsonPath("$.replayMode").value("pressure_variant"))
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("replay_seed"))
            .andReturn()
            .response.contentAsString.let(objectMapper::readTree)

        val sessionId = sessionResponse.get("id").asLong()
        val sessionQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()

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
                            "contentText" to "I rolled back carefully after checking errors, but I did not explain the threshold in detail.\n".repeat(4),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextQuestion.sourceType").value("replay_ai_follow_up"))
            .andExpect(jsonPath("$.nextQuestion.generationStatus").value("replay_ai_generated"))
            .andExpect(jsonPath("$.nextQuestion.bodyText").value("Stay close to the imported interviewer style: push on rollback thresholds, communication timing, and operational pressure."))
            .andExpect(jsonPath("$.nextQuestion.generationRationale").value("The imported interviewer style was high-pressure and the answer still left rollback thresholds vague."))
            .andExpect(jsonPath("$.nextQuestion.contentLocale").value("en"))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader).header("X-App-Locale", "en"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[1].sourceType").value("replay_ai_follow_up"))
            .andExpect(jsonPath("$.questions[1].generationStatus").value("replay_ai_generated"))
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

    private fun insertInterviewRecord(): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_records (
                user_id, company_name, role_name, interview_date, interview_type, source_audio_file_url, source_audio_file_name,
                transcript_status, analysis_status, overall_summary, created_at, updated_at
            ) VALUES (
                1, 'Replay Corp', 'Platform Engineer', current_date, 'onsite', '/uploads/interview-audio/replay.m4a', 'replay.m4a',
                'confirmed', 'completed', 'High-pressure outage interview focused on rollback timing.', now(), now()
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )!!

    private fun insertInterviewerProfile(sourceInterviewRecordId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interviewer_profiles (
                user_id, source_interview_record_id, style_tags_json, tone_profile, pressure_level, depth_preference,
                follow_up_pattern_json, favorite_topics_json, opening_pattern, closing_pattern, created_at, updated_at
            ) VALUES (
                1, ?, '["pressure_probe","rollback_focus"]', 'probing', 'high', 'deep',
                '["threshold_probe","communication_probe"]', '["reliability","incident"]', 'technical_depth', 'technical_depth', now(), now()
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            sourceInterviewRecordId,
        )!!

    private fun insertInterviewRecordQuestion(
        sourceInterviewRecordId: Long,
        orderIndex: Int,
        text: String,
        questionType: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_record_questions (
                interview_record_id, segment_start_id, segment_end_id, text, normalized_text, question_type,
                topic_tags_json, intent_tags_json, derived_from_resume_section, derived_from_resume_record_type,
                derived_from_resume_record_id, derived_from_job_posting_section, parent_question_id, order_index, created_at, updated_at
            ) VALUES (
                ?, NULL, NULL, ?, ?, ?, '["incident"]', '["threshold_probe"]',
                NULL, NULL, NULL, NULL, NULL, ?, now(), now()
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            sourceInterviewRecordId,
            text,
            text.lowercase(),
            questionType,
            orderIndex,
        )!!

    private fun insertInterviewRecordAnswer(
        sourceInterviewRecordId: Long,
        interviewRecordQuestionOrderIndex: Int,
        summary: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO interview_record_answers (
                interview_record_question_id, segment_start_id, segment_end_id, text, normalized_text, summary,
                confidence_markers_json, weakness_tags_json, strength_tags_json, analysis_json, order_index, created_at, updated_at
            ) VALUES (
                (SELECT id FROM interview_record_questions WHERE interview_record_id = ? AND order_index = ?),
                NULL, NULL, ?, ?, ?, '[]', '["missing_thresholds"]', '[]', '{}', ?, now(), now()
            )
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            sourceInterviewRecordId,
            interviewRecordQuestionOrderIndex,
            "$summary raw answer",
            summary.lowercase(),
            summary,
            interviewRecordQuestionOrderIndex,
        )!!

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
                } else if (body.contains("Replay mode:")) {
                    if (english) {
                        "{\"promptText\":\"What exact rollback threshold would you commit to before escalating?\",\"bodyText\":\"Stay close to the imported interviewer style: push on rollback thresholds, communication timing, and operational pressure.\",\"tags\":[\"incident\",\"rollback\"],\"focusSkillNames\":[\"Incident Response\",\"Operational Judgment\"],\"resumeContextSummary\":null,\"resumeEvidence\":[],\"generationRationale\":\"The imported interviewer style was high-pressure and the answer still left rollback thresholds vague.\"}"
                    } else {
                        "{\"promptText\":\"에스컬레이션 전에 어떤 롤백 임계치를 기준으로 삼았나요?\",\"bodyText\":\"가져온 면접관 스타일을 유지해서 롤백 임계치, 커뮤니케이션 타이밍, 운영 압박을 더 집요하게 확인해 주세요.\",\"tags\":[\"장애\",\"롤백\"],\"focusSkillNames\":[\"장애 대응\",\"운영 판단\"],\"resumeContextSummary\":null,\"resumeEvidence\":[],\"generationRationale\":\"가져온 면접관 스타일이 압박형이고 답변에서도 롤백 임계치가 아직 모호했습니다.\"}"
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

            override fun postMultipart(
                url: String,
                apiKey: String,
                parts: Map<String, InterviewLlmMultipartPart>,
                timeout: Duration,
            ): String = throw UnsupportedOperationException("multipart transport not used in this test")
        }
    }
}
