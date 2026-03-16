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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
            .andExpect(jsonPath("$.totalSegmentCount").value(4))
            .andExpect(jsonPath("$.editedSegmentCount").value(0))
            .andExpect(jsonPath("$.totalQuestionCount").value(2))
            .andExpect(jsonPath("$.changedQuestionCount").value(0))
            .andExpect(jsonPath("$.weakAnswerCount").value(2))
            .andExpect(jsonPath("$.followUpQuestionCount").value(1))
            .andExpect(jsonPath("$.questionSourceCounts.deterministic").value(2))
            .andExpect(jsonPath("$.answerSourceCounts.deterministic").value(2))
            .andExpect(jsonPath("$.questionFilterSummary.allQuestions").value(2))
            .andExpect(jsonPath("$.questionFilterSummary.primaryQuestions").value(1))
            .andExpect(jsonPath("$.questionFilterSummary.followUpQuestions").value(1))
            .andExpect(jsonPath("$.questionFilterSummary.weakAnswerQuestions").value(2))
            .andExpect(jsonPath("$.questionFilterSummary.weakFollowUpQuestions").value(1))
            .andExpect(jsonPath("$.questionFilterSummary.confirmedQuestions").value(0))
            .andExpect(jsonPath("$.questionDistributionSummary.questionTypeCounts.project").value(1))
            .andExpect(jsonPath("$.questionDistributionSummary.questionTypeCounts.technical_depth").value(1))
            .andExpect(jsonPath("$.questionDistributionSummary.topicTagCounts.redis").exists())
            .andExpect(jsonPath("$.questionOriginSummary.generalQuestions").value(2))
            .andExpect(jsonPath("$.questionOriginSummary.resumeLinkedQuestions").value(0))
            .andExpect(jsonPath("$.questionOriginSummary.jobPostingLinkedQuestions").value(0))
            .andExpect(jsonPath("$.questionOriginSummary.hybridLinkedQuestions").value(0))
            .andExpect(jsonPath("$.replayReadiness.ready").value(true))
            .andExpect(jsonPath("$.replayReadiness.replayableQuestionCount").value(2))
            .andExpect(jsonPath("$.replayReadiness.linkedQuestionCount").value(2))
            .andExpect(jsonPath("$.replayReadiness.unlinkedQuestionCount").value(0))
            .andExpect(jsonPath("$.replayReadiness.followUpThreadCount").value(1))
            .andExpect(jsonPath("$.replayReadiness.hasInterviewerProfile").value(true))
            .andExpect(jsonPath("$.replayReadiness.recommendedReplayMode").value("original_replay"))
            .andExpect(jsonPath("$.replayReadiness.blockers.length()").value(0))
            .andExpect(jsonPath("$.transcriptIssueSummary.lowConfidenceSegmentCount").value(0))
            .andExpect(jsonPath("$.transcriptIssueSummary.speakerOverrideSegmentCount").value(0))
            .andExpect(jsonPath("$.transcriptIssueSummary.confirmedTextOverrideCount").value(0))
            .andExpect(jsonPath("$.transcriptIssueSummary.editedSegmentSequences.length()").value(0))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.totalCount").value(0))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.sortOrder").value(3))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.highlightVariant").value("neutral"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.badgeText").value("Ready"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.summaryText").value("Transcript not available yet."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.recommendedTab").value("issues"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.defaultExpanded").value(false))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.analyticsKey").value("practical_review_lane_transcript"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.trackingContext.laneKey").value("transcript"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.trackingContext.primaryAction").value("confirm"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.helpText").value("Check transcript accuracy, speaker attribution, and confirmed edits."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.whyItMatters").value("Transcript issues can distort downstream question, answer, and replay analysis."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.accessibilityLabel").value("Transcript review lane"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.screenReaderSummary").value("Transcript lane is ready."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.readiness").value("ready"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.severity").value("low"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.highestPriority").value("p2"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryAction").value("confirm"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionLabel").value("Confirm review"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionTarget").value("review:confirm"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionTargetPayload.panel").value("confirm_review"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryAction").value("start_replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryActionLabel").value("Start replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryActionTarget").value("review:replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryActionTargetPayload.panel").value("replay_launch"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateMessage").value("No transcript issues detected."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaAction").value("start_replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaLabel").value("Start replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaTarget").value("review:replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaTargetPayload.focus").value("recommended_replay_mode"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionMessage").isEmpty())
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.blockingReasons.length()").value(0))
            .andExpect(jsonPath("$.reviewLaneSummary.question.totalCount").value(2))
            .andExpect(jsonPath("$.reviewLaneSummary.question.sortOrder").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.question.highlightVariant").value("warning"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.badgeText").value("Needs review"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.summaryText").value("2 of 2 require review."))
            .andExpect(jsonPath("$.reviewLaneSummary.question.recommendedTab").value("questions"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.defaultExpanded").value(true))
            .andExpect(jsonPath("$.reviewLaneSummary.question.analyticsKey").value("practical_review_lane_question"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.trackingContext.laneKey").value("question"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.trackingContext.primaryAction").value("review_answers"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.helpText").value("Review imported questions and answer quality before confirming the record."))
            .andExpect(jsonPath("$.reviewLaneSummary.question.whyItMatters").value("Question and answer review determines what becomes a reliable study asset."))
            .andExpect(jsonPath("$.reviewLaneSummary.question.accessibilityLabel").value("Question review lane"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.screenReaderSummary").value("Questions lane needs review for 2 items, priority p1."))
            .andExpect(jsonPath("$.reviewLaneSummary.question.needsReviewCount").value(2))
            .andExpect(jsonPath("$.reviewLaneSummary.question.severity").value("medium"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.highestPriority").value("p1"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.primaryAction").value("review_answers"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.primaryActionLabel").value("Review answers"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.primaryActionTarget").value("question:questions"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.primaryActionTargetPayload.filter").value("needs_review"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.secondaryAction").value("confirm"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.secondaryActionLabel").value("Confirm review"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.secondaryActionTarget").value("review:confirm"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.secondaryActionTargetPayload.focus").value("confirmation_cta"))
            .andExpect(jsonPath("$.reviewLaneSummary.question.emptyStateCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.emptyStateCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.emptyStateCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.completionCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.completionCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.completionCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.question.blockingReasons[0]").value("weak_answers_present"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.totalCount").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.sortOrder").value(2))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.highlightVariant").value("warning"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.badgeText").value("Needs review"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.summaryText").value("1 of 1 require review."))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.recommendedTab").value("threads"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.defaultExpanded").value(false))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.analyticsKey").value("practical_review_lane_thread"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.trackingContext.laneKey").value("thread"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.trackingContext.primaryAction").value("review_weak_chain"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.helpText").value("Inspect follow-up chains to find weak probing paths and replay targets."))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.whyItMatters").value("Thread review shows whether the practical interview had strong probing depth."))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.accessibilityLabel").value("Thread review lane"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.screenReaderSummary").value("Threads lane needs review for 1 items, priority p1."))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.needsReviewCount").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.severity").value("medium"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.highestPriority").value("p1"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.primaryAction").value("review_weak_chain"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.primaryActionLabel").value("Review weak chain"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.primaryActionTarget").value("thread:weak_threads"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.primaryActionTargetPayload.filter").value("weak"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.secondaryAction").value("replay_chain"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.secondaryActionLabel").value("Replay this chain"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.secondaryActionTarget").value("thread:replay"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.secondaryActionTargetPayload.focus").value("first_replayable_thread"))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.emptyStateMessage").isEmpty())
            .andExpect(jsonPath("$.reviewLaneSummary.thread.emptyStateCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.emptyStateCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.emptyStateCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.completionMessage").isEmpty())
            .andExpect(jsonPath("$.reviewLaneSummary.thread.completionCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.completionCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.completionCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.thread.blockingReasons[0]").value("weak_threads_present"))
            .andExpect(jsonPath("$.answerQualitySummary.answeredQuestionCount").value(2))
            .andExpect(jsonPath("$.answerQualitySummary.weakAnswerCount").value(2))
            .andExpect(jsonPath("$.answerQualitySummary.strengthTaggedAnswerCount").value(2))
            .andExpect(jsonPath("$.answerQualitySummary.quantifiedAnswerCount").value(2))
            .andExpect(jsonPath("$.answerQualitySummary.structuredAnswerCount").value(0))
            .andExpect(jsonPath("$.answerQualitySummary.tradeoffAwareAnswerCount").value(0))
            .andExpect(jsonPath("$.answerQualitySummary.uncertainAnswerCount").value(0))
            .andExpect(jsonPath("$.answerQualitySummary.detailedAnswerCount").value(0))
            .andExpect(jsonPath("$.timelineNavigation.items.length()").value(2))
            .andExpect(jsonPath("$.timelineNavigation.items[0].threadRootQuestionId").exists())
            .andExpect(jsonPath("$.timelineNavigation.items[0].questionSegmentStartSequence").value(1))
            .andExpect(jsonPath("$.timelineNavigation.items[0].answerSegmentStartSequence").value(2))
            .andExpect(jsonPath("$.timelineNavigation.items[1].parentQuestionId").exists())
            .andExpect(jsonPath("$.timelineNavigation.items[1].threadRootQuestionId").value(org.hamcrest.Matchers.notNullValue()))
            .andExpect(jsonPath("$.actionRecommendations.primaryAction").value("confirm"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionLabel").value("Confirm review"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTarget").value("review:confirm"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTargetPayload.panel").value("confirm_review"))
            .andExpect(jsonPath("$.actionRecommendations.availableActions[0]").exists())
            .andExpect(jsonPath("$.actionRecommendations.availableActionTargets.confirm").value("review:confirm"))
            .andExpect(jsonPath("$.actionRecommendations.availableActionTargetPayloads.confirm.focus").value("confirmation_cta"))
            .andExpect(jsonPath("$.actionRecommendations.availableActionTargets.start_replay").value("review:replay"))
            .andExpect(jsonPath("$.actionRecommendations.canConfirm").value(true))
            .andExpect(jsonPath("$.actionRecommendations.canReplay").value(true))
            .andExpect(jsonPath("$.actionRecommendations.blockingReasons.length()").value(0))
            .andExpect(jsonPath("$.replayLaunchPreset.sessionType").value("replay_mock"))
            .andExpect(jsonPath("$.replayLaunchPreset.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.replayLaunchPreset.replayMode").value("original_replay"))
            .andExpect(jsonPath("$.replayLaunchPreset.recommendedReplayModeLabel").value("Original replay"))
            .andExpect(jsonPath("$.replayLaunchPreset.recommendedQuestionCount").value(2))
            .andExpect(jsonPath("$.replayLaunchPreset.seedQuestionIds.length()").value(1))
            .andExpect(jsonPath("$.replayLaunchPreset.availableReplayModes.length()").value(3))
            .andExpect(jsonPath("$.replayLaunchPreset.availableReplayModeLabels.original_replay").value("Original replay"))
            .andExpect(jsonPath("$.replayLaunchPreset.presetTitle").value("Replay this interview pattern"))
            .andExpect(jsonPath("$.replayLaunchPreset.launchButtonLabel").value("Start recommended replay"))
            .andExpect(jsonPath("$.provenanceComparisonSummary.aiRefinementApplied").value(false))
            .andExpect(jsonPath("$.provenanceComparisonSummary.confirmedVersionAvailable").value(false))
            .andExpect(jsonPath("$.provenanceComparisonSummary.summaryChangedFromDeterministic").value(false))
            .andExpect(jsonPath("$.provenanceComparisonSummary.changedQuestionCountFromDeterministic").value(0))
            .andExpect(jsonPath("$.provenanceComparisonSummary.changedAnswerCountFromDeterministic").value(0))
            .andExpect(jsonPath("$.provenanceComparisonSummary.currentQuestionSource").value("deterministic"))
            .andExpect(jsonPath("$.provenanceComparisonSummary.currentAnswerSource").value("deterministic"))
            .andExpect(jsonPath("$.questionSummaries.length()").value(2))
            .andExpect(jsonPath("$.questionSummaries[0].deepLink.archiveSourceType").value("real_interview"))
            .andExpect(jsonPath("$.questionSummaries[0].deepLink.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.questionSummaries[0].deepLink.sourceInterviewQuestionId").exists())
            .andExpect(jsonPath("$.questionSummaries[0].deepLink.canStartReplayMock").value(true))
            .andExpect(jsonPath("$.questionSummaries[0].deepLink.replaySessionType").value("replay_mock"))
            .andExpect(jsonPath("$.questionSummaries[0].topicTags[0]").exists())
            .andExpect(jsonPath("$.questionSummaries[0].originType").value("general"))
            .andExpect(jsonPath("$.questionSummaries[0].isFollowUp").value(false))
            .andExpect(jsonPath("$.questionSummaries[1].isFollowUp").value(true))
            .andExpect(jsonPath("$.questionSummaries[1].hasWeakAnswer").value(true))
            .andExpect(jsonPath("$.questionSummaries[1].weaknessTags[0]").exists())
            .andExpect(jsonPath("$.questionSummaries[0].confidenceMarkers[0]").value("quantified"))
            .andExpect(jsonPath("$.followUpThreads.length()").value(1))
            .andExpect(jsonPath("$.followUpThreads[0].questionIds.length()").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].followUpCount").value(1))
            .andExpect(jsonPath("$.followUpThreads[0].weakQuestionCount").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].answeredQuestionCount").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].quantifiedQuestionCount").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].recommendedAction").value("review_weak_chain"))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.sessionType").value("replay_mock"))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.recommendedReplayModeLabel").value("Original replay"))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.recommendedQuestionCount").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.seedQuestionIds.length()").value(2))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.presetTitle").value("Replay this follow-up chain"))
            .andExpect(jsonPath("$.followUpThreads[0].replayLaunchPreset.launchButtonLabel").value("Replay this chain"))
            .andExpect(jsonPath("$.followUpThreads[0].structuredQuestionCount").value(0))
            .andExpect(jsonPath("$.followUpThreads[0].tradeoffAwareQuestionCount").value(0))
            .andExpect(jsonPath("$.followUpThreads[0].uncertainQuestionCount").value(0))

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
            .andExpect(jsonPath("$.changedQuestionCount").value(2))
            .andExpect(jsonPath("$.questionFilterSummary.confirmedQuestions").value(2))
            .andExpect(jsonPath("$.questionDistributionSummary.questionTypeCounts.project").exists())
            .andExpect(jsonPath("$.questionOriginSummary.generalQuestions").value(2))
            .andExpect(jsonPath("$.replayReadiness.ready").value(true))
            .andExpect(jsonPath("$.transcriptIssueSummary.confirmedTextOverrideCount").value(0))
            .andExpect(jsonPath("$.answerQualitySummary.answeredQuestionCount").value(2))
            .andExpect(jsonPath("$.timelineNavigation.items.length()").value(2))
            .andExpect(jsonPath("$.actionRecommendations.primaryAction").value("start_replay"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionLabel").value("Start replay"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTarget").value("review:replay"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTargetPayload.panel").value("replay_launch"))
            .andExpect(jsonPath("$.actionRecommendations.canConfirm").value(false))
            .andExpect(jsonPath("$.replayLaunchPreset.sessionType").value("replay_mock"))
            .andExpect(jsonPath("$.provenanceComparisonSummary.confirmedVersionAvailable").value(true))
            .andExpect(jsonPath("$.provenanceComparisonSummary.changedQuestionCountFromDeterministic").value(2))
            .andExpect(jsonPath("$.provenanceComparisonSummary.changedAnswerCountFromDeterministic").value(2))
            .andExpect(jsonPath("$.provenanceComparisonSummary.currentQuestionSource").value("confirmed"))
            .andExpect(jsonPath("$.provenanceComparisonSummary.currentAnswerSource").value("confirmed"))
            .andExpect(jsonPath("$.questionSourceCounts.confirmed").value(2))
            .andExpect(jsonPath("$.answerSourceCounts.confirmed").value(2))
            .andExpect(jsonPath("$.questionSummaries[0].questionStructuringSource").value("confirmed"))
            .andExpect(jsonPath("$.questionSummaries[0].answerStructuringSource").value("confirmed"))
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
    fun `review exposes transcript issue summary for pending transcript overrides`() {
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
        val firstSegmentId = transcript.get("segments")[0].get("id").asLong()

        mockMvc.perform(
            patch("/api/interview-records/$recordId/transcript/segments/$firstSegmentId")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "speakerType" to "candidate",
                            "confirmedText" to "Tell me about the migration project and rollout metrics?",
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/interview-records/$recordId/review").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transcriptIssueSummary.speakerOverrideSegmentCount").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.speakerOverrideSegmentSequences[0]").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.confirmedTextOverrideCount").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.editedSegmentSequences[0]").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.resolvedIssueCount").value(0))
            .andExpect(jsonPath("$.transcriptIssueSummary.unresolvedIssueCount").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.confirmationReadiness").value("needs_review"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.totalCount").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.sortOrder").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.highlightVariant").value("danger"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.badgeText").value("Needs review"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.summaryText").value("1 of 1 require review."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.recommendedTab").value("issues"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.defaultExpanded").value(true))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.analyticsKey").value("practical_review_lane_transcript"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.trackingContext.highlightVariant").value("danger"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.accessibilityLabel").value("Transcript review lane"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.screenReaderSummary").value("Transcript lane needs review for 1 items, priority p0."))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.needsReviewCount").value(1))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.readiness").value("needs_review"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.severity").value("high"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.highestPriority").value("p0"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryAction").value("review_transcript"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionLabel").value("Review transcript"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionTarget").value("transcript:issues"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.primaryActionTargetPayload.focus").value("top_priority_issue"))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryActionTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.secondaryActionTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateMessage").isEmpty())
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.emptyStateCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionMessage").isEmpty())
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaAction").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaTarget").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.completionCtaTargetPayload").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.reviewLaneSummary.transcript.blockingReasons[0]").value("pending_transcript_edits"))
            .andExpect(jsonPath("$.transcriptIssueSummary.reviewerLaneCounts.transcript_review").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.topPrioritySegmentActions[0].sequence").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.topPrioritySegmentActions[0].priority").value("p0"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].sequence").value(1))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].issueTypes[0]").value("speaker_override"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].issueTypes[1]").value("confirmed_override"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].recommendedAction").value("review_now"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].triageReason").isString)
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].ctaLabel").value("Review transcript edit"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].severity").value("high"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].priority").value("p0"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].reviewerLane").value("transcript_review"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].linkedQuestionId").isNumber)
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].threadRootQuestionId").isNumber)
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].deepLink.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].deepLink.sourceInterviewQuestionId").isNumber)
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].deepLink.archiveSourceType").value("real_interview"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].replayLaunchPreset.sessionType").value("replay_mock"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].replayLaunchPreset.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].replayLaunchPreset.recommendedReplayModeLabel").value("Original replay"))
            .andExpect(jsonPath("$.transcriptIssueSummary.segmentActions[0].replayLaunchPreset.presetTitle").value("Replay from this transcript issue"))
            .andExpect(jsonPath("$.actionRecommendations.primaryAction").value("review_transcript"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionLabel").value("Review transcript"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTarget").value("transcript:issues"))
            .andExpect(jsonPath("$.actionRecommendations.primaryActionTargetPayload.focus").value("top_priority_issue"))
            .andExpect(jsonPath("$.actionRecommendations.availableActionTargets.review_transcript").value("transcript:issues"))
            .andExpect(jsonPath("$.actionRecommendations.blockingReasons[0]").value("pending_transcript_edits"))
            .andExpect(jsonPath("$.actionRecommendations.canConfirm").value(false))
            .andExpect(jsonPath("$.replayLaunchPreset.sourceInterviewRecordId").value(recordId))
            .andExpect(jsonPath("$.provenanceComparisonSummary.currentQuestionSource").value("deterministic"))
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

    @Test
    fun `bulk review applies multiple segment edits and can confirm in one call`() {
        val audio = MockMultipartFile("file", "bulk-review.wav", "audio/wav", "fake-audio".toByteArray())
        val created = mockMvc.perform(
            multipart("/api/interview-records")
                .file(audio)
                .param(
                    "transcriptText",
                    """
                    interviewer: Tell me about the rollout?
                    candidate: We rolled it out in phases.
                    interviewer: What metric moved?
                    candidate: latency improved.
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
        val firstQuestionSegmentId = transcript["segments"][0]["id"].asLong()
        val secondAnswerSegmentId = transcript["segments"][3]["id"].asLong()

        mockMvc.perform(
            patch("/api/interview-records/$recordId/review")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "edits" to listOf(
                                mapOf(
                                    "segmentId" to firstQuestionSegmentId,
                                    "confirmedText" to "Tell me about the rollout strategy and rollback guardrails?",
                                ),
                                mapOf(
                                    "segmentId" to secondAnswerSegmentId,
                                    "confirmedText" to "p95 latency improved by 25 percent after the phased rollout.",
                                ),
                            ),
                            "confirmAfterApply" to true,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("confirmed"))
            .andExpect(jsonPath("$.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.editedSegmentCount").value(0))
            .andExpect(jsonPath("$.changedQuestionCount").value(2))
            .andExpect(jsonPath("$.questionSourceCounts.confirmed").value(2))
            .andExpect(jsonPath("$.questionSummaries[1].answerSummary").value(org.hamcrest.Matchers.containsString("25 percent")))
            .andExpect(jsonPath("$.followUpThreads[0].structuringSources[0]").value("confirmed"))

        mockMvc.perform(get("/api/interview-records/$recordId/questions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].text").value("Tell me about the rollout strategy and rollback guardrails?"))
            .andExpect(jsonPath("$.items[1].answer.summary").value(org.hamcrest.Matchers.containsString("25 percent")))
            .andExpect(jsonPath("$.items[0].structuringSource").value("confirmed"))

        mockMvc.perform(get("/api/interview-records/$recordId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.structuringStage").value("confirmed"))
            .andExpect(jsonPath("$.confirmedAt").exists())
    }
}
