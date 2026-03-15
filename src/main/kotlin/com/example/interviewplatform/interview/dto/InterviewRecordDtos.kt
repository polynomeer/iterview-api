package com.example.interviewplatform.interview.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

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
    val questionCount: Int,
    val answerCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
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
