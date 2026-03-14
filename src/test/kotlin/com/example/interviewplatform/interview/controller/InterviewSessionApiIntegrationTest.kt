package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.startsWith
import org.hamcrest.Matchers.nullValue
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
class InterviewSessionApiIntegrationTest {
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
            VALUES (1, 'session-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "session-user@example.com")}"
    }

    @Test
    fun `resume mock session selects resume matched questions`() {
        val categoryId = insertCategory("Backend")
        val questionId = insertQuestion("Explain Spring transaction boundaries", categoryId)
        val skillCategoryId = insertSkillCategory("backend_engineering", "Backend Engineering")
        val skillId = insertSkill(skillCategoryId, "Spring Boot")
        val tagId = insertTag("transactions")
        val resumeVersionId = insertResumeVersion()
        jdbcTemplate.update(
            """
            INSERT INTO resume_skill_snapshots (
                resume_version_id, skill_id, skill_name, source_text, confidence_score, is_confirmed, created_at, updated_at
            ) VALUES (?, ?, 'Spring Boot', 'Built APIs with Spring Boot', 91.0, true, now(), now())
            """.trimIndent(),
            resumeVersionId,
            skillId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at)
            VALUES (?, ?, 0.95, now())
            """.trimIndent(),
            questionId,
            skillId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_tags (question_id, tag_id, created_at)
            VALUES (?, ?, now())
            """.trimIndent(),
            questionId,
            tagId,
        )

        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
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
            .andExpect(jsonPath("$.sessionType").value("resume_mock"))
            .andExpect(jsonPath("$.resumeVersionId").value(resumeVersionId))
            .andExpect(jsonPath("$.currentQuestion.questionId").value(questionId))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value("Explain Spring transaction boundaries body"))
            .andExpect(jsonPath("$.currentQuestion.contentLocale").value(nullValue()))
            .andExpect(jsonPath("$.currentQuestion.tags[0]").value("transactions"))
            .andExpect(jsonPath("$.currentQuestion.focusSkillNames[0]").value(startsWith("Spring Boot")))
            .andExpect(jsonPath("$.currentQuestion.generationStatus").value("seeded"))
            .andExpect(jsonPath("$.questions[0].status").value("current"))
    }

    @Test
    fun `resume mock session requires explicit resume version`() {
        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `full coverage resume session creates evidence inventory and resume map`() {
        val resumeVersionId = insertResumeVersion()
        insertResumeProject(resumeVersionId)
        insertResumeExperience(resumeVersionId)
        insertResumeAward(resumeVersionId)
        insertResumeCertification(resumeVersionId)
        insertResumeEducation(resumeVersionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "interviewMode" to "full_coverage",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.interviewMode").value("full_coverage"))
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.currentQuestion.title").value("Payment platform migration를 어떤 문제와 맥락에서 진행했는지 구체적으로 설명해 주세요."))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(startsWith("이력서 근거: ")))
            .andExpect(jsonPath("$.currentQuestion.contentLocale").value("ko"))
            .andExpect(jsonPath("$.currentQuestion.resumeEvidence[0].sourceRecordType").value("resume_project_snapshot"))
            .andReturn()
            .response
            .contentAsString

        val sessionJson = objectMapper.readTree(sessionResponse)
        val sessionId = sessionJson.get("id").asLong()
        val sessionQuestionId = sessionJson.get("currentQuestion").get("id").asLong()

        mockMvc.perform(get("/api/interview-sessions/$sessionId/coverage").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overallCoveragePercent").value(lessThan(100)))
            .andExpect(jsonPath("$.evidenceItems.length()").value(greaterThanOrEqualTo(4)))
            .andExpect(jsonPath("$.facetSummaries[0].sourceRecordType").isString)
            .andExpect(jsonPath("$.facetSummaries[0].sourceRecordId").isNumber)
            .andExpect(jsonPath("$.facetSummaries[0].defendedFacets").isArray)
            .andExpect(jsonPath("$.evidenceItems[0].facet").isString)
            .andExpect(jsonPath("$.evidenceItems[0].sourceRecordType").value("resume_project_snapshot"))
            .andExpect(jsonPath("$.evidenceItems[0].sourceRecordId").isNumber)
            .andExpect(jsonPath("$.evidenceItems[0].displayOrder").value(1))
            .andExpect(jsonPath("$.evidenceItems[0].coverageStatus").value("asked"))
            .andExpect(jsonPath("$.evidenceItems[0].linkedQuestionIds[0]").value(sessionQuestionId))

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to sessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "I led the migration, staged rollout, monitored errors, and measured conversion lift after the release.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.facetSummaries").isArray)
            .andExpect(jsonPath("$.summary.weakFacetSummaries").isArray)
            .andExpect(jsonPath("$.summary.skippedFacetSummaries").isArray)

        mockMvc.perform(get("/api/interview-sessions/$sessionId/coverage").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overallCoveragePercent").value(lessThan(100)))
            .andExpect(jsonPath("$.defendedCoveragePercent").value(greaterThan(0)))
            .andExpect(jsonPath("$.weakFacetSummaries").isArray)

        mockMvc.perform(get("/api/interview-sessions/$sessionId/resume-map").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resumeVersionId").value(resumeVersionId))
            .andExpect(jsonPath("$.facetSummaries[0].sourceRecordType").isString)
            .andExpect(jsonPath("$.weakFacetSummaries").isArray)
            .andExpect(jsonPath("$.skippedFacetSummaries").isArray)
            .andExpect(jsonPath("$.evidenceItems[0].facet").isString)
            .andExpect(jsonPath("$.evidenceItems[0].displayOrder").value(1))
            .andExpect(jsonPath("$.evidenceItems[0].primaryQuestionCount").value(1))
            .andExpect(jsonPath("$.evidenceItems[0].followUpQuestionCount").value(0))
            .andExpect(jsonPath("$.evidenceItems[0].relatedQuestions[0].sessionQuestionId").value(sessionQuestionId))
            .andExpect(jsonPath("$.evidenceItems[0].relatedQuestions[0].orderIndex").value(1))
            .andExpect(jsonPath("$.evidenceItems[0].relatedQuestions[0].status").value("answered"))
            .andExpect(jsonPath("$.evidenceItems[0].relatedQuestions[0].isFollowUp").value(false))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.summary.facetSummaries[0].sourceRecordType").isString)
            .andExpect(jsonPath("$.summary.weakFacetSummaries").isArray)
            .andExpect(jsonPath("$.summary.skippedFacetSummaries").isArray)
    }

    @Test
    fun `full coverage next question generates uncovered resume question when none is queued`() {
        val resumeVersionId = insertResumeVersion()
        insertResumeProject(resumeVersionId)
        insertResumeExperience(resumeVersionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "interviewMode" to "full_coverage",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()
        val firstQuestionId = sessionResponse.get("currentQuestion").get("questionId").asLong()
        val answerAttemptId = insertAnswerAttempt(firstQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            answerAttemptId,
            firstSessionQuestionId,
        )
        jdbcTemplate.update(
            """
            UPDATE interview_session_evidence_items
            SET coverage_status = CASE WHEN display_order = 1 THEN 'defended' ELSE 'unasked' END,
                updated_at = now()
            WHERE interview_session_id = ?
            """.trimIndent(),
            sessionId,
        )

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.currentQuestion.id").isNumber)
            .andExpect(jsonPath("$.currentQuestion.id").value(org.hamcrest.Matchers.not(firstSessionQuestionId.toInt())))
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.summary.totalQuestions").value(2))

        mockMvc.perform(get("/api/interview-sessions/$sessionId/coverage").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overallCoveragePercent").value(lessThan(100)))
    }

    @Test
    fun `full coverage can continue after coverage reaches one hundred percent`() {
        val resumeVersionId = insertResumeVersion()
        insertResumeProject(resumeVersionId)
        insertResumeExperience(resumeVersionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "interviewMode" to "full_coverage",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()
        val firstQuestionId = sessionResponse.get("currentQuestion").get("questionId").asLong()
        val firstAnswerAttemptId = insertAnswerAttempt(firstQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            firstAnswerAttemptId,
            firstSessionQuestionId,
        )

        val secondAdvance = mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.summary.totalQuestions").value(2))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val secondSessionQuestionId = secondAdvance.get("currentQuestion").get("id").asLong()
        val secondQuestionId = secondAdvance.get("currentQuestion").get("questionId").asLong()
        val secondAnswerAttemptId = insertAnswerAttempt(secondQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            secondAnswerAttemptId,
            secondSessionQuestionId,
        )
        jdbcTemplate.update(
            """
            UPDATE interview_session_evidence_items
            SET coverage_status = 'defended',
                updated_at = now()
            WHERE interview_session_id = ?
            """.trimIndent(),
            sessionId,
        )

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.currentQuestion.id").isNumber)
            .andExpect(jsonPath("$.currentQuestion.id").value(org.hamcrest.Matchers.not(secondSessionQuestionId.toInt())))
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.currentQuestion.generationStatus").value("coverage_extended"))
            .andExpect(jsonPath("$.summary.totalQuestions").value(3))

        mockMvc.perform(get("/api/interview-sessions/$sessionId/coverage").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overallCoveragePercent").value(100))
    }

    @Test
    fun `full coverage prefers next facet sequence within the same record`() {
        val resumeVersionId = insertResumeVersion()
        insertResumeProject(resumeVersionId)
        insertResumeExperience(resumeVersionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "interviewMode" to "full_coverage",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()
        val firstQuestionId = sessionResponse.get("currentQuestion").get("questionId").asLong()
        val firstAnswerAttemptId = insertAnswerAttempt(firstQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            firstAnswerAttemptId,
            firstSessionQuestionId,
        )
        jdbcTemplate.update(
            """
            UPDATE interview_session_evidence_items
            SET source_record_type = 'resume_project_snapshot',
                source_record_id = 999,
                label = 'Facet Sequence Project',
                snippet = CASE
                    WHEN display_order = 1 THEN 'Problem facet snippet'
                    WHEN display_order = 2 THEN 'Action facet snippet'
                    WHEN display_order = 3 THEN 'Result facet snippet'
                    ELSE snippet
                END,
                facet = CASE
                    WHEN display_order = 1 THEN 'problem'
                    WHEN display_order = 2 THEN 'action'
                    WHEN display_order = 3 THEN 'result'
                    ELSE facet
                END,
                coverage_status = CASE
                    WHEN display_order = 1 THEN 'defended'
                    WHEN display_order IN (2, 3) THEN 'unasked'
                    ELSE 'defended'
                END,
                updated_at = now()
            WHERE interview_session_id = ?
            """.trimIndent(),
            sessionId,
        )

        val secondAdvance = mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(startsWith("이력서 근거: Action facet snippet")))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(org.hamcrest.Matchers.containsString("실제 구현 단계")))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val secondSessionQuestionId = secondAdvance.get("currentQuestion").get("id").asLong()
        val secondQuestionId = secondAdvance.get("currentQuestion").get("questionId").asLong()
        val secondAnswerAttemptId = insertAnswerAttempt(secondQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            secondAnswerAttemptId,
            secondSessionQuestionId,
        )
        jdbcTemplate.update(
            """
            UPDATE interview_session_evidence_items
            SET coverage_status = CASE
                WHEN display_order IN (1, 2) THEN 'defended'
                WHEN display_order = 3 THEN 'unasked'
                ELSE 'defended'
            END,
                updated_at = now()
            WHERE interview_session_id = ?
            """.trimIndent(),
            sessionId,
        )

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(startsWith("이력서 근거: Result facet snippet")))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(org.hamcrest.Matchers.containsString("어떤 방식으로 검증했는지")))
    }

    @Test
    fun `full coverage revisits weak facet before defended facet after one hundred percent coverage`() {
        val resumeVersionId = insertResumeVersion()
        insertResumeProject(resumeVersionId)
        insertResumeExperience(resumeVersionId)

        val sessionResponse = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "resume_mock",
                            "interviewMode" to "full_coverage",
                            "questionCount" to 1,
                            "resumeVersionId" to resumeVersionId,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()
        val firstQuestionId = sessionResponse.get("currentQuestion").get("questionId").asLong()
        val firstAnswerAttemptId = insertAnswerAttempt(firstQuestionId)

        jdbcTemplate.update(
            """
            UPDATE interview_session_questions
            SET answer_attempt_id = ?, updated_at = now()
            WHERE id = ?
            """.trimIndent(),
            firstAnswerAttemptId,
            firstSessionQuestionId,
        )
        jdbcTemplate.update(
            """
            UPDATE interview_session_evidence_items
            SET source_record_type = CASE
                    WHEN display_order IN (1, 2) THEN 'resume_project_snapshot'
                    ELSE source_record_type
                END,
                source_record_id = CASE
                    WHEN display_order IN (1, 2) THEN 777
                    ELSE source_record_id
                END,
                label = CASE
                    WHEN display_order = 1 THEN 'Weak Metric Facet Project'
                    WHEN display_order = 2 THEN 'Defended Problem Facet Project'
                    ELSE label
                END,
                snippet = CASE
                    WHEN display_order = 1 THEN 'Weak metric facet snippet'
                    WHEN display_order = 2 THEN 'Defended problem facet snippet'
                    ELSE snippet
                END,
                facet = CASE
                    WHEN display_order = 1 THEN 'metric'
                    WHEN display_order = 2 THEN 'problem'
                    ELSE facet
                END,
                coverage_status = CASE
                    WHEN display_order = 1 THEN 'weak'
                    WHEN display_order = 2 THEN 'defended'
                    ELSE 'defended'
                END,
                updated_at = now()
            WHERE interview_session_id = ?
            """.trimIndent(),
            sessionId,
        )

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.currentQuestion.sourceType").value("coverage_planner"))
            .andExpect(jsonPath("$.currentQuestion.generationStatus").value("coverage_extended"))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(startsWith("이력서 근거: Weak metric facet snippet")))
            .andExpect(jsonPath("$.currentQuestion.bodyText").value(org.hamcrest.Matchers.containsString("어떤 지표를 봤는지")))
    }

    @Test
    fun `next question rejects advancing while current question is unanswered`() {
        val categoryId = insertCategory("Advance Guard")
        val questionId = insertQuestion("Explain how you debug latency spikes", categoryId)
        val sessionResponse = createTopicSession(listOf(questionId))
        val sessionId = sessionResponse.get("id").asLong()
        val currentQuestionId = sessionResponse.get("currentQuestion").get("id").asLong()

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.message").value("Current question must be answered or skipped before advancing: $currentQuestionId"))
    }

    @Test
    fun `skip question marks current question skipped and advances to next one`() {
        val categoryId = insertCategory("Skip Flow")
        val firstQuestionId = insertQuestion("Describe a large migration you led", categoryId)
        val secondQuestionId = insertQuestion("How did you handle rollout risk", categoryId)

        val sessionResponse = createTopicSession(listOf(firstQuestionId, secondQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()
        val secondSessionQuestionId = sessionResponse.get("questions")[1].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/skip-question")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("sessionQuestionId" to firstSessionQuestionId),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.currentQuestion.id").value(secondSessionQuestionId))
            .andExpect(jsonPath("$.summary.totalQuestions").value(2))
            .andExpect(jsonPath("$.summary.answeredQuestions").value(0))
            .andExpect(jsonPath("$.summary.skippedQuestions").value(1))
            .andExpect(jsonPath("$.summary.remainingQuestions").value(1))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].status").value("skipped"))
            .andExpect(jsonPath("$.questions[1].status").value("current"))
            .andExpect(jsonPath("$.summary.skippedQuestions").value(1))
    }

    @Test
    fun `review mock session prioritizes pending review questions`() {
        val categoryId = insertCategory("Distributed Systems")
        val questionId = insertQuestion("Explain retry backoff", categoryId)
        val answerAttemptId = insertAnswerAttempt(questionId)
        jdbcTemplate.update(
            """
            INSERT INTO review_queue (
                user_id, question_id, trigger_answer_attempt_id, reason_type, priority, scheduled_for, status, created_at, updated_at
            ) VALUES (1, ?, ?, 'low_score', 9, now() - interval '1 day', 'pending', now(), now())
            """.trimIndent(),
            questionId,
            answerAttemptId,
        )

        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "review_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.currentQuestion.questionId").value(questionId))
            .andExpect(jsonPath("$.questions[0].status").value("current"))
    }

    @Test
    fun `list sessions returns session history summaries`() {
        val categoryId = insertCategory("History")
        val firstQuestionId = insertQuestion("Explain idempotency", categoryId)
        val secondQuestionId = insertQuestion("Explain retries", categoryId)

        val sessionResponse = createTopicSession(listOf(firstQuestionId, secondQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to firstSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "We used dedupe keys, idempotency tokens, and replay-safe consumers.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/interview-sessions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(sessionId))
            .andExpect(jsonPath("$[0].sessionType").value("topic_mock"))
            .andExpect(jsonPath("$[0].questionCount").value(2))
            .andExpect(jsonPath("$[0].answeredCount").value(1))
            .andExpect(jsonPath("$[0].averageScore").isNumber)
    }

    @Test
    fun `session answers progress and complete session`() {
        val categoryId = insertCategory("Architecture")
        val firstQuestionId = insertQuestion("Design idempotent jobs", categoryId)
        val secondQuestionId = insertQuestion("Explain queue backpressure", categoryId)

        val sessionResponse = createTopicSession(listOf(firstQuestionId, secondQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()
        val secondSessionQuestionId = sessionResponse.get("questions")[1].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to firstSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "I improved reliability by 30 percent because we added idempotency keys and explicit dedupe checks.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("in_progress"))
            .andExpect(jsonPath("$.nextQuestion.id").value(secondSessionQuestionId))
            .andExpect(jsonPath("$.summary.answeredQuestions").value(1))

        mockMvc.perform(post("/api/interview-sessions/$sessionId/next-question").header("Authorization", authHeader))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.message").value("Current question must be answered or skipped before advancing: $secondSessionQuestionId"))

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to secondSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "We controlled producer rate, bounded queue depth, and scaled consumers based on lag metrics.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.nextQuestion").value(nullValue()))
            .andExpect(jsonPath("$.summary.answeredQuestions").value(2))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.currentQuestion").value(nullValue()))
            .andExpect(jsonPath("$.questions[0].status").value("answered"))
            .andExpect(jsonPath("$.questions[1].status").value("answered"))
            .andExpect(jsonPath("$.summary.averageScore").isNumber)
    }

    @Test
    fun `answering a session question inserts follow up and stores interview source metadata`() {
        val categoryId = insertCategory("Follow Up")
        val parentQuestionId = insertQuestion("Describe a difficult production incident", categoryId)
        val childQuestionId = insertQuestion("What metrics did you watch during mitigation", categoryId)
        jdbcTemplate.update(
            """
            INSERT INTO question_relationships (
                parent_question_id, child_question_id, relationship_type, depth, display_order, created_at
            ) VALUES (?, ?, 'follow_up', 1, 1, now())
            """.trimIndent(),
            parentQuestionId,
            childQuestionId,
        )

        val sessionResponse = createTopicSession(listOf(parentQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val parentSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to parentSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "We reduced blast radius, watched saturation, error rate, and queue lag while rolling back.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextQuestion.isFollowUp").value(true))
            .andExpect(jsonPath("$.nextQuestion.parentSessionQuestionId").value(parentSessionQuestionId))
            .andExpect(jsonPath("$.nextQuestion.sourceType").value("catalog_follow_up"))
            .andExpect(jsonPath("$.nextQuestion.bodyText").value("What metrics did you watch during mitigation body"))
            .andExpect(jsonPath("$.nextQuestion.generationStatus").value("catalog_follow_up"))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[1].questionId").value(childQuestionId))
            .andExpect(jsonPath("$.questions[1].isFollowUp").value(true))
            .andExpect(jsonPath("$.questions[1].depth").value(1))

        jdbcTemplate.queryForMap(
            """
            SELECT source_type, source_label, source_session_id, source_session_question_id, is_follow_up
            FROM user_question_progress
            WHERE user_id = 1 AND question_id = ?
            """.trimIndent(),
            parentQuestionId,
        ).also { row ->
            org.junit.jupiter.api.Assertions.assertEquals("interview", row["source_type"])
            org.junit.jupiter.api.Assertions.assertEquals("Interview", row["source_label"])
            org.junit.jupiter.api.Assertions.assertEquals(sessionId, (row["source_session_id"] as Number).toLong())
            org.junit.jupiter.api.Assertions.assertEquals(parentSessionQuestionId, (row["source_session_question_id"] as Number).toLong())
            org.junit.jupiter.api.Assertions.assertEquals(false, row["is_follow_up"])
        }
    }

    @Test
    fun `answering the first session question shifts later rows before inserting follow up`() {
        val categoryId = insertCategory("Ordered Follow Up")
        val firstQuestionId = insertQuestion("Walk through an outage you handled", categoryId)
        val followUpQuestionId = insertQuestion("How did you decide between rollback and mitigation", categoryId)
        val secondQuestionId = insertQuestion("Explain your alerting strategy", categoryId)
        val thirdQuestionId = insertQuestion("Describe your postmortem process", categoryId)
        jdbcTemplate.update(
            """
            INSERT INTO question_relationships (
                parent_question_id, child_question_id, relationship_type, depth, display_order, created_at
            ) VALUES (?, ?, 'follow_up', 1, 1, now())
            """.trimIndent(),
            firstQuestionId,
            followUpQuestionId,
        )

        val sessionResponse = createTopicSession(listOf(firstQuestionId, secondQuestionId, thirdQuestionId))
        val sessionId = sessionResponse.get("id").asLong()
        val firstSessionQuestionId = sessionResponse.get("questions")[0].get("id").asLong()
        val secondSessionQuestionId = sessionResponse.get("questions")[1].get("id").asLong()
        val thirdSessionQuestionId = sessionResponse.get("questions")[2].get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionQuestionId" to firstSessionQuestionId,
                            "answerMode" to "text",
                            "contentText" to "We checked blast radius, rollback cost, and system saturation before deciding.\n".repeat(5),
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextQuestion.questionId").value(followUpQuestionId))
            .andExpect(jsonPath("$.nextQuestion.orderIndex").value(2))

        mockMvc.perform(get("/api/interview-sessions/$sessionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.questions[0].id").value(firstSessionQuestionId))
            .andExpect(jsonPath("$.questions[0].orderIndex").value(1))
            .andExpect(jsonPath("$.questions[1].questionId").value(followUpQuestionId))
            .andExpect(jsonPath("$.questions[1].orderIndex").value(2))
            .andExpect(jsonPath("$.questions[2].id").value(secondSessionQuestionId))
            .andExpect(jsonPath("$.questions[2].orderIndex").value(3))
            .andExpect(jsonPath("$.questions[3].id").value(thirdSessionQuestionId))
            .andExpect(jsonPath("$.questions[3].orderIndex").value(4))
    }

    @Test
    fun `create session rejects unsupported type`() {
        mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "voice_mock",
                            "questionCount" to 1,
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `session answer validates required session question id`() {
        val categoryId = insertCategory("Validation")
        val questionId = insertQuestion("Explain validation boundaries", categoryId)
        val sessionResponse = createTopicSession(listOf(questionId))
        val sessionId = sessionResponse.get("id").asLong()

        mockMvc.perform(
            post("/api/interview-sessions/$sessionId/answers")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "answerMode" to "text",
                            "contentText" to "Missing session question id",
                        ),
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
    }

    private fun createTopicSession(questionIds: List<Long>): JsonNode {
        val response = mockMvc.perform(
            post("/api/interview-sessions")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "sessionType" to "topic_mock",
                            "questionCount" to questionIds.size,
                            "seedQuestionIds" to questionIds,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)
    }

    private fun insertCategory(name: String): Long {
        return jdbcTemplate.queryForObject(
            "INSERT INTO categories (name, created_at) VALUES (?, now()) RETURNING id",
            Long::class.java,
            "$name-${System.nanoTime()}",
        )
    }

    private fun insertQuestion(title: String, categoryId: Long): Long {
        return jdbcTemplate.queryForObject(
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
    }

    private fun insertSkillCategory(code: String, name: String): Long {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO skill_categories (code, name, display_order, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            "$code-${System.nanoTime()}",
            "$name-${System.nanoTime()}",
            999,
        )
    }

    private fun insertSkill(skillCategoryId: Long, name: String): Long {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            VALUES (?, ?, 'Skill for tests', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            skillCategoryId,
            "$name-${System.nanoTime()}",
        )
    }

    private fun insertTag(name: String): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO tags (name, tag_type, created_at)
            VALUES (?, 'topic', now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            name,
        )

    private fun insertResumeVersion(): Long {
        val resumeId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resumes (id, user_id, title, is_primary, created_at, updated_at)
            VALUES (DEFAULT, 1, 'Session Resume', true, now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (
                resume_id, version_no, file_url, raw_text, parsed_json, summary_text, is_active, uploaded_at, created_at,
                file_name, file_type, parsing_status
            ) VALUES (?, 1, 'https://files.example.com/session.pdf', 'resume text', '{}', 'summary', true, now(), now(),
                'session.pdf', 'application/pdf', 'completed')
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        )
    }

    private fun insertResumeProject(resumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_project_snapshots (
                resume_version_id, title, organization_name, role_name, summary_text, content_text,
                project_category_code, project_category_name, tech_stack_text, display_order, source_text, created_at, updated_at
            ) VALUES (
                ?, 'Payment platform migration', 'Example Corp', 'Backend Engineer',
                'Migrated the payment platform without downtime',
                'Led a staged payment migration, reduced risk with feature flags, and improved conversion after the rollout.',
                'backend_platform', 'Backend Platform', 'Kotlin, Spring Boot, PostgreSQL', 1,
                'Led a staged payment migration, reduced risk with feature flags, and improved conversion after the rollout.',
                now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeVersionId,
        )

    private fun insertResumeAward(resumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_award_items (
                resume_version_id, title, issuer_name, description, display_order, source_text, created_at, updated_at
            ) VALUES (
                ?, 'Engineering Excellence Award', 'Example Corp',
                'Recognized for leading a high-impact platform migration', 2,
                'Recognized for leading a high-impact platform migration',
                now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeVersionId,
        )

    private fun insertResumeExperience(resumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_experience_snapshots (
                resume_version_id, company_name, role_name, employment_type, is_current, project_name,
                summary_text, impact_text, source_text, risk_level, display_order, is_confirmed, created_at, updated_at
            ) VALUES (
                ?, 'Example Corp', 'Backend Engineer', 'full_time', true, 'Payments Platform',
                'Owned the payments platform migration and release strategy',
                'Reduced rollback risk and improved conversion through staged rollout',
                'Owned the payments platform migration and release strategy',
                'MEDIUM', 2, true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeVersionId,
        )

    private fun insertResumeCertification(resumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_certification_items (
                resume_version_id, name, issuer_name, credential_code, score_text, display_order, source_text, created_at, updated_at
            ) VALUES (
                ?, 'AWS Solutions Architect Associate', 'Amazon', 'AWS-SAA-123', 'Pass', 3,
                'AWS Solutions Architect Associate',
                now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeVersionId,
        )

    private fun insertResumeEducation(resumeVersionId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_education_items (
                resume_version_id, institution_name, degree_name, field_of_study, description, display_order, source_text, created_at, updated_at
            ) VALUES (
                ?, 'Example University', 'B.S.', 'Computer Science',
                'Studied distributed systems and database internals', 4,
                'Studied distributed systems and database internals',
                now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeVersionId,
        )

    private fun insertAnswerAttempt(questionId: Long): Long {
        val answerAttemptId = jdbcTemplate.queryForObject(
            """
            INSERT INTO answer_attempts (
                user_id, question_id, resume_version_id, source_daily_card_id, attempt_no, answer_mode, content_text, submitted_at, created_at
            ) VALUES (1, ?, NULL, NULL, 1, 'text', 'baseline answer', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            questionId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO answer_scores (
                answer_attempt_id, total_score, structure_score, specificity_score, technical_accuracy_score, role_fit_score,
                company_fit_score, communication_score, evaluation_result, evaluated_at
            ) VALUES (?, 45, 45, 45, 45, 45, 45, 45, 'FAIL', now())
            """.trimIndent(),
            answerAttemptId,
        )
        return answerAttemptId
    }
}
