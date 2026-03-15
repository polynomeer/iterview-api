package com.example.interviewplatform.interview.mapper

import com.example.interviewplatform.interview.dto.InterviewRecordAnalysisDto
import com.example.interviewplatform.interview.dto.InterviewRecordDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordFollowUpEdgeDto
import com.example.interviewplatform.interview.dto.InterviewRecordListItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordQuestionAnswerDto
import com.example.interviewplatform.interview.dto.InterviewRecordQuestionDto
import com.example.interviewplatform.interview.dto.InterviewTranscriptSegmentDto
import com.example.interviewplatform.interview.dto.InterviewerProfileDto
import com.example.interviewplatform.interview.entity.InterviewRecordAnswerEntity
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import com.example.interviewplatform.interview.entity.InterviewRecordFollowUpEdgeEntity
import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import com.example.interviewplatform.interview.entity.InterviewTranscriptSegmentEntity
import com.example.interviewplatform.interview.entity.InterviewerProfileEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

object InterviewRecordMapper {
    fun toListItemDto(
        entity: InterviewRecordEntity,
        questionCount: Int,
    ): InterviewRecordListItemDto = InterviewRecordListItemDto(
        id = entity.id,
        companyName = entity.companyName,
        roleName = entity.roleName,
        interviewDate = entity.interviewDate,
        interviewType = entity.interviewType,
        transcriptStatus = entity.transcriptStatus,
        analysisStatus = entity.analysisStatus,
        linkedResumeVersionId = entity.linkedResumeVersionId,
        interviewerProfileId = entity.interviewerProfileId,
        questionCount = questionCount,
        createdAt = entity.createdAt,
    )

    fun toDetailDto(
        entity: InterviewRecordEntity,
        questionCount: Int,
        answerCount: Int,
    ): InterviewRecordDetailDto = InterviewRecordDetailDto(
        id = entity.id,
        companyName = entity.companyName,
        roleName = entity.roleName,
        interviewDate = entity.interviewDate,
        interviewType = entity.interviewType,
        sourceAudioFileUrl = entity.sourceAudioFileUrl,
        sourceAudioFileName = entity.sourceAudioFileName,
        sourceAudioDurationMs = entity.sourceAudioDurationMs,
        transcriptStatus = entity.transcriptStatus,
        analysisStatus = entity.analysisStatus,
        linkedResumeVersionId = entity.linkedResumeVersionId,
        linkedJobPostingId = entity.linkedJobPostingId,
        interviewerProfileId = entity.interviewerProfileId,
        overallSummary = entity.overallSummary,
        questionCount = questionCount,
        answerCount = answerCount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun toTranscriptSegmentDto(entity: InterviewTranscriptSegmentEntity): InterviewTranscriptSegmentDto =
        InterviewTranscriptSegmentDto(
            id = entity.id,
            startMs = entity.startMs,
            endMs = entity.endMs,
            speakerType = entity.speakerType,
            rawText = entity.rawText,
            cleanedText = entity.cleanedText,
            confirmedText = entity.confirmedText,
            confidenceScore = entity.confidenceScore,
            sequence = entity.sequence,
        )

    fun toQuestionDto(
        entity: InterviewRecordQuestionEntity,
        answer: InterviewRecordAnswerEntity?,
        objectMapper: ObjectMapper,
    ): InterviewRecordQuestionDto = InterviewRecordQuestionDto(
        id = entity.id,
        linkedQuestionId = entity.linkedQuestionId,
        text = entity.text,
        normalizedText = entity.normalizedText,
        questionType = entity.questionType,
        topicTags = decodeStringList(entity.topicTagsJson, objectMapper),
        intentTags = decodeStringList(entity.intentTagsJson, objectMapper),
        derivedFromResumeSection = entity.derivedFromResumeSection,
        derivedFromResumeRecordType = entity.derivedFromResumeRecordType,
        derivedFromResumeRecordId = entity.derivedFromResumeRecordId,
        derivedFromJobPostingSection = entity.derivedFromJobPostingSection,
        parentQuestionId = entity.parentQuestionId,
        orderIndex = entity.orderIndex,
        answer = answer?.let { toAnswerDto(it, objectMapper) },
    )

    fun toAnswerDto(entity: InterviewRecordAnswerEntity, objectMapper: ObjectMapper): InterviewRecordQuestionAnswerDto =
        InterviewRecordQuestionAnswerDto(
            id = entity.id,
            text = entity.text,
            normalizedText = entity.normalizedText,
            summary = entity.summary,
            confidenceMarkers = decodeStringList(entity.confidenceMarkersJson, objectMapper),
            weaknessTags = decodeStringList(entity.weaknessTagsJson, objectMapper),
            strengthTags = decodeStringList(entity.strengthTagsJson, objectMapper),
            orderIndex = entity.orderIndex,
        )

    fun toFollowUpEdgeDto(entity: InterviewRecordFollowUpEdgeEntity): InterviewRecordFollowUpEdgeDto =
        InterviewRecordFollowUpEdgeDto(
            fromQuestionId = entity.fromQuestionId,
            toQuestionId = entity.toQuestionId,
            relationType = entity.relationType,
            triggerType = entity.triggerType,
        )

    fun toAnalysisDto(
        interviewRecordId: Long,
        questions: List<InterviewRecordQuestionEntity>,
        answers: List<InterviewRecordAnswerEntity>,
        followUpCount: Int,
        topicTags: List<String>,
        overallSummary: String?,
        objectMapper: ObjectMapper,
    ): InterviewRecordAnalysisDto = InterviewRecordAnalysisDto(
        interviewRecordId = interviewRecordId,
        totalQuestions = questions.size,
        totalAnswers = answers.size,
        followUpCount = followUpCount,
        questionTypeDistribution = questions.groupingBy { it.questionType }.eachCount().toSortedMap(),
        weakAnswerQuestionIds = answers.filter { decodeStringList(it.weaknessTagsJson, objectMapper).isNotEmpty() }.map { it.interviewRecordQuestionId },
        topicTags = topicTags,
        overallSummary = overallSummary,
    )

    fun toInterviewerProfileDto(entity: InterviewerProfileEntity, objectMapper: ObjectMapper): InterviewerProfileDto =
        InterviewerProfileDto(
            id = entity.id,
            sourceInterviewRecordId = entity.sourceInterviewRecordId,
            styleTags = decodeStringList(entity.styleTagsJson, objectMapper),
            toneProfile = entity.toneProfile,
            pressureLevel = entity.pressureLevel,
            depthPreference = entity.depthPreference,
            followUpPatterns = decodeStringList(entity.followUpPatternJson, objectMapper),
            favoriteTopics = decodeStringList(entity.favoriteTopicsJson, objectMapper),
            openingPattern = entity.openingPattern,
            closingPattern = entity.closingPattern,
        )

    private fun decodeStringList(raw: String, objectMapper: ObjectMapper): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())
}
