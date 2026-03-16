package com.example.interviewplatform.interview.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class InterviewRecordListItemDto(
    val id: Long,
    val companyName: String?,
    val roleName: String?,
    val interviewDate: LocalDate?,
    val interviewType: String,
    val transcriptStatus: String,
    val analysisStatus: String,
    val linkedResumeVersionId: Long?,
    val interviewerProfileId: Long?,
    val questionCount: Int,
    val createdAt: Instant,
)

data class InterviewRecordDetailDto(
    val id: Long,
    val companyName: String?,
    val roleName: String?,
    val interviewDate: LocalDate?,
    val interviewType: String,
    val sourceAudioFileUrl: String?,
    val sourceAudioFileName: String?,
    val sourceAudioDurationMs: Long?,
    val transcriptStatus: String,
    val analysisStatus: String,
    val linkedResumeVersionId: Long?,
    val linkedJobPostingId: Long?,
    val interviewerProfileId: Long?,
    val deterministicSummary: String?,
    val aiEnrichedSummary: String?,
    val overallSummary: String?,
    val structuringStage: String,
    val confirmedAt: Instant?,
    val questionCount: Int,
    val answerCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class InterviewRecordReviewDto(
    val interviewRecordId: Long,
    val structuringStage: String,
    val requiresConfirmation: Boolean,
    val deterministicSummary: String?,
    val aiEnrichedSummary: String?,
    val overallSummary: String?,
    val confirmedAt: Instant?,
    val totalSegmentCount: Int,
    val editedSegmentCount: Int,
    val totalQuestionCount: Int,
    val changedQuestionCount: Int,
    val weakAnswerCount: Int,
    val followUpQuestionCount: Int,
    val questionSourceCounts: Map<String, Int>,
    val answerSourceCounts: Map<String, Int>,
    val interviewerProfileSource: String?,
    val questionFilterSummary: InterviewRecordReviewQuestionFilterSummaryDto,
    val questionDistributionSummary: InterviewRecordReviewQuestionDistributionSummaryDto,
    val questionOriginSummary: InterviewRecordReviewQuestionOriginSummaryDto,
    val replayReadiness: InterviewRecordReplayReadinessDto,
    val transcriptIssueSummary: InterviewRecordTranscriptIssueSummaryDto,
    val answerQualitySummary: InterviewRecordAnswerQualitySummaryDto,
    val timelineNavigation: InterviewRecordTimelineNavigationDto,
    val actionRecommendations: InterviewRecordReviewActionRecommendationsDto,
    val replayLaunchPreset: InterviewRecordReplayLaunchPresetDto,
    val questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    val followUpThreads: List<InterviewRecordReviewFollowUpThreadDto>,
)

data class InterviewRecordReviewQuestionFilterSummaryDto(
    val allQuestions: Int,
    val primaryQuestions: Int,
    val followUpQuestions: Int,
    val weakAnswerQuestions: Int,
    val weakFollowUpQuestions: Int,
    val confirmedQuestions: Int,
)

data class InterviewRecordReviewQuestionDistributionSummaryDto(
    val questionTypeCounts: Map<String, Int>,
    val topicTagCounts: Map<String, Int>,
)

data class InterviewRecordReviewQuestionOriginSummaryDto(
    val resumeLinkedQuestions: Int,
    val jobPostingLinkedQuestions: Int,
    val hybridLinkedQuestions: Int,
    val generalQuestions: Int,
)

data class InterviewRecordReplayReadinessDto(
    val ready: Boolean,
    val replayableQuestionCount: Int,
    val linkedQuestionCount: Int,
    val unlinkedQuestionCount: Int,
    val followUpThreadCount: Int,
    val hasInterviewerProfile: Boolean,
    val recommendedReplayMode: String,
    val blockers: List<String>,
)

data class InterviewRecordTranscriptIssueSummaryDto(
    val lowConfidenceSegmentCount: Int,
    val lowConfidenceSegmentSequences: List<Int>,
    val speakerOverrideSegmentCount: Int,
    val speakerOverrideSegmentSequences: List<Int>,
    val confirmedTextOverrideCount: Int,
    val editedSegmentSequences: List<Int>,
)

data class InterviewRecordAnswerQualitySummaryDto(
    val answeredQuestionCount: Int,
    val weakAnswerCount: Int,
    val strengthTaggedAnswerCount: Int,
    val quantifiedAnswerCount: Int,
    val structuredAnswerCount: Int,
    val tradeoffAwareAnswerCount: Int,
    val uncertainAnswerCount: Int,
    val detailedAnswerCount: Int,
)

data class InterviewRecordTimelineNavigationDto(
    val items: List<InterviewRecordTimelineNavigationItemDto>,
)

data class InterviewRecordTimelineNavigationItemDto(
    val questionId: Long,
    val orderIndex: Int,
    val parentQuestionId: Long?,
    val threadRootQuestionId: Long,
    val questionSegmentStartSequence: Int?,
    val questionSegmentEndSequence: Int?,
    val answerSegmentStartSequence: Int?,
    val answerSegmentEndSequence: Int?,
)

data class InterviewRecordReviewActionRecommendationsDto(
    val primaryAction: String,
    val availableActions: List<String>,
    val blockingReasons: List<String>,
    val canConfirm: Boolean,
    val canReplay: Boolean,
)

data class InterviewRecordReplayLaunchPresetDto(
    val sessionType: String,
    val sourceInterviewRecordId: Long,
    val replayMode: String,
    val recommendedQuestionCount: Int,
    val seedQuestionIds: List<Long>,
    val availableReplayModes: List<String>,
)

data class InterviewRecordReviewQuestionSummaryDto(
    val questionId: Long,
    val linkedQuestionId: Long?,
    val deepLink: InterviewRecordReviewQuestionDeepLinkDto,
    val orderIndex: Int,
    val text: String,
    val questionType: String,
    val topicTags: List<String>,
    val originType: String,
    val derivedFromResumeSection: String?,
    val derivedFromJobPostingSection: String?,
    val isFollowUp: Boolean,
    val parentQuestionId: Long?,
    val hasWeakAnswer: Boolean,
    val answerSummary: String?,
    val weaknessTags: List<String>,
    val strengthTags: List<String>,
    val questionStructuringSource: String,
    val answerStructuringSource: String?,
)

data class InterviewRecordReviewQuestionDeepLinkDto(
    val questionDetailQuestionId: Long?,
    val archiveSourceType: String,
    val sourceInterviewRecordId: Long,
    val sourceInterviewQuestionId: Long,
    val canStartReplayMock: Boolean,
    val replaySessionType: String,
)

data class InterviewRecordReviewFollowUpThreadDto(
    val rootQuestionId: Long,
    val rootLinkedQuestionId: Long?,
    val rootOrderIndex: Int,
    val rootText: String,
    val questionIds: List<Long>,
    val linkedQuestionIds: List<Long>,
    val followUpQuestionIds: List<Long>,
    val followUpCount: Int,
    val weakQuestionCount: Int,
    val structuringSources: List<String>,
)

data class InterviewTranscriptSegmentDto(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val speakerType: String,
    val rawText: String?,
    val cleanedText: String?,
    val confirmedText: String?,
    val confidenceScore: BigDecimal?,
    val sequence: Int,
)

data class InterviewRecordTranscriptDto(
    val interviewRecordId: Long,
    val rawTranscript: String?,
    val cleanedTranscript: String?,
    val confirmedTranscript: String?,
    val transcriptStatus: String,
    val segments: List<InterviewTranscriptSegmentDto>,
    val updatedAt: Instant,
)

data class UpdateInterviewTranscriptSegmentRequest(
    val speakerType: String?,
    val cleanedText: String?,
    val confirmedText: String?,
)

data class BulkUpdateInterviewTranscriptSegmentsRequest(
    @field:NotEmpty
    @field:Valid
    val edits: List<UpdateInterviewTranscriptSegmentItemRequest>,
    val confirmAfterApply: Boolean = false,
)

data class UpdateInterviewTranscriptSegmentItemRequest(
    @field:NotNull
    val segmentId: Long?,
    val speakerType: String?,
    val cleanedText: String?,
    val confirmedText: String?,
)

data class InterviewRecordQuestionAnswerDto(
    val id: Long,
    val text: String,
    val normalizedText: String?,
    val summary: String?,
    val confidenceMarkers: List<String>,
    val weaknessTags: List<String>,
    val strengthTags: List<String>,
    val structuringSource: String,
    val orderIndex: Int,
)

data class InterviewRecordQuestionDto(
    val id: Long,
    val linkedQuestionId: Long?,
    val text: String,
    val normalizedText: String?,
    val questionType: String,
    val topicTags: List<String>,
    val intentTags: List<String>,
    val derivedFromResumeSection: String?,
    val derivedFromResumeRecordType: String?,
    val derivedFromResumeRecordId: Long?,
    val derivedFromJobPostingSection: String?,
    val parentQuestionId: Long?,
    val structuringSource: String,
    val orderIndex: Int,
    val answer: InterviewRecordQuestionAnswerDto?,
)

data class InterviewRecordQuestionsResponseDto(
    val interviewRecordId: Long,
    val items: List<InterviewRecordQuestionDto>,
)

data class InterviewRecordFollowUpEdgeDto(
    val fromQuestionId: Long,
    val toQuestionId: Long,
    val relationType: String,
    val triggerType: String,
)

data class InterviewRecordAnalysisDto(
    val interviewRecordId: Long,
    val totalQuestions: Int,
    val totalAnswers: Int,
    val followUpCount: Int,
    val questionTypeDistribution: Map<String, Int>,
    val weakAnswerQuestionIds: List<Long>,
    val topicTags: List<String>,
    val structuringStage: String,
    val overallSummary: String?,
)

data class InterviewerProfileDto(
    val id: Long,
    val sourceInterviewRecordId: Long,
    val styleTags: List<String>,
    val toneProfile: String,
    val pressureLevel: String,
    val depthPreference: String,
    val followUpPatterns: List<String>,
    val favoriteTopics: List<String>,
    val openingPattern: String?,
    val closingPattern: String?,
    val structuringSource: String,
)
