package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.interview.dto.InterviewRecordAnalysisDto
import com.example.interviewplatform.interview.dto.InterviewRecordDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordListItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordQuestionsResponseDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewFollowUpThreadDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionDeepLinkDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionDistributionSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionFilterSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionOriginSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReplayReadinessDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptIssueSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordAnswerQualitySummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptDto
import com.example.interviewplatform.interview.dto.BulkUpdateInterviewTranscriptSegmentsRequest
import com.example.interviewplatform.interview.dto.UpdateInterviewTranscriptSegmentRequest
import com.example.interviewplatform.interview.dto.InterviewerProfileDto
import com.example.interviewplatform.interview.entity.InterviewRecordAnswerEntity
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import com.example.interviewplatform.interview.entity.InterviewRecordFollowUpEdgeEntity
import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import com.example.interviewplatform.interview.entity.InterviewTranscriptSegmentEntity
import com.example.interviewplatform.interview.entity.InterviewerProfileEntity
import com.example.interviewplatform.interview.mapper.InterviewRecordMapper
import com.example.interviewplatform.interview.repository.InterviewRecordAnswerRepository
import com.example.interviewplatform.interview.repository.InterviewRecordFollowUpEdgeRepository
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordRepository
import com.example.interviewplatform.interview.repository.InterviewTranscriptSegmentRepository
import com.example.interviewplatform.interview.repository.InterviewerProfileRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Service
class InterviewRecordService(
    private val interviewRecordRepository: InterviewRecordRepository,
    private val interviewTranscriptSegmentRepository: InterviewTranscriptSegmentRepository,
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val interviewRecordAnswerRepository: InterviewRecordAnswerRepository,
    private val interviewRecordFollowUpEdgeRepository: InterviewRecordFollowUpEdgeRepository,
    private val interviewerProfileRepository: InterviewerProfileRepository,
    private val interviewRecordQuestionAssetService: InterviewRecordQuestionAssetService,
    private val interviewAudioStorageService: InterviewAudioStorageService,
    private val practicalInterviewStructuringEnrichmentService: PracticalInterviewStructuringEnrichmentService,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val clockService: ClockService,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun listRecords(userId: Long): List<InterviewRecordListItemDto> {
        val records = interviewRecordRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val questionCountsByRecordId = records.associate { record ->
            record.id to interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(record.id).size
        }
        return records.map { InterviewRecordMapper.toListItemDto(it, questionCountsByRecordId[it.id] ?: 0) }
    }

    @Transactional
    fun createRecord(
        userId: Long,
        file: MultipartFile,
        companyName: String?,
        roleName: String?,
        interviewDate: LocalDate?,
        interviewType: String?,
        linkedResumeVersionId: Long?,
        linkedJobPostingId: Long?,
        transcriptText: String?,
    ): InterviewRecordDetailDto {
        validateAudio(file)
        linkedResumeVersionId?.let { requireOwnedResumeVersion(userId, it) }
        val now = clockService.now()
        val storedFile = interviewAudioStorageService.store(userId, file, now)
        val normalizedTranscript = transcriptText?.trim()?.takeIf { it.isNotEmpty() }

        var record = interviewRecordRepository.save(
            InterviewRecordEntity(
                userId = userId,
                companyName = companyName?.trim()?.takeIf { it.isNotEmpty() },
                roleName = roleName?.trim()?.takeIf { it.isNotEmpty() },
                interviewDate = interviewDate,
                interviewType = interviewType?.trim()?.takeIf { it.isNotEmpty() } ?: INTERVIEW_TYPE_GENERAL,
                sourceAudioFileUrl = buildInterviewAudioFileUrl(storedFile.storageKey),
                sourceAudioFileName = storedFile.fileName,
                sourceAudioDurationMs = null,
                sourceAudioContentType = file.contentType,
                rawTranscript = normalizedTranscript,
                cleanedTranscript = normalizedTranscript,
                confirmedTranscript = normalizedTranscript,
                transcriptStatus = if (normalizedTranscript == null) TRANSCRIPT_STATUS_PENDING else TRANSCRIPT_STATUS_CONFIRMED,
                analysisStatus = if (normalizedTranscript == null) ANALYSIS_STATUS_PENDING else ANALYSIS_STATUS_COMPLETED,
                linkedResumeVersionId = linkedResumeVersionId,
                linkedJobPostingId = linkedJobPostingId,
                interviewerProfileId = null,
                deterministicSummary = null,
                aiEnrichedSummary = null,
                overallSummary = null,
                structuringStage = STRUCTURING_STAGE_DETERMINISTIC,
                confirmedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        if (normalizedTranscript != null) {
            rebuildStructuredData(record, normalizedTranscript, now)
            record = requireOwnedRecord(userId, record.id)
        }
        return toDetailDto(record)
    }

    @Transactional(readOnly = true)
    fun getRecord(userId: Long, recordId: Long): InterviewRecordDetailDto =
        toDetailDto(requireOwnedRecord(userId, recordId))

    @Transactional(readOnly = true)
    fun getTranscript(userId: Long, recordId: Long): InterviewRecordTranscriptDto {
        val record = requireOwnedRecord(userId, recordId)
        val segments = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(recordId)
        return InterviewRecordTranscriptDto(
            interviewRecordId = record.id,
            rawTranscript = record.rawTranscript,
            cleanedTranscript = record.cleanedTranscript,
            confirmedTranscript = record.confirmedTranscript,
            transcriptStatus = record.transcriptStatus,
            segments = segments.map(InterviewRecordMapper::toTranscriptSegmentDto),
            updatedAt = record.updatedAt,
        )
    }

    @Transactional
    fun updateTranscriptSegment(
        userId: Long,
        recordId: Long,
        segmentId: Long,
        request: UpdateInterviewTranscriptSegmentRequest,
    ): InterviewRecordTranscriptDto {
        val record = requireOwnedRecord(userId, recordId)
        val segment = interviewTranscriptSegmentRepository.findByIdAndInterviewRecordId(segmentId, record.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript segment not found: $segmentId")
        val now = clockService.now()
        interviewTranscriptSegmentRepository.save(
            InterviewTranscriptSegmentEntity(
                id = segment.id,
                interviewRecordId = segment.interviewRecordId,
                startMs = segment.startMs,
                endMs = segment.endMs,
                speakerType = request.speakerType?.trim()?.takeIf { it.isNotEmpty() } ?: segment.speakerType,
                rawText = segment.rawText,
                cleanedText = request.cleanedText?.trim() ?: segment.cleanedText,
                confirmedText = request.confirmedText?.trim() ?: segment.confirmedText,
                confidenceScore = segment.confidenceScore,
                sequence = segment.sequence,
                createdAt = segment.createdAt,
                updatedAt = now,
            ),
        )
        val refreshedSegments = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(record.id)
        val rebuiltTranscript = refreshedSegments.joinToString("\n") { it.confirmedText ?: it.cleanedText ?: it.rawText.orEmpty() }.trim()
        val updatedRecord = interviewRecordRepository.save(
            InterviewRecordEntity(
                id = record.id,
                userId = record.userId,
                companyName = record.companyName,
                roleName = record.roleName,
                interviewDate = record.interviewDate,
                interviewType = record.interviewType,
                sourceAudioFileUrl = record.sourceAudioFileUrl,
                sourceAudioFileName = record.sourceAudioFileName,
                sourceAudioDurationMs = record.sourceAudioDurationMs,
                sourceAudioContentType = record.sourceAudioContentType,
                rawTranscript = record.rawTranscript,
                cleanedTranscript = refreshedSegments.joinToString("\n") { it.cleanedText ?: it.rawText.orEmpty() }.trim(),
                confirmedTranscript = rebuiltTranscript,
                transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                analysisStatus = ANALYSIS_STATUS_COMPLETED,
                linkedResumeVersionId = record.linkedResumeVersionId,
                linkedJobPostingId = record.linkedJobPostingId,
                interviewerProfileId = record.interviewerProfileId,
                deterministicSummary = record.deterministicSummary,
                aiEnrichedSummary = record.aiEnrichedSummary,
                overallSummary = record.overallSummary,
                structuringStage = record.structuringStage,
                confirmedAt = record.confirmedAt,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
        rebuildStructuredData(updatedRecord, rebuiltTranscript, now, isUserConfirmed = true)
        return getTranscript(userId, record.id)
    }

    @Transactional
    fun listQuestions(userId: Long, recordId: Long): InterviewRecordQuestionsResponseDto {
        val record = requireOwnedRecord(userId, recordId)
        val answersByQuestionId = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(
            interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId).map { it.id },
        )
            .associateBy { it.interviewRecordQuestionId }
        val questions = interviewRecordQuestionAssetService.ensureLinkedQuestionAssets(
            record = record,
            questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId),
            answersByQuestionId = answersByQuestionId,
            now = clockService.now(),
        )
        return InterviewRecordQuestionsResponseDto(
            interviewRecordId = recordId,
            items = questions.map { InterviewRecordMapper.toQuestionDto(it, answersByQuestionId[it.id], objectMapper) },
        )
    }

    @Transactional(readOnly = true)
    fun getReview(userId: Long, recordId: Long): InterviewRecordReviewDto {
        val record = requireOwnedRecord(userId, recordId)
        val segments = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(recordId)
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId)
        val answers = if (questions.isEmpty()) {
            emptyList()
        } else {
            interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(questions.map { it.id })
        }
        val answerByQuestionId = answers.associateBy { it.interviewRecordQuestionId }
        val interviewerProfile = interviewerProfileRepository.findBySourceInterviewRecordId(recordId)
        val questionSummaries = questions.map { question ->
            val answer = answerByQuestionId[question.id]
            val weaknessTags = answer?.let { decodeStringList(it.weaknessTagsJson) }.orEmpty()
            val strengthTags = answer?.let { decodeStringList(it.strengthTagsJson) }.orEmpty()
            val topicTags = decodeStringList(question.topicTagsJson)
            val originType = resolveReviewQuestionOriginType(question)
            InterviewRecordReviewQuestionSummaryDto(
                questionId = question.id,
                linkedQuestionId = question.linkedQuestionId,
                deepLink = InterviewRecordReviewQuestionDeepLinkDto(
                    questionDetailQuestionId = question.linkedQuestionId,
                    archiveSourceType = "real_interview",
                    sourceInterviewRecordId = recordId,
                    sourceInterviewQuestionId = question.id,
                    canStartReplayMock = true,
                    replaySessionType = REVIEW_REPLAY_SESSION_TYPE,
                ),
                orderIndex = question.orderIndex,
                text = question.text,
                questionType = question.questionType,
                topicTags = topicTags,
                originType = originType,
                derivedFromResumeSection = question.derivedFromResumeSection,
                derivedFromJobPostingSection = question.derivedFromJobPostingSection,
                isFollowUp = question.parentQuestionId != null,
                parentQuestionId = question.parentQuestionId,
                hasWeakAnswer = weaknessTags.isNotEmpty(),
                answerSummary = answer?.summary,
                weaknessTags = weaknessTags,
                strengthTags = strengthTags,
                questionStructuringSource = question.structuringSource,
                answerStructuringSource = answer?.structuringSource,
            )
        }
        return InterviewRecordReviewDto(
            interviewRecordId = record.id,
            structuringStage = record.structuringStage,
            requiresConfirmation = record.structuringStage != STRUCTURING_STAGE_CONFIRMED,
            deterministicSummary = record.deterministicSummary,
            aiEnrichedSummary = record.aiEnrichedSummary,
            overallSummary = record.overallSummary,
            confirmedAt = record.confirmedAt,
            totalSegmentCount = segments.size,
            editedSegmentCount = segments.count(::isEditedSegment),
            totalQuestionCount = questions.size,
            changedQuestionCount = questions.count { it.structuringSource != STRUCTURING_STAGE_DETERMINISTIC },
            weakAnswerCount = answers.count { decodeStringList(it.weaknessTagsJson).isNotEmpty() },
            followUpQuestionCount = questions.count { it.parentQuestionId != null },
            questionSourceCounts = questions.groupingBy { it.structuringSource }.eachCount().toSortedMap(),
            answerSourceCounts = answers.groupingBy { it.structuringSource }.eachCount().toSortedMap(),
            interviewerProfileSource = interviewerProfile?.structuringSource,
            questionFilterSummary = buildReviewQuestionFilterSummary(questionSummaries),
            questionDistributionSummary = buildReviewQuestionDistributionSummary(questionSummaries),
            questionOriginSummary = buildReviewQuestionOriginSummary(questionSummaries),
            replayReadiness = buildReplayReadiness(questionSummaries, interviewerProfile != null),
            transcriptIssueSummary = buildTranscriptIssueSummary(segments),
            answerQualitySummary = buildAnswerQualitySummary(answers),
            questionSummaries = questionSummaries,
            followUpThreads = buildReviewFollowUpThreads(questionSummaries),
        )
    }

    private fun buildReviewQuestionFilterSummary(
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): InterviewRecordReviewQuestionFilterSummaryDto = InterviewRecordReviewQuestionFilterSummaryDto(
        allQuestions = questionSummaries.size,
        primaryQuestions = questionSummaries.count { !it.isFollowUp },
        followUpQuestions = questionSummaries.count { it.isFollowUp },
        weakAnswerQuestions = questionSummaries.count { it.hasWeakAnswer },
        weakFollowUpQuestions = questionSummaries.count { it.isFollowUp && it.hasWeakAnswer },
        confirmedQuestions = questionSummaries.count {
            it.questionStructuringSource == STRUCTURING_STAGE_CONFIRMED &&
                (it.answerStructuringSource == null || it.answerStructuringSource == STRUCTURING_STAGE_CONFIRMED)
        },
    )

    private fun buildReviewQuestionDistributionSummary(
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): InterviewRecordReviewQuestionDistributionSummaryDto = InterviewRecordReviewQuestionDistributionSummaryDto(
        questionTypeCounts = questionSummaries.groupingBy { it.questionType }.eachCount().toSortedMap(),
        topicTagCounts = questionSummaries
            .flatMap { it.topicTags }
            .groupingBy { it }
            .eachCount()
            .toSortedMap(),
    )

    private fun buildReviewQuestionOriginSummary(
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): InterviewRecordReviewQuestionOriginSummaryDto = InterviewRecordReviewQuestionOriginSummaryDto(
        resumeLinkedQuestions = questionSummaries.count { it.originType == REVIEW_ORIGIN_RESUME_LINKED },
        jobPostingLinkedQuestions = questionSummaries.count { it.originType == REVIEW_ORIGIN_JOB_POSTING_LINKED },
        hybridLinkedQuestions = questionSummaries.count { it.originType == REVIEW_ORIGIN_HYBRID_LINKED },
        generalQuestions = questionSummaries.count { it.originType == REVIEW_ORIGIN_GENERAL },
    )

    private fun buildReplayReadiness(
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
        hasInterviewerProfile: Boolean,
    ): InterviewRecordReplayReadinessDto {
        val linkedQuestionCount = questionSummaries.count { it.linkedQuestionId != null }
        val blockers = buildList {
            if (questionSummaries.isEmpty()) {
                add(REPLAY_BLOCKER_NO_QUESTIONS)
            }
            if (!hasInterviewerProfile) {
                add(REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE)
            }
        }
        return InterviewRecordReplayReadinessDto(
            ready = blockers.isEmpty(),
            replayableQuestionCount = questionSummaries.size,
            linkedQuestionCount = linkedQuestionCount,
            unlinkedQuestionCount = questionSummaries.size - linkedQuestionCount,
            followUpThreadCount = questionSummaries.count { !it.isFollowUp },
            hasInterviewerProfile = hasInterviewerProfile,
            recommendedReplayMode = REVIEW_REPLAY_MODE_ORIGINAL,
            blockers = blockers,
        )
    }

    private fun buildTranscriptIssueSummary(
        segments: List<InterviewTranscriptSegmentEntity>,
    ): InterviewRecordTranscriptIssueSummaryDto {
        val lowConfidenceSegments = segments.filter { (it.confidenceScore ?: BigDecimal.ONE) < LOW_CONFIDENCE_THRESHOLD }
        val speakerOverrideSegments = segments.filter(::hasSpeakerOverride)
        val editedSegments = segments.filter(::isEditedSegment)
        return InterviewRecordTranscriptIssueSummaryDto(
            lowConfidenceSegmentCount = lowConfidenceSegments.size,
            lowConfidenceSegmentSequences = lowConfidenceSegments.map { it.sequence },
            speakerOverrideSegmentCount = speakerOverrideSegments.size,
            speakerOverrideSegmentSequences = speakerOverrideSegments.map { it.sequence },
            confirmedTextOverrideCount = editedSegments.size,
            editedSegmentSequences = editedSegments.map { it.sequence },
        )
    }

    private fun buildAnswerQualitySummary(
        answers: List<InterviewRecordAnswerEntity>,
    ): InterviewRecordAnswerQualitySummaryDto {
        val decodedAnswers = answers.map { answer ->
            DecodedReviewAnswerQuality(
                weaknessTags = decodeStringList(answer.weaknessTagsJson),
                strengthTags = decodeStringList(answer.strengthTagsJson),
                confidenceMarkers = decodeStringList(answer.confidenceMarkersJson),
            )
        }
        return InterviewRecordAnswerQualitySummaryDto(
            answeredQuestionCount = answers.size,
            weakAnswerCount = decodedAnswers.count { it.weaknessTags.isNotEmpty() },
            strengthTaggedAnswerCount = decodedAnswers.count { it.strengthTags.isNotEmpty() },
            quantifiedAnswerCount = decodedAnswers.count {
                "quantified" in it.strengthTags || "quantified" in it.confidenceMarkers
            },
            structuredAnswerCount = decodedAnswers.count { "structured" in it.strengthTags },
            tradeoffAwareAnswerCount = decodedAnswers.count { "tradeoff_aware" in it.strengthTags },
            uncertainAnswerCount = decodedAnswers.count { "uncertain" in it.confidenceMarkers },
            detailedAnswerCount = decodedAnswers.count { "detailed" in it.strengthTags },
        )
    }

    private fun hasSpeakerOverride(segment: InterviewTranscriptSegmentEntity): Boolean {
        val rawText = segment.rawText?.trim().orEmpty()
        if (rawText.isBlank()) {
            return false
        }
        return classifySpeaker(rawText).first != segment.speakerType
    }

    private fun resolveReviewQuestionOriginType(question: InterviewRecordQuestionEntity): String {
        val hasResumeLink = question.derivedFromResumeSection != null || question.derivedFromResumeRecordId != null
        val hasJobPostingLink = question.derivedFromJobPostingSection != null
        return when {
            hasResumeLink && hasJobPostingLink -> REVIEW_ORIGIN_HYBRID_LINKED
            hasResumeLink -> REVIEW_ORIGIN_RESUME_LINKED
            hasJobPostingLink -> REVIEW_ORIGIN_JOB_POSTING_LINKED
            else -> REVIEW_ORIGIN_GENERAL
        }
    }

    private fun buildReviewFollowUpThreads(
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): List<InterviewRecordReviewFollowUpThreadDto> {
        if (questionSummaries.isEmpty()) {
            return emptyList()
        }
        val summaryByQuestionId = questionSummaries.associateBy { it.questionId }
        val summariesByRootId = linkedMapOf<Long, MutableList<InterviewRecordReviewQuestionSummaryDto>>()
        questionSummaries.forEach { summary ->
            val rootQuestionId = resolveRootQuestionId(summary, summaryByQuestionId)
            summariesByRootId.computeIfAbsent(rootQuestionId) { mutableListOf() }.add(summary)
        }
        return summariesByRootId.values.map { threadSummaries ->
            val sortedThreadSummaries = threadSummaries.sortedBy { it.orderIndex }
            val rootSummary = sortedThreadSummaries.first()
            InterviewRecordReviewFollowUpThreadDto(
                rootQuestionId = rootSummary.questionId,
                rootLinkedQuestionId = rootSummary.linkedQuestionId,
                rootOrderIndex = rootSummary.orderIndex,
                rootText = rootSummary.text,
                questionIds = sortedThreadSummaries.map { it.questionId },
                linkedQuestionIds = sortedThreadSummaries.mapNotNull { it.linkedQuestionId }.distinct(),
                followUpQuestionIds = sortedThreadSummaries.filter { it.isFollowUp }.map { it.questionId },
                followUpCount = sortedThreadSummaries.count { it.isFollowUp },
                weakQuestionCount = sortedThreadSummaries.count { it.hasWeakAnswer },
                structuringSources = sortedThreadSummaries
                    .flatMap { listOfNotNull(it.questionStructuringSource, it.answerStructuringSource) }
                    .distinct()
                    .sorted(),
            )
        }
    }

    private fun resolveRootQuestionId(
        summary: InterviewRecordReviewQuestionSummaryDto,
        summaryByQuestionId: Map<Long, InterviewRecordReviewQuestionSummaryDto>,
    ): Long {
        var current = summary
        val visited = linkedSetOf(current.questionId)
        while (current.parentQuestionId != null) {
            val parent = summaryByQuestionId[current.parentQuestionId] ?: break
            if (!visited.add(parent.questionId)) {
                break
            }
            current = parent
        }
        return current.questionId
    }

    @Transactional
    fun applyReview(
        userId: Long,
        recordId: Long,
        request: BulkUpdateInterviewTranscriptSegmentsRequest,
    ): InterviewRecordReviewDto {
        val record = requireOwnedRecord(userId, recordId)
        val now = clockService.now()
        val segmentsById = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(recordId)
            .associateBy { it.id }
        val updatedSegments = request.edits.map { edit ->
            val segmentId = edit.segmentId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "segmentId is required")
            val existing = segmentsById[segmentId]
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript segment not found: $segmentId")
            InterviewTranscriptSegmentEntity(
                id = existing.id,
                interviewRecordId = existing.interviewRecordId,
                startMs = existing.startMs,
                endMs = existing.endMs,
                speakerType = edit.speakerType?.trim()?.takeIf { it.isNotEmpty() } ?: existing.speakerType,
                rawText = existing.rawText,
                cleanedText = edit.cleanedText?.trim() ?: existing.cleanedText,
                confirmedText = edit.confirmedText?.trim() ?: existing.confirmedText,
                confidenceScore = existing.confidenceScore,
                sequence = existing.sequence,
                createdAt = existing.createdAt,
                updatedAt = now,
            )
        }
        if (updatedSegments.isNotEmpty()) {
            interviewTranscriptSegmentRepository.saveAll(updatedSegments)
        }
        val refreshedSegments = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(record.id)
        val rebuiltTranscript = refreshedSegments.joinToString("\n") { it.confirmedText ?: it.cleanedText ?: it.rawText.orEmpty() }.trim()
        val updatedRecord = interviewRecordRepository.save(
            InterviewRecordEntity(
                id = record.id,
                userId = record.userId,
                companyName = record.companyName,
                roleName = record.roleName,
                interviewDate = record.interviewDate,
                interviewType = record.interviewType,
                sourceAudioFileUrl = record.sourceAudioFileUrl,
                sourceAudioFileName = record.sourceAudioFileName,
                sourceAudioDurationMs = record.sourceAudioDurationMs,
                sourceAudioContentType = record.sourceAudioContentType,
                rawTranscript = record.rawTranscript,
                cleanedTranscript = refreshedSegments.joinToString("\n") { it.cleanedText ?: it.rawText.orEmpty() }.trim(),
                confirmedTranscript = rebuiltTranscript,
                transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                analysisStatus = ANALYSIS_STATUS_COMPLETED,
                linkedResumeVersionId = record.linkedResumeVersionId,
                linkedJobPostingId = record.linkedJobPostingId,
                interviewerProfileId = record.interviewerProfileId,
                deterministicSummary = record.deterministicSummary,
                aiEnrichedSummary = record.aiEnrichedSummary,
                overallSummary = record.overallSummary,
                structuringStage = record.structuringStage,
                confirmedAt = record.confirmedAt,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
        rebuildStructuredData(updatedRecord, rebuiltTranscript, now, isUserConfirmed = request.confirmAfterApply)
        if (request.confirmAfterApply) {
            confirmRecord(userId, recordId)
        }
        return getReview(userId, recordId)
    }

    @Transactional(readOnly = true)
    fun getAnalysis(userId: Long, recordId: Long): InterviewRecordAnalysisDto {
        val record = requireOwnedRecord(userId, recordId)
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId)
        val answers = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(questions.map { it.id })
        val edges = interviewRecordFollowUpEdgeRepository.findByInterviewRecordIdOrderByIdAsc(recordId)
        val topicTags = questions.flatMap {
            runCatching {
                objectMapper.readValue(it.topicTagsJson, object : TypeReference<List<String>>() {})
            }.getOrDefault(emptyList())
        }.distinct()
        return InterviewRecordMapper.toAnalysisDto(
            record = record,
            interviewRecordId = record.id,
            questions = questions,
            answers = answers,
            followUpCount = edges.size,
            topicTags = topicTags,
            overallSummary = record.overallSummary,
            objectMapper = objectMapper,
        )
    }

    @Transactional(readOnly = true)
    fun getInterviewerProfile(userId: Long, recordId: Long): InterviewerProfileDto {
        requireOwnedRecord(userId, recordId)
        val profile = interviewerProfileRepository.findBySourceInterviewRecordId(recordId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interviewer profile not found for record: $recordId")
        return InterviewRecordMapper.toInterviewerProfileDto(profile, objectMapper)
    }

    @Transactional
    fun confirmRecord(userId: Long, recordId: Long): InterviewRecordDetailDto {
        val record = requireOwnedRecord(userId, recordId)
        val now = clockService.now()
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId)
        val answers = if (questions.isEmpty()) {
            emptyList()
        } else {
            interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(questions.map { it.id })
        }
        if (questions.isNotEmpty()) {
            interviewRecordQuestionRepository.saveAll(
                questions.map {
                    InterviewRecordQuestionEntity(
                        id = it.id,
                        interviewRecordId = it.interviewRecordId,
                        segmentStartId = it.segmentStartId,
                        segmentEndId = it.segmentEndId,
                        text = it.text,
                        normalizedText = it.normalizedText,
                        questionType = it.questionType,
                        topicTagsJson = it.topicTagsJson,
                        intentTagsJson = it.intentTagsJson,
                        derivedFromResumeSection = it.derivedFromResumeSection,
                        derivedFromResumeRecordType = it.derivedFromResumeRecordType,
                        derivedFromResumeRecordId = it.derivedFromResumeRecordId,
                        derivedFromJobPostingSection = it.derivedFromJobPostingSection,
                        linkedQuestionId = it.linkedQuestionId,
                        parentQuestionId = it.parentQuestionId,
                        structuringSource = STRUCTURING_STAGE_CONFIRMED,
                        orderIndex = it.orderIndex,
                        createdAt = it.createdAt,
                        updatedAt = now,
                    )
                },
            )
        }
        if (answers.isNotEmpty()) {
            interviewRecordAnswerRepository.saveAll(
                answers.map {
                    InterviewRecordAnswerEntity(
                        id = it.id,
                        interviewRecordQuestionId = it.interviewRecordQuestionId,
                        segmentStartId = it.segmentStartId,
                        segmentEndId = it.segmentEndId,
                        text = it.text,
                        normalizedText = it.normalizedText,
                        summary = it.summary,
                        confidenceMarkersJson = it.confidenceMarkersJson,
                        weaknessTagsJson = it.weaknessTagsJson,
                        strengthTagsJson = it.strengthTagsJson,
                        analysisJson = it.analysisJson,
                        structuringSource = STRUCTURING_STAGE_CONFIRMED,
                        orderIndex = it.orderIndex,
                        createdAt = it.createdAt,
                        updatedAt = now,
                    )
                },
            )
        }
        interviewerProfileRepository.findBySourceInterviewRecordId(recordId)?.let {
            interviewerProfileRepository.save(
                InterviewerProfileEntity(
                    id = it.id,
                    userId = it.userId,
                    sourceInterviewRecordId = it.sourceInterviewRecordId,
                    styleTagsJson = it.styleTagsJson,
                    toneProfile = it.toneProfile,
                    pressureLevel = it.pressureLevel,
                    depthPreference = it.depthPreference,
                    followUpPatternJson = it.followUpPatternJson,
                    favoriteTopicsJson = it.favoriteTopicsJson,
                    openingPattern = it.openingPattern,
                    closingPattern = it.closingPattern,
                    structuringSource = STRUCTURING_STAGE_CONFIRMED,
                    createdAt = it.createdAt,
                    updatedAt = now,
                ),
            )
        }
        val updated = interviewRecordRepository.save(
            InterviewRecordEntity(
                id = record.id,
                userId = record.userId,
                companyName = record.companyName,
                roleName = record.roleName,
                interviewDate = record.interviewDate,
                interviewType = record.interviewType,
                sourceAudioFileUrl = record.sourceAudioFileUrl,
                sourceAudioFileName = record.sourceAudioFileName,
                sourceAudioDurationMs = record.sourceAudioDurationMs,
                sourceAudioContentType = record.sourceAudioContentType,
                rawTranscript = record.rawTranscript,
                cleanedTranscript = record.cleanedTranscript,
                confirmedTranscript = record.confirmedTranscript,
                transcriptStatus = record.transcriptStatus,
                analysisStatus = record.analysisStatus,
                linkedResumeVersionId = record.linkedResumeVersionId,
                linkedJobPostingId = record.linkedJobPostingId,
                interviewerProfileId = record.interviewerProfileId,
                deterministicSummary = record.deterministicSummary,
                aiEnrichedSummary = record.aiEnrichedSummary,
                overallSummary = record.aiEnrichedSummary ?: record.overallSummary,
                structuringStage = STRUCTURING_STAGE_CONFIRMED,
                confirmedAt = now,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
        return toDetailDto(updated)
    }

    private fun toDetailDto(record: InterviewRecordEntity): InterviewRecordDetailDto {
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(record.id)
        val answers = if (questions.isEmpty()) emptyList() else interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(questions.map { it.id })
        return InterviewRecordMapper.toDetailDto(record, questions.size, answers.size)
    }

    private fun requireOwnedRecord(userId: Long, recordId: Long): InterviewRecordEntity =
        interviewRecordRepository.findByIdAndUserId(recordId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Interview record not found: $recordId")

    private fun requireOwnedResumeVersion(userId: Long, resumeVersionId: Long) {
        val version = resumeVersionRepository.findById(resumeVersionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $resumeVersionId") }
        if (resumeRepository.findByIdAndUserId(version.resumeId, userId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $resumeVersionId")
        }
    }

    private fun validateAudio(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Interview audio file is required")
        }
        val fileName = file.originalFilename?.lowercase().orEmpty()
        val supported = fileName.endsWith(".mp3") || fileName.endsWith(".m4a") || fileName.endsWith(".wav") || fileName.endsWith(".mp4")
        if (!supported) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported interview uploads are mp3, m4a, wav, or mp4")
        }
    }

    private fun buildInterviewAudioFileUrl(storageKey: String): String = "/uploads/interview-audio/$storageKey"

    private fun rebuildStructuredData(
        record: InterviewRecordEntity,
        transcriptText: String,
        now: Instant,
        isUserConfirmed: Boolean = false,
    ) {
        val existingQuestions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(record.id)
        val existingQuestionIds = existingQuestions.map { it.id }
        val existingAnswers = if (existingQuestionIds.isEmpty()) {
            emptyList()
        } else {
            interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(existingQuestionIds)
        }
        val existingSegments = interviewTranscriptSegmentRepository.findByInterviewRecordIdOrderBySequenceAsc(record.id)

        interviewRecordFollowUpEdgeRepository.deleteByInterviewRecordId(record.id)
        interviewRecordFollowUpEdgeRepository.flush()
        if (existingAnswers.isNotEmpty()) {
            interviewRecordAnswerRepository.deleteAllInBatch(existingAnswers)
            interviewRecordAnswerRepository.flush()
        }
        if (existingQuestions.isNotEmpty()) {
            interviewRecordQuestionRepository.deleteAllInBatch(existingQuestions)
            interviewRecordQuestionRepository.flush()
        }
        if (existingSegments.isNotEmpty()) {
            interviewTranscriptSegmentRepository.deleteAllInBatch(existingSegments)
            interviewTranscriptSegmentRepository.flush()
        }

        val deterministicParsed = parseTranscript(transcriptText, now)
        val parsed = practicalInterviewStructuringEnrichmentService.enrich(
            record = record,
            transcriptText = transcriptText,
            parsedTranscript = deterministicParsed,
        )
        val deterministicSummary = buildOverallSummary(deterministicParsed.questions)
        val aiEnrichedSummary = when {
            parsed.structuringSource == STRUCTURING_STAGE_AI_ENRICHED && !parsed.overallSummaryOverride.isNullOrBlank() -> parsed.overallSummaryOverride
            parsed.structuringSource == STRUCTURING_STAGE_AI_ENRICHED -> buildOverallSummary(parsed.questions)
            else -> null
        }
        val persistedStructuringSource = if (isUserConfirmed) STRUCTURING_STAGE_CONFIRMED else parsed.structuringSource
        val segments = interviewTranscriptSegmentRepository.saveAll(parsed.segments.map { it.toEntity(record.id) })
        val persistedQuestions = mutableListOf<InterviewRecordQuestionEntity>()
        val answersToPersist = mutableListOf<InterviewRecordAnswerEntity>()
        val segmentIdBySequence = segments.associateBy({ it.sequence }, { it.id })
        val persistedQuestionIdByOrderIndex = mutableMapOf<Int, Long>()

        parsed.questions.forEach { parsedQuestion ->
            val questionEntity = interviewRecordQuestionRepository.save(
                InterviewRecordQuestionEntity(
                    interviewRecordId = record.id,
                    segmentStartId = segmentIdBySequence[parsedQuestion.segmentStartSequence],
                    segmentEndId = segmentIdBySequence[parsedQuestion.segmentEndSequence],
                    text = parsedQuestion.text,
                    normalizedText = parsedQuestion.normalizedText,
                    questionType = parsedQuestion.questionType,
                    topicTagsJson = objectMapper.writeValueAsString(parsedQuestion.topicTags),
                    intentTagsJson = objectMapper.writeValueAsString(parsedQuestion.intentTags),
                    derivedFromResumeSection = parsedQuestion.derivedFromResumeSection,
                    derivedFromResumeRecordType = parsedQuestion.derivedFromResumeRecordType,
                    derivedFromResumeRecordId = parsedQuestion.derivedFromResumeRecordId,
                    derivedFromJobPostingSection = null,
                    parentQuestionId = parsedQuestion.parentOrderIndex?.let(persistedQuestionIdByOrderIndex::get),
                    structuringSource = persistedStructuringSource,
                    orderIndex = parsedQuestion.orderIndex,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            persistedQuestions += questionEntity
            persistedQuestionIdByOrderIndex[parsedQuestion.orderIndex] = questionEntity.id
            parsedQuestion.answer?.let { parsedAnswer ->
                answersToPersist += InterviewRecordAnswerEntity(
                    interviewRecordQuestionId = questionEntity.id,
                    segmentStartId = segmentIdBySequence[parsedAnswer.segmentStartSequence],
                    segmentEndId = segmentIdBySequence[parsedAnswer.segmentEndSequence],
                    text = parsedAnswer.text,
                    normalizedText = parsedAnswer.normalizedText,
                    summary = parsedAnswer.summary,
                    confidenceMarkersJson = objectMapper.writeValueAsString(parsedAnswer.confidenceMarkers),
                    weaknessTagsJson = objectMapper.writeValueAsString(parsedAnswer.weaknessTags),
                    strengthTagsJson = objectMapper.writeValueAsString(parsedAnswer.strengthTags),
                    analysisJson = objectMapper.writeValueAsString(parsedAnswer.analysis),
                    structuringSource = persistedStructuringSource,
                    orderIndex = parsedQuestion.orderIndex,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        }
        if (answersToPersist.isNotEmpty()) {
            interviewRecordAnswerRepository.saveAll(answersToPersist)
        }
        val answersByQuestionId = answersToPersist.associateBy { it.interviewRecordQuestionId }
        interviewRecordQuestionAssetService.ensureLinkedQuestionAssets(
            record = record,
            questions = persistedQuestions,
            answersByQuestionId = answersByQuestionId,
            now = now,
        )

        if (persistedQuestions.size > 1) {
            val edges = buildFollowUpEdges(record.id, parsed.questions, persistedQuestions, now)
            if (edges.isNotEmpty()) {
                interviewRecordFollowUpEdgeRepository.saveAll(edges)
            }
        }

        val overallSummary = if (isUserConfirmed) {
            aiEnrichedSummary ?: buildOverallSummary(parsed.questions)
        } else {
            parsed.overallSummaryOverride ?: buildOverallSummary(parsed.questions)
        }
        val profile = buildInterviewerProfile(
            userId = record.userId,
            recordId = record.id,
            questions = parsed.questions,
            profileOverride = parsed.interviewerProfileOverride,
            structuringSource = persistedStructuringSource,
            now = now,
        )
        interviewerProfileRepository.findBySourceInterviewRecordId(record.id)?.let { existingProfile ->
            interviewRecordRepository.saveAndFlush(
                InterviewRecordEntity(
                    id = record.id,
                    userId = record.userId,
                    companyName = record.companyName,
                    roleName = record.roleName,
                    interviewDate = record.interviewDate,
                    interviewType = record.interviewType,
                    sourceAudioFileUrl = record.sourceAudioFileUrl,
                    sourceAudioFileName = record.sourceAudioFileName,
                    sourceAudioDurationMs = record.sourceAudioDurationMs,
                    sourceAudioContentType = record.sourceAudioContentType,
                    rawTranscript = record.rawTranscript,
                    cleanedTranscript = transcriptText,
                    confirmedTranscript = transcriptText,
                    transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                    analysisStatus = ANALYSIS_STATUS_COMPLETED,
                    linkedResumeVersionId = record.linkedResumeVersionId,
                    linkedJobPostingId = record.linkedJobPostingId,
                    interviewerProfileId = null,
                    deterministicSummary = deterministicSummary,
                    aiEnrichedSummary = aiEnrichedSummary,
                    overallSummary = overallSummary,
                    structuringStage = persistedStructuringSource,
                    createdAt = record.createdAt,
                    updatedAt = now,
                ),
            )
            interviewerProfileRepository.delete(existingProfile)
            interviewerProfileRepository.flush()
        }
        val savedProfile = interviewerProfileRepository.save(profile)
        interviewRecordRepository.save(
            InterviewRecordEntity(
                id = record.id,
                userId = record.userId,
                companyName = record.companyName,
                roleName = record.roleName,
                interviewDate = record.interviewDate,
                interviewType = record.interviewType,
                sourceAudioFileUrl = record.sourceAudioFileUrl,
                sourceAudioFileName = record.sourceAudioFileName,
                sourceAudioDurationMs = record.sourceAudioDurationMs,
                sourceAudioContentType = record.sourceAudioContentType,
                rawTranscript = record.rawTranscript,
                cleanedTranscript = transcriptText,
                confirmedTranscript = transcriptText,
                transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                analysisStatus = ANALYSIS_STATUS_COMPLETED,
                linkedResumeVersionId = record.linkedResumeVersionId,
                linkedJobPostingId = record.linkedJobPostingId,
                interviewerProfileId = savedProfile.id,
                deterministicSummary = deterministicSummary,
                aiEnrichedSummary = aiEnrichedSummary,
                overallSummary = overallSummary,
                structuringStage = persistedStructuringSource,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
    }

    private fun buildFollowUpEdges(
        interviewRecordId: Long,
        parsedQuestions: List<ParsedQuestion>,
        persistedQuestions: List<InterviewRecordQuestionEntity>,
        now: Instant,
    ): List<InterviewRecordFollowUpEdgeEntity> {
        val persistedQuestionIdByOrderIndex = persistedQuestions.associateBy({ it.orderIndex }, { it.id })
        val explicitEdges = parsedQuestions.mapNotNull { parsedQuestion ->
            val fromQuestionId = parsedQuestion.parentOrderIndex?.let(persistedQuestionIdByOrderIndex::get) ?: return@mapNotNull null
            val toQuestionId = persistedQuestionIdByOrderIndex[parsedQuestion.orderIndex] ?: return@mapNotNull null
            InterviewRecordFollowUpEdgeEntity(
                interviewRecordId = interviewRecordId,
                fromQuestionId = fromQuestionId,
                toQuestionId = toQuestionId,
                relationType = RELATION_TYPE_FOLLOW_UP,
                triggerType = TRIGGER_TYPE_INTERVIEWER_PROBE,
                createdAt = now,
            )
        }
        if (explicitEdges.isNotEmpty()) {
            return explicitEdges
        }
        return persistedQuestions.zipWithNext().map { (fromQuestion, toQuestion) ->
            InterviewRecordFollowUpEdgeEntity(
                interviewRecordId = interviewRecordId,
                fromQuestionId = fromQuestion.id,
                toQuestionId = toQuestion.id,
                relationType = RELATION_TYPE_FOLLOW_UP,
                triggerType = TRIGGER_TYPE_INTERVIEWER_PROBE,
                createdAt = now,
            )
        }
    }

    private fun parseTranscript(transcriptText: String, now: Instant): ParsedInterviewTranscript {
        val lines = transcriptText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val classifiedSegments = lines.mapIndexed { index, line ->
            val (speakerType, normalizedText) = classifySpeaker(line)
            ParsedSegment(
                sequence = index + 1,
                startMs = index * 1_000L,
                endMs = ((index + 1) * 1_000L) - 1,
                speakerType = speakerType,
                rawText = line,
                cleanedText = normalizedText,
                confirmedText = normalizedText,
                confidenceScore = BigDecimal("0.85"),
                createdAt = now,
                updatedAt = now,
            )
        }
        val segments = mergeAdjacentSegments(classifiedSegments, now)
        val questions = mutableListOf<ParsedQuestion>()
        var questionOrder = 1
        var currentQuestionSegment: ParsedSegment? = null
        val currentAnswerSegments = mutableListOf<ParsedSegment>()

        fun flushQuestion() {
            val questionSegment = currentQuestionSegment ?: return
            val answerSegments = currentAnswerSegments.toList()
            val questionText = questionSegment.confirmedText.orEmpty()
            val questionType = inferQuestionType(questionText)
            val topicTags = inferTopicTags(questionText)
            val answerText = answerSegments.joinToString(" ") { it.confirmedText.orEmpty() }.trim()
            val parentQuestion = inferParentQuestion(questions, questionText, topicTags)
            questions += ParsedQuestion(
                orderIndex = questionOrder++,
                segmentStartSequence = questionSegment.sequence,
                segmentEndSequence = questionSegment.sequence,
                text = questionText,
                normalizedText = normalize(questionText),
                questionType = questionType,
                topicTags = topicTags,
                intentTags = inferIntentTags(questionType),
                derivedFromResumeSection = deriveResumeSection(topicTags),
                derivedFromResumeRecordType = deriveResumeRecordType(topicTags),
                derivedFromResumeRecordId = null,
                parentOrderIndex = parentQuestion?.orderIndex,
                answer = if (answerText.isBlank()) {
                    null
                } else {
                    ParsedAnswer(
                        segmentStartSequence = answerSegments.first().sequence,
                        segmentEndSequence = answerSegments.last().sequence,
                        text = answerText,
                        normalizedText = normalize(answerText),
                        summary = summarizeAnswer(answerText),
                        confidenceMarkers = inferConfidenceMarkers(answerText),
                        weaknessTags = inferWeaknessTags(answerText),
                        strengthTags = inferStrengthTags(answerText),
                        analysis = mapOf(
                            "specificity" to inferSpecificity(answerText),
                            "containsNumbers" to answerText.contains(Regex("\\d")),
                            "containsTradeoff" to containsTradeoff(answerText),
                            "containsProblemActionResult" to containsProblemActionResult(answerText),
                        ),
                    )
                },
            )
            currentQuestionSegment = null
            currentAnswerSegments.clear()
        }

        segments.forEach { segment ->
            if (segment.speakerType == SPEAKER_TYPE_INTERVIEWER) {
                flushQuestion()
                currentQuestionSegment = segment
            } else if (currentQuestionSegment != null) {
                currentAnswerSegments += segment
            }
        }
        flushQuestion()
        return ParsedInterviewTranscript(segments, questions)
    }

    private fun mergeAdjacentSegments(
        rawSegments: List<ParsedSegment>,
        now: Instant,
    ): List<ParsedSegment> {
        if (rawSegments.isEmpty()) return emptyList()
        val merged = mutableListOf<ParsedSegment>()
        rawSegments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.speakerType == segment.speakerType) {
                merged[merged.lastIndex] = ParsedSegment(
                    sequence = previous.sequence,
                    startMs = previous.startMs,
                    endMs = segment.endMs,
                    speakerType = previous.speakerType,
                    rawText = "${previous.rawText} ${segment.rawText}".trim(),
                    cleanedText = "${previous.cleanedText} ${segment.cleanedText}".trim(),
                    confirmedText = "${previous.confirmedText} ${segment.confirmedText}".trim(),
                    confidenceScore = if (previous.confidenceScore <= segment.confidenceScore) previous.confidenceScore else segment.confidenceScore,
                    createdAt = previous.createdAt,
                    updatedAt = now,
                )
            } else {
                merged += segment
            }
        }
        return merged.mapIndexed { index, segment ->
            ParsedSegment(
                sequence = index + 1,
                startMs = segment.startMs,
                endMs = segment.endMs,
                speakerType = segment.speakerType,
                rawText = segment.rawText,
                cleanedText = segment.cleanedText,
                confirmedText = segment.confirmedText,
                confidenceScore = segment.confidenceScore,
                createdAt = segment.createdAt,
                updatedAt = segment.updatedAt,
            )
        }
    }

    private fun inferParentQuestion(
        existingQuestions: List<ParsedQuestion>,
        questionText: String,
        topicTags: List<String>,
    ): ParsedQuestion? {
        val normalizedQuestionText = normalize(questionText)
        val latestQuestion = existingQuestions.lastOrNull() ?: return null
        if (isLikelyFollowUpQuestion(normalizedQuestionText)) {
            return latestQuestion
        }
        val overlappedQuestion = existingQuestions
            .asReversed()
            .firstOrNull { existing ->
                existing.topicTags.intersect(topicTags.toSet()).isNotEmpty() && topicTags.isNotEmpty()
            }
        return overlappedQuestion?.takeIf {
            normalizedQuestionText.contains("그", ignoreCase = true) ||
                normalizedQuestionText.contains("then", ignoreCase = true) ||
                normalizedQuestionText.contains("당시", ignoreCase = true) ||
                normalizedQuestionText.startsWith("어떻게", ignoreCase = true) ||
                normalizedQuestionText.startsWith("how ", ignoreCase = true) ||
                normalizedQuestionText.startsWith("what ", ignoreCase = true) ||
                normalizedQuestionText.length <= 60
        }
    }

    private fun isLikelyFollowUpQuestion(normalizedQuestionText: String): Boolean {
        val followUpMarkers = listOf(
            "구체적으로",
            "좀 더",
            "그때",
            "당시",
            "이후",
            "why exactly",
            "how exactly",
            "what happened next",
            "can you elaborate",
            "tell me more",
            "how did you",
        )
        return followUpMarkers.any { normalizedQuestionText.contains(it, ignoreCase = true) } ||
            normalizedQuestionText.startsWith("그리고", ignoreCase = true)
    }

    private fun buildInterviewerProfile(
        userId: Long,
        recordId: Long,
        questions: List<ParsedQuestion>,
        profileOverride: PracticalInterviewInterviewerProfileOverride?,
        structuringSource: String,
        now: Instant,
    ): InterviewerProfileEntity {
        val favoriteTopics = questions.flatMap { it.topicTags }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(5).map { it.key }
        val followUpCount = questions.count { it.parentOrderIndex != null }
        val pressureLevel = when {
            questions.any { it.text.contains("왜", ignoreCase = true) || it.text.contains("really", ignoreCase = true) } -> "high"
            followUpCount >= 2 -> "high"
            questions.size >= 5 -> "medium"
            else -> "low"
        }
        val depthPreference = when {
            followUpCount >= 2 -> "deep"
            questions.any { it.questionType == QUESTION_TYPE_TECHNICAL_DEPTH || it.questionType == QUESTION_TYPE_PROJECT } -> "deep"
            else -> "moderate"
        }
        val followUpPatterns = buildList {
            if (followUpCount > 0) add("clarification_probe")
            if (questions.any { it.parentOrderIndex != null && "performance" in it.topicTags }) add("metric_probe")
            if (questions.any { it.parentOrderIndex != null && "project" in it.topicTags }) add("deep_dive_probe")
            if (questions.zipWithNext().any { (first, second) -> second.parentOrderIndex == first.orderIndex }) add("sequential_probe")
        }.ifEmpty { listOf("sequential_probe") }
        val styleTags = buildList {
            add("structured_probe")
            if (pressureLevel == "high") add("pressure_probe")
            if (depthPreference == "deep") add("depth_probe")
        }
        return InterviewerProfileEntity(
            userId = userId,
            sourceInterviewRecordId = recordId,
            styleTagsJson = objectMapper.writeValueAsString(profileOverride?.styleTags ?: styleTags),
            toneProfile = profileOverride?.toneProfile ?: if (pressureLevel == "high") "probing" else "measured",
            pressureLevel = profileOverride?.pressureLevel ?: pressureLevel,
            depthPreference = profileOverride?.depthPreference ?: depthPreference,
            followUpPatternJson = objectMapper.writeValueAsString(profileOverride?.followUpPatterns ?: followUpPatterns),
            favoriteTopicsJson = objectMapper.writeValueAsString(profileOverride?.favoriteTopics ?: favoriteTopics),
            openingPattern = profileOverride?.openingPattern ?: questions.firstOrNull()?.questionType,
            closingPattern = profileOverride?.closingPattern ?: questions.lastOrNull()?.questionType,
            structuringSource = structuringSource,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun buildOverallSummary(questions: List<ParsedQuestion>): String {
        if (questions.isEmpty()) return "Imported interview record without structured questions."
        val topics = questions.flatMap { it.topicTags }.distinct().joinToString(", ").ifBlank { "general" }
        val followUpCount = questions.count { it.parentOrderIndex != null }
        val quantifiedAnswers = questions.count { it.answer?.strengthTags?.contains("quantified") == true }
        return buildString {
            append("Imported ${questions.size} interview questions")
            append(" across topics $topics")
            if (followUpCount > 0) append(", including $followUpCount follow-up probes")
            if (quantifiedAnswers > 0) append(" and $quantifiedAnswers quantified candidate answers")
            append(".")
        }
    }

    private fun classifySpeaker(line: String): Pair<String, String> {
        val normalized = line.trim()
        val lowered = normalized.lowercase()
        val interviewerPrefixes = listOf("interviewer:", "q:", "면접관:", "질문:")
        val candidatePrefixes = listOf("candidate:", "a:", "지원자:", "답변:", "me:")
        interviewerPrefixes.firstOrNull { lowered.startsWith(it) }?.let { return SPEAKER_TYPE_INTERVIEWER to normalized.substringAfter(":").trim() }
        candidatePrefixes.firstOrNull { lowered.startsWith(it) }?.let { return SPEAKER_TYPE_CANDIDATE to normalized.substringAfter(":").trim() }
        return if (normalized.endsWith("?")) {
            SPEAKER_TYPE_INTERVIEWER to normalized
        } else {
            SPEAKER_TYPE_CANDIDATE to normalized
        }
    }

    private fun inferQuestionType(questionText: String): String = when {
        questionText.contains("자기소개") || questionText.contains("introduce yourself", true) -> QUESTION_TYPE_INTRO
        questionText.contains("왜") || questionText.contains("why", true) -> QUESTION_TYPE_MOTIVATION
        questionText.contains("프로젝트") || questionText.contains("project", true) -> QUESTION_TYPE_PROJECT
        questionText.contains("설계") || questionText.contains("design", true) -> QUESTION_TYPE_SYSTEM_DESIGN
        questionText.contains("성능") || questionText.contains("cache", true) || questionText.contains("redis", true) -> QUESTION_TYPE_TECHNICAL_DEPTH
        else -> QUESTION_TYPE_GENERAL
    }

    private fun inferTopicTags(text: String): List<String> {
        val tags = mutableListOf<String>()
        val lowered = text.lowercase()
        if ("redis" in lowered || "cache" in lowered || "캐시" in text) tags += "caching"
        if ("성능" in text || "latency" in lowered || "throughput" in lowered) tags += "performance"
        if ("프로젝트" in text || "project" in lowered) tags += "project"
        if ("설계" in text || "design" in lowered || "architecture" in lowered) tags += "system-design"
        if ("협업" in text || "conflict" in lowered) tags += "collaboration"
        if (tags.isEmpty()) tags += "general"
        return tags.distinct()
    }

    private fun inferIntentTags(questionType: String): List<String> = when (questionType) {
        QUESTION_TYPE_TECHNICAL_DEPTH -> listOf("technical_validation")
        QUESTION_TYPE_PROJECT -> listOf("resume_validation")
        QUESTION_TYPE_SYSTEM_DESIGN -> listOf("design_probe")
        QUESTION_TYPE_MOTIVATION -> listOf("motivation_probe")
        QUESTION_TYPE_INTRO -> listOf("intro_probe")
        else -> listOf("general_probe")
    }

    private fun deriveResumeSection(topicTags: List<String>): String? = when {
        "project" in topicTags -> "project"
        "performance" in topicTags || "caching" in topicTags -> "experience"
        else -> null
    }

    private fun deriveResumeRecordType(topicTags: List<String>): String? = when (deriveResumeSection(topicTags)) {
        "project" -> "resume_project_snapshot"
        "experience" -> "resume_experience_snapshot"
        else -> null
    }

    private fun inferConfidenceMarkers(answerText: String): List<String> = buildList {
        if (answerText.contains("아마") || answerText.contains("maybe", true)) add("uncertain")
        if (answerText.contains(Regex("\\d"))) add("quantified")
    }

    private fun inferWeaknessTags(answerText: String): List<String> = buildList {
        if (answerText.length < 80) add("too_short")
        if (!answerText.contains(Regex("\\d"))) add("missing_metrics")
        if (!containsProblemActionResult(answerText)) add("missing_star_shape")
        if (!containsTradeoff(answerText)) add("missing_tradeoff")
    }

    private fun inferStrengthTags(answerText: String): List<String> = buildList {
        if (answerText.length >= 120) add("detailed")
        if (answerText.contains(Regex("\\d"))) add("quantified")
        if (containsProblemActionResult(answerText)) add("structured")
        if (containsTradeoff(answerText)) add("tradeoff_aware")
    }

    private fun summarizeAnswer(answerText: String): String {
        val normalized = normalize(answerText)
        val summary = normalized.split(Regex("(?<=[.!?])\\s+")).take(2).joinToString(" ").take(240).trim()
        return if (summary.isBlank()) normalized.take(240) else summary
    }

    private fun inferSpecificity(answerText: String): String = when {
        answerText.length >= 180 || answerText.contains(Regex("\\d")) -> "high"
        answerText.length >= 100 -> "medium"
        else -> "low"
    }

    private fun containsProblemActionResult(answerText: String): Boolean {
        val lowered = answerText.lowercase()
        val hasProblem = listOf("문제", "이슈", "상황", "배경", "problem", "issue").any { lowered.contains(it) }
        val hasAction = listOf("구현", "적용", "설계", "조치", "개선", "implemented", "designed", "changed").any { lowered.contains(it) }
        val hasResult = listOf("결과", "성과", "개선", "줄였", "높였", "result", "outcome", "improved", "reduced").any { lowered.contains(it) }
        return (hasProblem && hasAction) || (hasAction && hasResult)
    }

    private fun containsTradeoff(answerText: String): Boolean {
        val lowered = answerText.lowercase()
        return listOf("대신", "트레이드오프", "하지만", "반면", "우회", "trade-off", "tradeoff", "however", "instead").any {
            lowered.contains(it)
        }
    }

    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private fun isEditedSegment(segment: InterviewTranscriptSegmentEntity): Boolean {
        val cleaned = segment.cleanedText?.trim().orEmpty()
        val confirmed = segment.confirmedText?.trim().orEmpty()
        return confirmed.isNotBlank() && confirmed != cleaned
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    companion object {
        private const val STRUCTURING_STAGE_DETERMINISTIC = "deterministic"
        private const val STRUCTURING_STAGE_AI_ENRICHED = "ai_enriched"
        private const val STRUCTURING_STAGE_CONFIRMED = "confirmed"
        private const val INTERVIEW_TYPE_GENERAL = "general"
        private const val TRANSCRIPT_STATUS_PENDING = "pending"
        private const val TRANSCRIPT_STATUS_CONFIRMED = "confirmed"
        private const val ANALYSIS_STATUS_PENDING = "pending"
        private const val ANALYSIS_STATUS_COMPLETED = "completed"
        private const val SPEAKER_TYPE_INTERVIEWER = "interviewer"
        private const val SPEAKER_TYPE_CANDIDATE = "candidate"
        private const val QUESTION_TYPE_INTRO = "intro"
        private const val QUESTION_TYPE_MOTIVATION = "motivation"
        private const val QUESTION_TYPE_PROJECT = "project"
        private const val QUESTION_TYPE_TECHNICAL_DEPTH = "technical_depth"
        private const val QUESTION_TYPE_SYSTEM_DESIGN = "system_design"
        private const val QUESTION_TYPE_GENERAL = "general"
        private const val RELATION_TYPE_FOLLOW_UP = "follow_up"
        private const val TRIGGER_TYPE_INTERVIEWER_PROBE = "interviewer_probe"
        private const val REVIEW_REPLAY_SESSION_TYPE = "replay_mock"
        private const val REVIEW_REPLAY_MODE_ORIGINAL = "original_replay"
        private const val REPLAY_BLOCKER_NO_QUESTIONS = "no_questions"
        private const val REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE = "no_interviewer_profile"
        private val LOW_CONFIDENCE_THRESHOLD = BigDecimal("0.80")
        private const val REVIEW_ORIGIN_RESUME_LINKED = "resume_linked"
        private const val REVIEW_ORIGIN_JOB_POSTING_LINKED = "job_posting_linked"
        private const val REVIEW_ORIGIN_HYBRID_LINKED = "hybrid_linked"
        private const val REVIEW_ORIGIN_GENERAL = "general"
    }
}

data class ParsedInterviewTranscript(
    val segments: List<ParsedSegment>,
    val questions: List<ParsedQuestion>,
    val overallSummaryOverride: String? = null,
    val interviewerProfileOverride: PracticalInterviewInterviewerProfileOverride? = null,
    val structuringSource: String = "deterministic",
)

private data class DecodedReviewAnswerQuality(
    val weaknessTags: List<String>,
    val strengthTags: List<String>,
    val confidenceMarkers: List<String>,
)

data class ParsedSegment(
    val sequence: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerType: String,
    val rawText: String,
    val cleanedText: String,
    val confirmedText: String,
    val confidenceScore: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toEntity(interviewRecordId: Long): InterviewTranscriptSegmentEntity = InterviewTranscriptSegmentEntity(
        interviewRecordId = interviewRecordId,
        startMs = startMs,
        endMs = endMs,
        speakerType = speakerType,
        rawText = rawText,
        cleanedText = cleanedText,
        confirmedText = confirmedText,
        confidenceScore = confidenceScore,
        sequence = sequence,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

data class ParsedQuestion(
    val orderIndex: Int,
    val segmentStartSequence: Int,
    val segmentEndSequence: Int,
    val text: String,
    val normalizedText: String,
    val questionType: String,
    val topicTags: List<String>,
    val intentTags: List<String>,
    val derivedFromResumeSection: String?,
    val derivedFromResumeRecordType: String?,
    val derivedFromResumeRecordId: Long?,
    val parentOrderIndex: Int?,
    val answer: ParsedAnswer?,
)

data class ParsedAnswer(
    val segmentStartSequence: Int,
    val segmentEndSequence: Int,
    val text: String,
    val normalizedText: String,
    val summary: String,
    val confidenceMarkers: List<String>,
    val weaknessTags: List<String>,
    val strengthTags: List<String>,
    val analysis: Map<String, Any>,
)
