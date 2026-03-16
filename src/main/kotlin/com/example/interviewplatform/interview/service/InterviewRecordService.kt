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
import com.example.interviewplatform.interview.dto.InterviewRecordReviewLaneItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewLaneBlockerDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewLaneSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewQuestionOriginSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordReplayBlockerDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordReplayReadinessDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptSegmentActionDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptIssueSummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordAnswerQualitySummaryDto
import com.example.interviewplatform.interview.dto.InterviewRecordTimelineNavigationDto
import com.example.interviewplatform.interview.dto.InterviewRecordTimelineNavigationItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewActionRecommendationsDto
import com.example.interviewplatform.interview.dto.InterviewRecordReviewActionBlockerDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordReplayLaunchPresetDto
import com.example.interviewplatform.interview.dto.InterviewRecordProvenanceComparisonSummaryDto
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
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.nio.file.Path
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
    private val practicalInterviewTranscriptExtractionService: PracticalInterviewTranscriptExtractionService,
    private val practicalInterviewStructuringEnrichmentService: PracticalInterviewStructuringEnrichmentService,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val clockService: ClockService,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${app.upload.interview-audio.max-size-bytes:52428800}")
    private val interviewAudioUploadMaxSizeBytes: Long,
    @Value("\${app.interview.transcription.max-auto-retries:3}")
    private val transcriptMaxAutoRetries: Int,
    @Value("\${app.interview.transcription.retry-base-delay-seconds:300}")
    private val transcriptRetryBaseDelaySeconds: Long,
    @Value("\${app.interview.transcription.processing-timeout-seconds:900}")
    private val transcriptProcessingTimeoutSeconds: Long,
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
        val automaticTranscriptionConfigured = practicalInterviewTranscriptExtractionService.isConfigured()
        logger.debug(
            "Creating interview record userId={}, audioFileName={}, contentType={}, fileSize={}, transcriptSource={}, transcriptLength={}",
            userId,
            file.originalFilename,
            file.contentType,
            file.size,
            when {
                transcriptText?.trim()?.isNotEmpty() == true -> "request"
                automaticTranscriptionConfigured -> "queued_audio_transcription"
                else -> "none"
            },
            normalizedTranscript?.length ?: 0,
        )

        val transcriptStatus = when {
            normalizedTranscript != null -> TRANSCRIPT_STATUS_CONFIRMED
            automaticTranscriptionConfigured -> TRANSCRIPT_STATUS_PENDING
            else -> TRANSCRIPT_STATUS_FAILED
        }
        val analysisStatus = when {
            normalizedTranscript != null -> ANALYSIS_STATUS_COMPLETED
            automaticTranscriptionConfigured -> ANALYSIS_STATUS_PENDING
            else -> ANALYSIS_STATUS_FAILED
        }
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
                transcriptStatus = transcriptStatus,
                transcriptErrorCode = if (normalizedTranscript == null && !automaticTranscriptionConfigured) TRANSCRIPT_ERROR_NOT_CONFIGURED else null,
                transcriptErrorMessage = if (normalizedTranscript == null && !automaticTranscriptionConfigured) {
                    "Automatic transcription is not configured for this environment."
                } else {
                    null
                },
                transcriptRetryCount = 0,
                transcriptLastAttemptAt = null,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = if (normalizedTranscript == null && automaticTranscriptionConfigured) now else null,
                analysisStatus = analysisStatus,
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
        logger.debug(
            "Created interview record recordId={}, transcriptStatus={}, analysisStatus={}, structuringStage={}",
            record.id,
            record.transcriptStatus,
            record.analysisStatus,
            record.structuringStage,
        )

        if (normalizedTranscript != null) {
            logger.debug("Triggering structuring pipeline immediately recordId={}", record.id)
            try {
                rebuildStructuredData(record, normalizedTranscript, now)
            } catch (ex: Exception) {
                logger.error("Structuring pipeline failed recordId={}", record.id, ex)
                throw ex
            }
            record = requireOwnedRecord(userId, record.id)
            logger.debug(
                "Structuring pipeline completed recordId={}, transcriptStatus={}, analysisStatus={}, structuringStage={}, interviewerProfileId={}",
                record.id,
                record.transcriptStatus,
                record.analysisStatus,
                record.structuringStage,
                record.interviewerProfileId,
            )
        } else {
            if (automaticTranscriptionConfigured) {
                logger.debug("Queued transcript extraction recordId={}", record.id)
                publishTranscriptRequested(record.id)
            } else {
                logger.warn("Transcript extraction unavailable recordId={} because extractor is not configured", record.id)
            }
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
        logger.debug(
            "Fetched transcript recordId={}, transcriptStatus={}, analysisStatus={}, segmentCount={}",
            recordId,
            record.transcriptStatus,
            record.analysisStatus,
            segments.size,
        )
        if (segments.isEmpty()) {
            logger.warn(
                "Transcript has no segments recordId={}, transcriptStatus={}, analysisStatus={}",
                recordId,
                record.transcriptStatus,
                record.analysisStatus,
            )
        }
        return InterviewRecordTranscriptDto(
            interviewRecordId = record.id,
            rawTranscript = record.rawTranscript,
            cleanedTranscript = record.cleanedTranscript,
            confirmedTranscript = record.confirmedTranscript,
            transcriptStatus = record.transcriptStatus,
            transcriptErrorCode = record.transcriptErrorCode,
            transcriptErrorMessage = record.transcriptErrorMessage,
            transcriptRetryCount = record.transcriptRetryCount,
            transcriptLastAttemptAt = record.transcriptLastAttemptAt,
            transcriptNextRetryAt = record.transcriptNextRetryAt,
            segments = segments.map(InterviewRecordMapper::toTranscriptSegmentDto),
            updatedAt = record.updatedAt,
        )
    }

    @Transactional
    fun retryTranscription(userId: Long, recordId: Long): InterviewRecordDetailDto {
        val record = requireOwnedRecord(userId, recordId)
        if (record.confirmedTranscript?.isNotBlank() == true || record.transcriptStatus == TRANSCRIPT_STATUS_CONFIRMED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Transcript is already confirmed for record: $recordId")
        }
        if (record.transcriptStatus == TRANSCRIPT_STATUS_PROCESSING && !isProcessingTimedOut(record, clockService.now())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Transcript extraction is already processing for record: $recordId")
        }
        val now = clockService.now()
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
                transcriptStatus = if (practicalInterviewTranscriptExtractionService.isConfigured()) TRANSCRIPT_STATUS_PENDING else TRANSCRIPT_STATUS_FAILED,
                transcriptErrorCode = if (practicalInterviewTranscriptExtractionService.isConfigured()) null else TRANSCRIPT_ERROR_NOT_CONFIGURED,
                transcriptErrorMessage = if (practicalInterviewTranscriptExtractionService.isConfigured()) null else {
                    "Automatic transcription is not configured for this environment."
                },
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = if (practicalInterviewTranscriptExtractionService.isConfigured()) now else null,
                analysisStatus = if (practicalInterviewTranscriptExtractionService.isConfigured()) ANALYSIS_STATUS_PENDING else ANALYSIS_STATUS_FAILED,
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
        if (practicalInterviewTranscriptExtractionService.isConfigured()) {
            publishTranscriptRequested(updated.id)
        }
        return toDetailDto(updated)
    }

    @Transactional
    fun processQueuedTranscriptExtraction(recordId: Long) {
        val record = interviewRecordRepository.findById(recordId).orElse(null) ?: run {
            logger.warn("Skipping transcript extraction because record was not found recordId={}", recordId)
            return
        }
        if (record.transcriptStatus == TRANSCRIPT_STATUS_CONFIRMED || record.confirmedTranscript?.isNotBlank() == true) {
            logger.debug("Skipping transcript extraction because transcript is already confirmed recordId={}", recordId)
            return
        }
        if (!practicalInterviewTranscriptExtractionService.isConfigured()) {
            markTranscriptionFailure(
                record = record,
                errorCode = TRANSCRIPT_ERROR_NOT_CONFIGURED,
                errorMessage = "Automatic transcription is not configured for this environment.",
                now = clockService.now(),
                incrementRetryCount = false,
            )
            return
        }

        val now = clockService.now()
        if (record.transcriptStatus == TRANSCRIPT_STATUS_PROCESSING && !isProcessingTimedOut(record, now)) {
            logger.debug("Skipping transcript extraction because another worker is already processing recordId={}", recordId)
            return
        }

        val processingRecord = interviewRecordRepository.save(
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
                transcriptStatus = TRANSCRIPT_STATUS_PROCESSING,
                transcriptErrorCode = null,
                transcriptErrorMessage = null,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = now,
                transcriptProcessingStartedAt = now,
                transcriptNextRetryAt = null,
                analysisStatus = ANALYSIS_STATUS_PENDING,
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

        try {
            val audioPath = resolveStoredAudioPath(processingRecord)
            val transcript = practicalInterviewTranscriptExtractionService.extractOrNull(
                audioFilePath = audioPath,
                fileName = processingRecord.sourceAudioFileName ?: audioPath.fileName.toString(),
                contentType = processingRecord.sourceAudioContentType,
            )
            if (transcript.isNullOrBlank()) {
                markTranscriptionFailure(
                    record = processingRecord,
                    errorCode = TRANSCRIPT_ERROR_EMPTY_TRANSCRIPT,
                    errorMessage = "Automatic transcription did not return any transcript text.",
                    now = clockService.now(),
                )
                return
            }
            rebuildStructuredData(processingRecord, transcript, clockService.now())
            logger.info("Transcript extraction completed recordId={}", recordId)
        } catch (ex: Exception) {
            logger.warn("Transcript extraction failed recordId={}", recordId, ex)
            markTranscriptionFailure(
                record = processingRecord,
                errorCode = TRANSCRIPT_ERROR_EXTRACTION_FAILED,
                errorMessage = ex.message ?: "Automatic transcription failed.",
                now = clockService.now(),
            )
        }
    }

    @Transactional
    fun recoverTimedOutTranscriptExtractions() {
        val now = clockService.now()
        val threshold = now.minusSeconds(transcriptProcessingTimeoutSeconds)
        interviewRecordRepository.findTimedOutProcessingTranscriptRecords(threshold)
            .forEach { record ->
                logger.warn("Recovering timed-out transcript extraction recordId={}", record.id)
                markTranscriptionFailure(
                    record = record,
                    errorCode = TRANSCRIPT_ERROR_PROCESSING_TIMEOUT,
                    errorMessage = "Automatic transcription timed out before completion.",
                    now = now,
                )
            }
    }

    @Transactional
    fun enqueueEligibleTranscriptRetries() {
        if (!practicalInterviewTranscriptExtractionService.isConfigured()) {
            return
        }
        val now = clockService.now()
        interviewRecordRepository.findRetryableTranscriptRecords(now)
            .forEach { record ->
                if (record.transcriptStatus == TRANSCRIPT_STATUS_PROCESSING && !isProcessingTimedOut(record, now)) {
                    return@forEach
                }
                publishTranscriptRequested(record.id)
            }
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
                transcriptErrorCode = null,
                transcriptErrorMessage = null,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = null,
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
        val persistedQuestions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(recordId)
        val answersByQuestionId = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(
            persistedQuestions.map { it.id },
        )
            .associateBy { it.interviewRecordQuestionId }
        val questions = interviewRecordQuestionAssetService.ensureLinkedQuestionAssets(
            record = record,
            questions = persistedQuestions,
            answersByQuestionId = answersByQuestionId,
            now = clockService.now(),
        )
        logger.debug(
            "Fetched questions recordId={}, analysisStatus={}, persistedQuestionCount={}, responseQuestionCount={}, answerCount={}",
            recordId,
            record.analysisStatus,
            persistedQuestions.size,
            questions.size,
            answersByQuestionId.size,
        )
        if (questions.isEmpty()) {
            logger.warn(
                "No structured questions available recordId={}, analysisStatus={}, transcriptStatus={}",
                recordId,
                record.analysisStatus,
                record.transcriptStatus,
            )
        }
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
        val segmentSequenceById = segments.associate { it.id to it.sequence }
        val answerByQuestionId = answers.associateBy { it.interviewRecordQuestionId }
        val questionById = questions.associateBy { it.id }
        val interviewerProfile = interviewerProfileRepository.findBySourceInterviewRecordId(recordId)
        val questionSummaries = questions.map { question ->
            val answer = answerByQuestionId[question.id]
            val confidenceMarkers = answer?.let { decodeStringList(it.confidenceMarkersJson) }.orEmpty()
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
                confidenceMarkers = confidenceMarkers,
                weaknessTags = weaknessTags,
                strengthTags = strengthTags,
                questionStructuringSource = question.structuringSource,
                answerStructuringSource = answer?.structuringSource,
            )
        }
        val replayReadiness = buildReplayReadiness(questionSummaries, interviewerProfile != null)
        val transcriptIssueSummary = buildTranscriptIssueSummary(
            recordId = record.id,
            segments = segments,
            questions = questions,
            answers = answers,
            segmentSequenceById = segmentSequenceById,
            questionById = questionById,
        )
        val followUpThreads = buildReviewFollowUpThreads(questionSummaries)
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
            replayReadiness = replayReadiness,
            transcriptIssueSummary = transcriptIssueSummary,
            reviewLaneSummary = buildReviewLaneSummary(
                transcriptIssueSummary = transcriptIssueSummary,
                questionSummaries = questionSummaries,
                followUpThreads = followUpThreads,
            ),
            answerQualitySummary = buildAnswerQualitySummary(answers),
            timelineNavigation = buildTimelineNavigation(questions, answerByQuestionId, segmentSequenceById),
            actionRecommendations = buildActionRecommendations(
                recordStructuringStage = record.structuringStage,
                editedSegmentCount = segments.count(::isEditedSegment),
                weakAnswerCount = answers.count { decodeStringList(it.weaknessTagsJson).isNotEmpty() },
                replayReadiness = replayReadiness,
            ),
            replayLaunchPreset = buildReplayLaunchPreset(recordId, questionSummaries, replayReadiness),
            provenanceComparisonSummary = buildProvenanceComparisonSummary(
                record = record,
                questions = questions,
                answers = answers,
                interviewerProfileSource = interviewerProfile?.structuringSource,
            ),
            questionSummaries = questionSummaries,
            followUpThreads = followUpThreads,
        )
    }

    private fun buildReviewLaneSummary(
        transcriptIssueSummary: InterviewRecordTranscriptIssueSummaryDto,
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
        followUpThreads: List<InterviewRecordReviewFollowUpThreadDto>,
    ): InterviewRecordReviewLaneSummaryDto {
        val questionNeedsReviewCount = questionSummaries.count {
            it.hasWeakAnswer ||
                it.questionStructuringSource != STRUCTURING_STAGE_CONFIRMED ||
                (it.answerStructuringSource != null && it.answerStructuringSource != STRUCTURING_STAGE_CONFIRMED)
        }
        val threadNeedsReviewCount = followUpThreads.count { it.recommendedAction != THREAD_ACTION_STABLE_CHAIN }
        val transcriptBlockingReasons = buildList {
            if (transcriptIssueSummary.unresolvedIssueCount > 0) {
                add(REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS)
            }
        }
        val questionBlockingReasons = buildList {
            if (questionSummaries.any { it.hasWeakAnswer }) {
                add(REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT)
            }
            if (questionSummaries.any {
                    it.questionStructuringSource != STRUCTURING_STAGE_CONFIRMED ||
                        (it.answerStructuringSource != null && it.answerStructuringSource != STRUCTURING_STAGE_CONFIRMED)
                }
            ) {
                add(REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT)
            }
        }
        val threadBlockingReasons = buildList {
            if (followUpThreads.any { it.recommendedAction == THREAD_ACTION_REVIEW_WEAK_CHAIN }) {
                add(REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT)
            }
            if (followUpThreads.isEmpty()) {
                add(REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE)
            }
        }
        val threadPrimaryAction = when {
            followUpThreads.any { it.recommendedAction == THREAD_ACTION_REVIEW_WEAK_CHAIN } -> THREAD_ACTION_REVIEW_WEAK_CHAIN
            followUpThreads.any { it.recommendedAction == THREAD_ACTION_REPLAY_CHAIN } -> THREAD_ACTION_REPLAY_CHAIN
            else -> THREAD_ACTION_STABLE_CHAIN
        }
        val transcriptPrimaryAction = if (transcriptIssueSummary.unresolvedIssueCount > 0) {
            REVIEW_ACTION_REVIEW_TRANSCRIPT
        } else {
            REVIEW_ACTION_CONFIRM
        }
        val questionPrimaryAction = if (questionNeedsReviewCount == 0) REVIEW_ACTION_CONFIRM else REVIEW_ACTION_REVIEW_ANSWERS
        val transcriptSeverity = transcriptIssueSummary.segmentActions
            .map { it.severity }
            .minByOrNull(::segmentSeverityRank)
            ?: SEGMENT_SEVERITY_LOW
        val transcriptHighestPriority = transcriptIssueSummary.segmentActions
            .map { it.priority }
            .minByOrNull(::segmentPriorityRank)
            ?: SEGMENT_PRIORITY_P2
        val questionSeverity = when {
            questionSummaries.any { it.hasWeakAnswer } -> SEGMENT_SEVERITY_MEDIUM
            questionNeedsReviewCount > 0 -> SEGMENT_SEVERITY_LOW
            else -> SEGMENT_SEVERITY_LOW
        }
        val questionHighestPriority = when {
            questionSummaries.any { it.hasWeakAnswer } -> SEGMENT_PRIORITY_P1
            questionNeedsReviewCount > 0 -> SEGMENT_PRIORITY_P2
            else -> SEGMENT_PRIORITY_P2
        }
        val threadSeverity = when {
            threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN -> SEGMENT_SEVERITY_MEDIUM
            threadPrimaryAction == THREAD_ACTION_REPLAY_CHAIN -> SEGMENT_SEVERITY_LOW
            else -> SEGMENT_SEVERITY_LOW
        }
        val threadHighestPriority = when {
            threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN -> SEGMENT_PRIORITY_P1
            else -> SEGMENT_PRIORITY_P2
        }
        val transcriptHighlightVariant = when {
            transcriptIssueSummary.unresolvedIssueCount > 0 -> REVIEW_LANE_HIGHLIGHT_DANGER
            transcriptIssueSummary.segmentActions.isNotEmpty() -> REVIEW_LANE_HIGHLIGHT_SUCCESS
            else -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
        }
        val questionHighlightVariant = when {
            questionSummaries.any { it.hasWeakAnswer } -> REVIEW_LANE_HIGHLIGHT_WARNING
            questionNeedsReviewCount > 0 -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
            questionSummaries.isNotEmpty() -> REVIEW_LANE_HIGHLIGHT_SUCCESS
            else -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
        }
        val threadHighlightVariant = when {
            threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN -> REVIEW_LANE_HIGHLIGHT_WARNING
            followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0 -> REVIEW_LANE_HIGHLIGHT_SUCCESS
            else -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
        }
        val laneSortTriples = listOf(
            Triple(REVIEW_LANE_KEY_TRANSCRIPT, transcriptSeverity, transcriptHighestPriority),
            Triple(REVIEW_LANE_KEY_QUESTION, questionSeverity, questionHighestPriority),
            Triple(REVIEW_LANE_KEY_THREAD, threadSeverity, threadHighestPriority),
        ).sortedWith(
            compareBy<Triple<String, String, String>>(
                { segmentSeverityRank(it.second) },
                { segmentPriorityRank(it.third) },
                { reviewLaneTieBreakRank(it.first) },
            ),
        )
        val laneSortOrderByKey = laneSortTriples
            .mapIndexed { index, triple -> triple.first to (index + 1) }
            .toMap()
        return InterviewRecordReviewLaneSummaryDto(
            transcript = InterviewRecordReviewLaneItemDto(
                sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_TRANSCRIPT),
                highlightVariant = transcriptHighlightVariant,
                badgeText = resolveReviewLaneBadgeText(
                    readiness = transcriptIssueSummary.confirmationReadiness,
                    needsReviewCount = transcriptIssueSummary.unresolvedIssueCount,
                ),
                summaryText = buildReviewLaneSummaryText(
                    laneLabel = "Transcript",
                    totalCount = transcriptIssueSummary.segmentActions.size,
                    readyCount = transcriptIssueSummary.resolvedIssueCount,
                    needsReviewCount = transcriptIssueSummary.unresolvedIssueCount,
                ),
                recommendedTab = resolveReviewLaneRecommendedTab(REVIEW_LANE_KEY_TRANSCRIPT),
                defaultExpanded = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_TRANSCRIPT) == 1,
                analyticsKey = buildReviewLaneAnalyticsKey(REVIEW_LANE_KEY_TRANSCRIPT),
                trackingContext = buildReviewLaneTrackingContext(
                    laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                    sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_TRANSCRIPT),
                    highlightVariant = transcriptHighlightVariant,
                    readiness = transcriptIssueSummary.confirmationReadiness,
                    primaryAction = transcriptPrimaryAction,
                ),
                helpText = buildReviewLaneHelpText(REVIEW_LANE_KEY_TRANSCRIPT),
                whyItMatters = buildReviewLaneWhyItMatters(REVIEW_LANE_KEY_TRANSCRIPT),
                accessibilityLabel = buildReviewLaneAccessibilityLabel(REVIEW_LANE_KEY_TRANSCRIPT),
                screenReaderSummary = buildReviewLaneScreenReaderSummary(
                    laneLabel = "Transcript",
                    readiness = transcriptIssueSummary.confirmationReadiness,
                    needsReviewCount = transcriptIssueSummary.unresolvedIssueCount,
                    highestPriority = transcriptHighestPriority,
                ),
                totalCount = transcriptIssueSummary.segmentActions.size,
                readyCount = transcriptIssueSummary.resolvedIssueCount,
                needsReviewCount = transcriptIssueSummary.unresolvedIssueCount,
                readiness = transcriptIssueSummary.confirmationReadiness,
                severity = transcriptSeverity,
                highestPriority = transcriptHighestPriority,
                primaryAction = transcriptPrimaryAction,
                primaryActionLabel = resolveReviewLaneActionLabel(transcriptPrimaryAction),
                primaryActionTarget = resolveReviewLaneActionTarget(
                    laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                    action = transcriptPrimaryAction,
                ),
                primaryActionTargetPayload = resolveReviewLaneActionTargetPayload(
                    laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                    action = transcriptPrimaryAction,
                ),
                secondaryAction = if (transcriptIssueSummary.unresolvedIssueCount == 0) REVIEW_ACTION_START_REPLAY else null,
                secondaryActionLabel = if (transcriptIssueSummary.unresolvedIssueCount == 0) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_START_REPLAY)
                } else {
                    null
                },
                secondaryActionTarget = if (transcriptIssueSummary.unresolvedIssueCount == 0) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                secondaryActionTargetPayload = if (transcriptIssueSummary.unresolvedIssueCount == 0) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                emptyStateMessage = if (transcriptIssueSummary.segmentActions.isEmpty()) {
                    "No transcript issues detected."
                } else {
                    null
                },
                emptyStateCtaAction = if (transcriptIssueSummary.segmentActions.isEmpty()) {
                    REVIEW_ACTION_START_REPLAY
                } else {
                    null
                },
                emptyStateCtaLabel = if (transcriptIssueSummary.segmentActions.isEmpty()) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_START_REPLAY)
                } else {
                    null
                },
                emptyStateCtaTarget = if (transcriptIssueSummary.segmentActions.isEmpty()) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                emptyStateCtaTargetPayload = if (transcriptIssueSummary.segmentActions.isEmpty()) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                completionMessage = if (
                    transcriptIssueSummary.segmentActions.isNotEmpty() &&
                    transcriptIssueSummary.unresolvedIssueCount == 0
                ) {
                    "Transcript review is ready."
                } else {
                    null
                },
                completionCtaAction = if (
                    transcriptIssueSummary.segmentActions.isNotEmpty() &&
                    transcriptIssueSummary.unresolvedIssueCount == 0
                ) {
                    REVIEW_ACTION_START_REPLAY
                } else {
                    null
                },
                completionCtaLabel = if (
                    transcriptIssueSummary.segmentActions.isNotEmpty() &&
                    transcriptIssueSummary.unresolvedIssueCount == 0
                ) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_START_REPLAY)
                } else {
                    null
                },
                completionCtaTarget = if (
                    transcriptIssueSummary.segmentActions.isNotEmpty() &&
                    transcriptIssueSummary.unresolvedIssueCount == 0
                ) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                completionCtaTargetPayload = if (
                    transcriptIssueSummary.segmentActions.isNotEmpty() &&
                    transcriptIssueSummary.unresolvedIssueCount == 0
                ) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_TRANSCRIPT,
                        action = REVIEW_ACTION_START_REPLAY,
                    )
                } else {
                    null
                },
                blockingReasons = transcriptBlockingReasons.distinct(),
                blockingReasonDetails = transcriptBlockingReasons
                    .distinct()
                    .map { buildReviewLaneBlockerDetail(REVIEW_LANE_KEY_TRANSCRIPT, it) },
            ),
            question = InterviewRecordReviewLaneItemDto(
                sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_QUESTION),
                highlightVariant = questionHighlightVariant,
                badgeText = resolveReviewLaneBadgeText(
                    readiness = if (questionNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    needsReviewCount = questionNeedsReviewCount,
                ),
                summaryText = buildReviewLaneSummaryText(
                    laneLabel = "Questions",
                    totalCount = questionSummaries.size,
                    readyCount = (questionSummaries.size - questionNeedsReviewCount).coerceAtLeast(0),
                    needsReviewCount = questionNeedsReviewCount,
                ),
                recommendedTab = resolveReviewLaneRecommendedTab(REVIEW_LANE_KEY_QUESTION),
                defaultExpanded = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_QUESTION) == 1,
                analyticsKey = buildReviewLaneAnalyticsKey(REVIEW_LANE_KEY_QUESTION),
                trackingContext = buildReviewLaneTrackingContext(
                    laneKey = REVIEW_LANE_KEY_QUESTION,
                    sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_QUESTION),
                    highlightVariant = questionHighlightVariant,
                    readiness = if (questionNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    primaryAction = questionPrimaryAction,
                ),
                helpText = buildReviewLaneHelpText(REVIEW_LANE_KEY_QUESTION),
                whyItMatters = buildReviewLaneWhyItMatters(REVIEW_LANE_KEY_QUESTION),
                accessibilityLabel = buildReviewLaneAccessibilityLabel(REVIEW_LANE_KEY_QUESTION),
                screenReaderSummary = buildReviewLaneScreenReaderSummary(
                    laneLabel = "Questions",
                    readiness = if (questionNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    needsReviewCount = questionNeedsReviewCount,
                    highestPriority = questionHighestPriority,
                ),
                totalCount = questionSummaries.size,
                readyCount = (questionSummaries.size - questionNeedsReviewCount).coerceAtLeast(0),
                needsReviewCount = questionNeedsReviewCount,
                readiness = if (questionNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                severity = questionSeverity,
                highestPriority = questionHighestPriority,
                primaryAction = questionPrimaryAction,
                primaryActionLabel = resolveReviewLaneActionLabel(questionPrimaryAction),
                primaryActionTarget = resolveReviewLaneActionTarget(
                    laneKey = REVIEW_LANE_KEY_QUESTION,
                    action = questionPrimaryAction,
                ),
                primaryActionTargetPayload = resolveReviewLaneActionTargetPayload(
                    laneKey = REVIEW_LANE_KEY_QUESTION,
                    action = questionPrimaryAction,
                ),
                secondaryAction = if (questionNeedsReviewCount > 0) REVIEW_ACTION_CONFIRM else null,
                secondaryActionLabel = if (questionNeedsReviewCount > 0) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_CONFIRM)
                } else {
                    null
                },
                secondaryActionTarget = if (questionNeedsReviewCount > 0) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_CONFIRM,
                    )
                } else {
                    null
                },
                secondaryActionTargetPayload = if (questionNeedsReviewCount > 0) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_CONFIRM,
                    )
                } else {
                    null
                },
                emptyStateMessage = if (questionSummaries.isEmpty()) {
                    "No structured interview questions found."
                } else {
                    null
                },
                emptyStateCtaAction = if (questionSummaries.isEmpty()) {
                    REVIEW_ACTION_REVIEW_TRANSCRIPT
                } else {
                    null
                },
                emptyStateCtaLabel = if (questionSummaries.isEmpty()) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT)
                } else {
                    null
                },
                emptyStateCtaTarget = if (questionSummaries.isEmpty()) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_REVIEW_TRANSCRIPT,
                    )
                } else {
                    null
                },
                emptyStateCtaTargetPayload = if (questionSummaries.isEmpty()) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_REVIEW_TRANSCRIPT,
                    )
                } else {
                    null
                },
                completionMessage = if (questionSummaries.isNotEmpty() && questionNeedsReviewCount == 0) {
                    "Question review is complete."
                } else {
                    null
                },
                completionCtaAction = if (questionSummaries.isNotEmpty() && questionNeedsReviewCount == 0) {
                    REVIEW_ACTION_CONFIRM
                } else {
                    null
                },
                completionCtaLabel = if (questionSummaries.isNotEmpty() && questionNeedsReviewCount == 0) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_CONFIRM)
                } else {
                    null
                },
                completionCtaTarget = if (questionSummaries.isNotEmpty() && questionNeedsReviewCount == 0) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_CONFIRM,
                    )
                } else {
                    null
                },
                completionCtaTargetPayload = if (questionSummaries.isNotEmpty() && questionNeedsReviewCount == 0) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_QUESTION,
                        action = REVIEW_ACTION_CONFIRM,
                    )
                } else {
                    null
                },
                blockingReasons = questionBlockingReasons.distinct(),
                blockingReasonDetails = questionBlockingReasons
                    .distinct()
                    .map { buildReviewLaneBlockerDetail(REVIEW_LANE_KEY_QUESTION, it) },
            ),
            thread = InterviewRecordReviewLaneItemDto(
                sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_THREAD),
                highlightVariant = threadHighlightVariant,
                badgeText = resolveReviewLaneBadgeText(
                    readiness = if (threadNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    needsReviewCount = threadNeedsReviewCount,
                ),
                summaryText = buildReviewLaneSummaryText(
                    laneLabel = "Threads",
                    totalCount = followUpThreads.size,
                    readyCount = (followUpThreads.size - threadNeedsReviewCount).coerceAtLeast(0),
                    needsReviewCount = threadNeedsReviewCount,
                ),
                recommendedTab = resolveReviewLaneRecommendedTab(REVIEW_LANE_KEY_THREAD),
                defaultExpanded = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_THREAD) == 1,
                analyticsKey = buildReviewLaneAnalyticsKey(REVIEW_LANE_KEY_THREAD),
                trackingContext = buildReviewLaneTrackingContext(
                    laneKey = REVIEW_LANE_KEY_THREAD,
                    sortOrder = laneSortOrderByKey.getValue(REVIEW_LANE_KEY_THREAD),
                    highlightVariant = threadHighlightVariant,
                    readiness = if (threadNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    primaryAction = threadPrimaryAction,
                ),
                helpText = buildReviewLaneHelpText(REVIEW_LANE_KEY_THREAD),
                whyItMatters = buildReviewLaneWhyItMatters(REVIEW_LANE_KEY_THREAD),
                accessibilityLabel = buildReviewLaneAccessibilityLabel(REVIEW_LANE_KEY_THREAD),
                screenReaderSummary = buildReviewLaneScreenReaderSummary(
                    laneLabel = "Threads",
                    readiness = if (threadNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                    needsReviewCount = threadNeedsReviewCount,
                    highestPriority = threadHighestPriority,
                ),
                totalCount = followUpThreads.size,
                readyCount = (followUpThreads.size - threadNeedsReviewCount).coerceAtLeast(0),
                needsReviewCount = threadNeedsReviewCount,
                readiness = if (threadNeedsReviewCount == 0) REVIEW_LANE_READY else REVIEW_LANE_NEEDS_REVIEW,
                severity = threadSeverity,
                highestPriority = threadHighestPriority,
                primaryAction = threadPrimaryAction,
                primaryActionLabel = resolveReviewLaneActionLabel(threadPrimaryAction),
                primaryActionTarget = resolveReviewLaneActionTarget(
                    laneKey = REVIEW_LANE_KEY_THREAD,
                    action = threadPrimaryAction,
                ),
                primaryActionTargetPayload = resolveReviewLaneActionTargetPayload(
                    laneKey = REVIEW_LANE_KEY_THREAD,
                    action = threadPrimaryAction,
                ),
                secondaryAction = if (threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN &&
                    followUpThreads.any { it.recommendedAction == THREAD_ACTION_REPLAY_CHAIN || it.replayLaunchPreset.seedQuestionIds.isNotEmpty() }
                ) {
                    THREAD_ACTION_REPLAY_CHAIN
                } else {
                    null
                },
                secondaryActionLabel = if (threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN &&
                    followUpThreads.any { it.recommendedAction == THREAD_ACTION_REPLAY_CHAIN || it.replayLaunchPreset.seedQuestionIds.isNotEmpty() }
                ) {
                    resolveReviewLaneActionLabel(THREAD_ACTION_REPLAY_CHAIN)
                } else {
                    null
                },
                secondaryActionTarget = if (threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN &&
                    followUpThreads.any { it.recommendedAction == THREAD_ACTION_REPLAY_CHAIN || it.replayLaunchPreset.seedQuestionIds.isNotEmpty() }
                ) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = THREAD_ACTION_REPLAY_CHAIN,
                    )
                } else {
                    null
                },
                secondaryActionTargetPayload = if (threadPrimaryAction == THREAD_ACTION_REVIEW_WEAK_CHAIN &&
                    followUpThreads.any { it.recommendedAction == THREAD_ACTION_REPLAY_CHAIN || it.replayLaunchPreset.seedQuestionIds.isNotEmpty() }
                ) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = THREAD_ACTION_REPLAY_CHAIN,
                    )
                } else {
                    null
                },
                emptyStateMessage = if (followUpThreads.isEmpty()) {
                    "No follow-up threads available yet."
                } else {
                    null
                },
                emptyStateCtaAction = if (followUpThreads.isEmpty()) {
                    REVIEW_ACTION_REVIEW_ANSWERS
                } else {
                    null
                },
                emptyStateCtaLabel = if (followUpThreads.isEmpty()) {
                    resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_ANSWERS)
                } else {
                    null
                },
                emptyStateCtaTarget = if (followUpThreads.isEmpty()) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = REVIEW_ACTION_REVIEW_ANSWERS,
                    )
                } else {
                    null
                },
                emptyStateCtaTargetPayload = if (followUpThreads.isEmpty()) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = REVIEW_ACTION_REVIEW_ANSWERS,
                    )
                } else {
                    null
                },
                completionMessage = if (followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0) {
                    "Thread review is stable."
                } else {
                    null
                },
                completionCtaAction = if (followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0) {
                    THREAD_ACTION_REPLAY_CHAIN
                } else {
                    null
                },
                completionCtaLabel = if (followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0) {
                    resolveReviewLaneActionLabel(THREAD_ACTION_REPLAY_CHAIN)
                } else {
                    null
                },
                completionCtaTarget = if (followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0) {
                    resolveReviewLaneActionTarget(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = THREAD_ACTION_REPLAY_CHAIN,
                    )
                } else {
                    null
                },
                completionCtaTargetPayload = if (followUpThreads.isNotEmpty() && threadNeedsReviewCount == 0) {
                    resolveReviewLaneActionTargetPayload(
                        laneKey = REVIEW_LANE_KEY_THREAD,
                        action = THREAD_ACTION_REPLAY_CHAIN,
                    )
                } else {
                    null
                },
                blockingReasons = threadBlockingReasons.distinct(),
                blockingReasonDetails = threadBlockingReasons
                    .distinct()
                    .map { buildReviewLaneBlockerDetail(REVIEW_LANE_KEY_THREAD, it) },
            ),
        )
    }

    private fun buildReviewLaneBlockerDetail(
        laneKey: String,
        code: String,
    ): InterviewRecordReviewLaneBlockerDetailDto = when (code) {
        REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = "Pending transcript edits",
            description = "Transcript edits still need review before this lane can be treated as stable.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveReviewLaneActionTarget(laneKey, REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(laneKey, REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
        REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = "Weak answers present",
            description = "Some imported answers are still weak and should be reviewed before confirming study quality.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_ANSWERS,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_ANSWERS),
            recommendedActionTarget = resolveReviewLaneActionTarget(laneKey, REVIEW_ACTION_REVIEW_ANSWERS),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(laneKey, REVIEW_ACTION_REVIEW_ANSWERS),
        )
        REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = "Unconfirmed structured questions",
            description = "Question or answer structuring is not fully confirmed yet.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_CONFIRM,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_CONFIRM),
            recommendedActionTarget = resolveReviewLaneActionTarget(laneKey, REVIEW_ACTION_CONFIRM),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(laneKey, REVIEW_ACTION_CONFIRM),
        )
        REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = "Weak follow-up threads",
            description = "Some follow-up chains still need review before they are ready for replay or study.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = THREAD_ACTION_REVIEW_WEAK_CHAIN,
            recommendedActionLabel = resolveReviewLaneActionLabel(THREAD_ACTION_REVIEW_WEAK_CHAIN),
            recommendedActionTarget = resolveReviewLaneActionTarget(laneKey, THREAD_ACTION_REVIEW_WEAK_CHAIN),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(laneKey, THREAD_ACTION_REVIEW_WEAK_CHAIN),
        )
        REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = "No follow-up threads",
            description = "No follow-up chains are available yet for this interview record.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = if (laneKey == REVIEW_LANE_KEY_THREAD) REVIEW_ACTION_REVIEW_ANSWERS else REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(
                if (laneKey == REVIEW_LANE_KEY_THREAD) REVIEW_ACTION_REVIEW_ANSWERS else REVIEW_ACTION_REVIEW_TRANSCRIPT,
            ),
            recommendedActionTarget = resolveReviewLaneActionTarget(
                laneKey,
                if (laneKey == REVIEW_LANE_KEY_THREAD) REVIEW_ACTION_REVIEW_ANSWERS else REVIEW_ACTION_REVIEW_TRANSCRIPT,
            ),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(
                laneKey,
                if (laneKey == REVIEW_LANE_KEY_THREAD) REVIEW_ACTION_REVIEW_ANSWERS else REVIEW_ACTION_REVIEW_TRANSCRIPT,
            ),
        )
        else -> InterviewRecordReviewLaneBlockerDetailDto(
            code = code,
            label = code,
            description = "Resolve this blocking issue before continuing review.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveReviewLaneActionTarget(laneKey, REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveReviewLaneActionTargetPayload(laneKey, REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
    }

    private fun resolveReviewBlockerSeverity(code: String): String = when (code) {
        REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS,
        REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE,
        -> SEGMENT_SEVERITY_HIGH
        REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT,
        REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT,
        REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT,
        REPLAY_BLOCKER_NO_QUESTIONS,
        -> SEGMENT_SEVERITY_MEDIUM
        REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE -> SEGMENT_SEVERITY_LOW
        else -> SEGMENT_SEVERITY_MEDIUM
    }

    private fun resolveReviewBlockerPriority(code: String): String = when (code) {
        REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS,
        REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE,
        -> SEGMENT_PRIORITY_P0
        REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT,
        REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT,
        REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT,
        REPLAY_BLOCKER_NO_QUESTIONS,
        -> SEGMENT_PRIORITY_P1
        REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE -> SEGMENT_PRIORITY_P2
        else -> SEGMENT_PRIORITY_P1
    }

    private fun resolveReviewBlockerHighlightVariant(code: String): String = when (resolveReviewBlockerSeverity(code)) {
        SEGMENT_SEVERITY_HIGH -> REVIEW_LANE_HIGHLIGHT_DANGER
        SEGMENT_SEVERITY_MEDIUM -> REVIEW_LANE_HIGHLIGHT_WARNING
        SEGMENT_SEVERITY_LOW -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
        else -> REVIEW_LANE_HIGHLIGHT_NEUTRAL
    }

    private fun resolveReviewBlockerSortOrder(code: String): Int = when (code) {
        REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS,
        REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE,
        -> 0
        REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT,
        REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT,
        -> 1
        REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT,
        REPLAY_BLOCKER_NO_QUESTIONS,
        -> 2
        REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE -> 3
        else -> 9
    }

    private fun resolveReviewLaneActionLabel(action: String): String = when (action) {
        REVIEW_ACTION_REVIEW_TRANSCRIPT -> "Review transcript"
        REVIEW_ACTION_REVIEW_ANSWERS -> "Review answers"
        REVIEW_ACTION_CONFIRM -> "Confirm review"
        REVIEW_ACTION_START_REPLAY -> "Start replay"
        THREAD_ACTION_REVIEW_WEAK_CHAIN -> "Review weak chain"
        THREAD_ACTION_REPLAY_CHAIN -> "Replay this chain"
        THREAD_ACTION_STABLE_CHAIN -> "Stable chain"
        else -> action
    }

    private fun resolveReviewLaneActionTarget(
        laneKey: String,
        action: String,
    ): String = when (action) {
        REVIEW_ACTION_REVIEW_TRANSCRIPT -> "$laneKey:issues"
        REVIEW_ACTION_REVIEW_ANSWERS -> "$laneKey:questions"
        REVIEW_ACTION_CONFIRM -> "review:confirm"
        REVIEW_ACTION_START_REPLAY -> "review:replay"
        THREAD_ACTION_REVIEW_WEAK_CHAIN -> "$laneKey:weak_threads"
        THREAD_ACTION_REPLAY_CHAIN -> "$laneKey:replay"
        THREAD_ACTION_STABLE_CHAIN -> "$laneKey:overview"
        else -> "$laneKey:overview"
    }

    private fun resolveGlobalActionTarget(action: String): String = when (action) {
        REVIEW_ACTION_REVIEW_TRANSCRIPT -> resolveReviewLaneActionTarget(REVIEW_LANE_KEY_TRANSCRIPT, action)
        REVIEW_ACTION_REVIEW_ANSWERS -> resolveReviewLaneActionTarget(REVIEW_LANE_KEY_QUESTION, action)
        REVIEW_ACTION_CONFIRM -> "review:confirm"
        REVIEW_ACTION_START_REPLAY -> "review:replay"
        THREAD_ACTION_REVIEW_WEAK_CHAIN,
        THREAD_ACTION_REPLAY_CHAIN,
        THREAD_ACTION_STABLE_CHAIN -> resolveReviewLaneActionTarget(REVIEW_LANE_KEY_THREAD, action)
        else -> "review:overview"
    }

    private fun resolveReviewLaneActionTargetPayload(
        laneKey: String,
        action: String,
    ): Map<String, String> = when (action) {
        REVIEW_ACTION_REVIEW_TRANSCRIPT -> mapOf(
            "tab" to REVIEW_LANE_TAB_ISSUES,
            "filter" to "open_issues",
            "focus" to "top_priority_issue",
        )
        REVIEW_ACTION_REVIEW_ANSWERS -> mapOf(
            "tab" to REVIEW_LANE_TAB_QUESTIONS,
            "filter" to "needs_review",
            "focus" to if (laneKey == REVIEW_LANE_KEY_THREAD) "thread_answer_gaps" else "weak_answers",
        )
        REVIEW_ACTION_CONFIRM -> mapOf(
            "tab" to REVIEW_LANE_TAB_OVERVIEW,
            "panel" to "confirm_review",
            "focus" to "confirmation_cta",
        )
        REVIEW_ACTION_START_REPLAY -> mapOf(
            "tab" to REVIEW_LANE_TAB_OVERVIEW,
            "panel" to "replay_launch",
            "focus" to "recommended_replay_mode",
        )
        THREAD_ACTION_REVIEW_WEAK_CHAIN -> mapOf(
            "tab" to REVIEW_LANE_TAB_THREADS,
            "filter" to "weak",
            "focus" to "first_weak_thread",
        )
        THREAD_ACTION_REPLAY_CHAIN -> mapOf(
            "tab" to REVIEW_LANE_TAB_THREADS,
            "filter" to "replay_ready",
            "focus" to "first_replayable_thread",
        )
        THREAD_ACTION_STABLE_CHAIN -> mapOf(
            "tab" to REVIEW_LANE_TAB_THREADS,
            "filter" to "stable",
            "focus" to "thread_overview",
        )
        else -> mapOf("tab" to REVIEW_LANE_TAB_OVERVIEW)
    }

    private fun resolveGlobalActionTargetPayload(action: String): Map<String, String> = when (action) {
        REVIEW_ACTION_REVIEW_TRANSCRIPT -> resolveReviewLaneActionTargetPayload(REVIEW_LANE_KEY_TRANSCRIPT, action)
        REVIEW_ACTION_REVIEW_ANSWERS -> resolveReviewLaneActionTargetPayload(REVIEW_LANE_KEY_QUESTION, action)
        REVIEW_ACTION_CONFIRM -> resolveReviewLaneActionTargetPayload(REVIEW_LANE_KEY_QUESTION, action)
        REVIEW_ACTION_START_REPLAY -> resolveReviewLaneActionTargetPayload(REVIEW_LANE_KEY_TRANSCRIPT, action)
        THREAD_ACTION_REVIEW_WEAK_CHAIN,
        THREAD_ACTION_REPLAY_CHAIN,
        THREAD_ACTION_STABLE_CHAIN -> resolveReviewLaneActionTargetPayload(REVIEW_LANE_KEY_THREAD, action)
        else -> mapOf("tab" to REVIEW_LANE_TAB_OVERVIEW)
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
            recommendedReplayModeLabel = resolveReplayModeLabel(REVIEW_REPLAY_MODE_ORIGINAL),
            statusBadgeText = if (blockers.isEmpty()) "Replay ready" else "Replay blocked",
            statusVariant = if (blockers.isEmpty()) REVIEW_LANE_HIGHLIGHT_SUCCESS else REVIEW_LANE_HIGHLIGHT_WARNING,
            statusSummary = if (blockers.isEmpty()) {
                "Replay can start with the recommended interviewer pattern."
            } else {
                "Resolve replay blockers before starting a replay session."
            },
            primaryCtaLabel = if (blockers.isEmpty()) "Start replay" else "Review blockers",
            blockedCtaLabel = "Resolve replay blockers",
            blockers = blockers,
            blockerDetails = blockers.map(::buildReplayBlockerDetail),
        )
    }

    private fun buildReplayBlockerDetail(code: String): InterviewRecordReplayBlockerDetailDto = when (code) {
        REPLAY_BLOCKER_NO_QUESTIONS -> InterviewRecordReplayBlockerDetailDto(
            code = code,
            label = "No replayable questions",
            description = "This interview record does not have imported questions that can seed a replay session yet.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
        REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE -> InterviewRecordReplayBlockerDetailDto(
            code = code,
            label = "Missing interviewer profile",
            description = "Replay needs interviewer tone and probing metadata before it can generate realistic follow-ups.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_CONFIRM,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_CONFIRM),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_CONFIRM),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_CONFIRM),
        )
        else -> InterviewRecordReplayBlockerDetailDto(
            code = code,
            label = code,
            description = "Resolve this replay blocker before starting replay.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
    }

    private fun buildTranscriptIssueSummary(
        recordId: Long,
        segments: List<InterviewTranscriptSegmentEntity>,
        questions: List<InterviewRecordQuestionEntity>,
        answers: List<InterviewRecordAnswerEntity>,
        segmentSequenceById: Map<Long, Int>,
        questionById: Map<Long, InterviewRecordQuestionEntity>,
    ): InterviewRecordTranscriptIssueSummaryDto {
        val lowConfidenceSegments = segments.filter { (it.confidenceScore ?: BigDecimal.ONE) < LOW_CONFIDENCE_THRESHOLD }
        val speakerOverrideSegments = segments.filter(::hasSpeakerOverride)
        val editedSegments = segments.filter(::isEditedSegment)
        val questionIdBySequence = mutableMapOf<Int, Long>()
        questions.forEach { question ->
            question.segmentStartId?.let(segmentSequenceById::get)?.let { questionIdBySequence[it] = question.id }
            question.segmentEndId?.let(segmentSequenceById::get)?.let { questionIdBySequence[it] = question.id }
        }
        answers.forEach { answer ->
            answer.segmentStartId?.let(segmentSequenceById::get)?.let { questionIdBySequence[it] = answer.interviewRecordQuestionId }
            answer.segmentEndId?.let(segmentSequenceById::get)?.let { questionIdBySequence[it] = answer.interviewRecordQuestionId }
        }
        val issueTypesBySequence = linkedMapOf<Int, MutableSet<String>>()
        lowConfidenceSegments.forEach { issueTypesBySequence.computeIfAbsent(it.sequence) { linkedSetOf() }.add(SEGMENT_ISSUE_LOW_CONFIDENCE) }
        speakerOverrideSegments.forEach { issueTypesBySequence.computeIfAbsent(it.sequence) { linkedSetOf() }.add(SEGMENT_ISSUE_SPEAKER_OVERRIDE) }
        editedSegments.forEach { issueTypesBySequence.computeIfAbsent(it.sequence) { linkedSetOf() }.add(SEGMENT_ISSUE_CONFIRMED_OVERRIDE) }
        val questionsByRootQuestionId = questions.groupBy { resolveQuestionThreadRootId(it, questionById) }
        val segmentActions = issueTypesBySequence.entries.map { (sequence, issueTypes) ->
            val linkedQuestionId = questionIdBySequence[sequence]
            val linkedQuestion = linkedQuestionId?.let(questionById::get)
            val threadRootQuestionId = linkedQuestion?.let { resolveQuestionThreadRootId(it, questionById) }
            InterviewRecordTranscriptSegmentActionDto(
                sequence = sequence,
                issueTypes = issueTypes.toList(),
                recommendedAction = when {
                    SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes -> SEGMENT_ACTION_REVIEW_NOW
                    SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes -> SEGMENT_ACTION_JUMP_TO_QUESTION
                    else -> SEGMENT_ACTION_JUMP_TO_THREAD
                },
                triageReason = resolveSegmentTriageReason(issueTypes),
                ctaLabel = resolveSegmentCtaLabel(issueTypes),
                severity = resolveSegmentIssueSeverity(issueTypes),
                priority = resolveSegmentIssuePriority(issueTypes),
                reviewerLane = resolveSegmentReviewerLane(issueTypes, linkedQuestionId, threadRootQuestionId),
                linkedQuestionId = linkedQuestionId,
                threadRootQuestionId = threadRootQuestionId,
                deepLink = linkedQuestion?.toReviewQuestionDeepLink(recordId),
                replayLaunchPreset = threadRootQuestionId?.let { rootQuestionId ->
                    buildThreadReplayLaunchPreset(
                        recordId = recordId,
                        threadQuestions = questionsByRootQuestionId[rootQuestionId].orEmpty(),
                    )
                },
            )
        }
        return InterviewRecordTranscriptIssueSummaryDto(
            lowConfidenceSegmentCount = lowConfidenceSegments.size,
            lowConfidenceSegmentSequences = lowConfidenceSegments.map { it.sequence },
            speakerOverrideSegmentCount = speakerOverrideSegments.size,
            speakerOverrideSegmentSequences = speakerOverrideSegments.map { it.sequence },
            confirmedTextOverrideCount = editedSegments.size,
            editedSegmentSequences = editedSegments.map { it.sequence },
            resolvedIssueCount = segmentActions.size - editedSegments.size,
            unresolvedIssueCount = editedSegments.size,
            confirmationReadiness = if (editedSegments.isEmpty()) {
                TRANSCRIPT_CONFIRMATION_READY
            } else {
                TRANSCRIPT_CONFIRMATION_NEEDS_REVIEW
            },
            reviewerLaneCounts = segmentActions.groupingBy { it.reviewerLane }.eachCount().toSortedMap(),
            topPrioritySegmentActions = segmentActions
                .sortedWith(compareBy<InterviewRecordTranscriptSegmentActionDto>({ segmentPriorityRank(it.priority) }, { it.sequence }))
                .take(MAX_TOP_PRIORITY_SEGMENT_ACTIONS),
            segmentActions = segmentActions,
        )
    }

    private fun segmentPriorityRank(priority: String): Int = when (priority) {
        SEGMENT_PRIORITY_P0 -> 0
        SEGMENT_PRIORITY_P1 -> 1
        else -> 2
    }

    private fun segmentSeverityRank(severity: String): Int = when (severity) {
        SEGMENT_SEVERITY_HIGH -> 0
        SEGMENT_SEVERITY_MEDIUM -> 1
        else -> 2
    }

    private fun reviewLaneTieBreakRank(laneKey: String): Int = when (laneKey) {
        REVIEW_LANE_KEY_TRANSCRIPT -> 0
        REVIEW_LANE_KEY_QUESTION -> 1
        else -> 2
    }

    private fun resolveReviewLaneBadgeText(
        readiness: String,
        needsReviewCount: Int,
    ): String = when {
        needsReviewCount > 0 -> "Needs review"
        readiness == REVIEW_LANE_READY -> "Ready"
        else -> "In progress"
    }

    private fun buildReviewLaneSummaryText(
        laneLabel: String,
        totalCount: Int,
        readyCount: Int,
        needsReviewCount: Int,
    ): String = when {
        totalCount == 0 -> "$laneLabel not available yet."
        needsReviewCount > 0 -> "$needsReviewCount of $totalCount require review."
        else -> "$readyCount of $totalCount are ready."
    }

    private fun resolveReviewLaneRecommendedTab(laneKey: String): String = when (laneKey) {
        REVIEW_LANE_KEY_TRANSCRIPT -> REVIEW_LANE_TAB_ISSUES
        REVIEW_LANE_KEY_QUESTION -> REVIEW_LANE_TAB_QUESTIONS
        REVIEW_LANE_KEY_THREAD -> REVIEW_LANE_TAB_THREADS
        else -> REVIEW_LANE_TAB_OVERVIEW
    }

    private fun buildReviewLaneAnalyticsKey(laneKey: String): String = "practical_review_lane_$laneKey"

    private fun buildReviewLaneTrackingContext(
        laneKey: String,
        sortOrder: Int,
        highlightVariant: String,
        readiness: String,
        primaryAction: String,
    ): Map<String, String> = mapOf(
        "laneKey" to laneKey,
        "sortOrder" to sortOrder.toString(),
        "highlightVariant" to highlightVariant,
        "readiness" to readiness,
        "primaryAction" to primaryAction,
    )

    private fun buildReviewLaneHelpText(laneKey: String): String = when (laneKey) {
        REVIEW_LANE_KEY_TRANSCRIPT -> "Check transcript accuracy, speaker attribution, and confirmed edits."
        REVIEW_LANE_KEY_QUESTION -> "Review imported questions and answer quality before confirming the record."
        REVIEW_LANE_KEY_THREAD -> "Inspect follow-up chains to find weak probing paths and replay targets."
        else -> "Review this lane before moving on."
    }

    private fun buildReviewLaneWhyItMatters(laneKey: String): String = when (laneKey) {
        REVIEW_LANE_KEY_TRANSCRIPT -> "Transcript issues can distort downstream question, answer, and replay analysis."
        REVIEW_LANE_KEY_QUESTION -> "Question and answer review determines what becomes a reliable study asset."
        REVIEW_LANE_KEY_THREAD -> "Thread review shows whether the practical interview had strong probing depth."
        else -> "This lane affects practical interview review quality."
    }

    private fun buildReviewLaneAccessibilityLabel(laneKey: String): String = when (laneKey) {
        REVIEW_LANE_KEY_TRANSCRIPT -> "Transcript review lane"
        REVIEW_LANE_KEY_QUESTION -> "Question review lane"
        REVIEW_LANE_KEY_THREAD -> "Thread review lane"
        else -> "Review lane"
    }

    private fun buildReviewLaneScreenReaderSummary(
        laneLabel: String,
        readiness: String,
        needsReviewCount: Int,
        highestPriority: String,
    ): String = when {
        needsReviewCount > 0 -> "$laneLabel lane needs review for $needsReviewCount items, priority $highestPriority."
        readiness == REVIEW_LANE_READY -> "$laneLabel lane is ready."
        else -> "$laneLabel lane is in progress."
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

    private fun buildTimelineNavigation(
        questions: List<InterviewRecordQuestionEntity>,
        answerByQuestionId: Map<Long, InterviewRecordAnswerEntity>,
        segmentSequenceById: Map<Long, Int>,
    ): InterviewRecordTimelineNavigationDto {
        val questionById = questions.associateBy { it.id }
        return InterviewRecordTimelineNavigationDto(
            items = questions.map { question ->
                val answer = answerByQuestionId[question.id]
                InterviewRecordTimelineNavigationItemDto(
                    questionId = question.id,
                    orderIndex = question.orderIndex,
                    parentQuestionId = question.parentQuestionId,
                    threadRootQuestionId = resolveQuestionThreadRootId(question, questionById),
                    questionSegmentStartSequence = question.segmentStartId?.let(segmentSequenceById::get),
                    questionSegmentEndSequence = question.segmentEndId?.let(segmentSequenceById::get),
                    answerSegmentStartSequence = answer?.segmentStartId?.let(segmentSequenceById::get),
                    answerSegmentEndSequence = answer?.segmentEndId?.let(segmentSequenceById::get),
                )
            },
        )
    }

    private fun buildActionRecommendations(
        recordStructuringStage: String,
        editedSegmentCount: Int,
        weakAnswerCount: Int,
        replayReadiness: InterviewRecordReplayReadinessDto,
    ): InterviewRecordReviewActionRecommendationsDto {
        val blockingReasons = buildList {
            if (editedSegmentCount > 0) {
                add(REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS)
            }
            if (!replayReadiness.ready) {
                addAll(replayReadiness.blockers)
            }
        }
        val availableActions = buildList {
            if (editedSegmentCount > 0) {
                add(REVIEW_ACTION_REVIEW_TRANSCRIPT)
            }
            if (weakAnswerCount > 0) {
                add(REVIEW_ACTION_REVIEW_ANSWERS)
            }
            if (recordStructuringStage != STRUCTURING_STAGE_CONFIRMED && editedSegmentCount == 0) {
                add(REVIEW_ACTION_CONFIRM)
            }
            if (replayReadiness.ready) {
                add(REVIEW_ACTION_START_REPLAY)
            }
        }.ifEmpty {
            listOf(REVIEW_ACTION_REVIEW_ANSWERS)
        }
        val primaryAction = when {
            editedSegmentCount > 0 -> REVIEW_ACTION_REVIEW_TRANSCRIPT
            recordStructuringStage != STRUCTURING_STAGE_CONFIRMED -> REVIEW_ACTION_CONFIRM
            replayReadiness.ready -> REVIEW_ACTION_START_REPLAY
            weakAnswerCount > 0 -> REVIEW_ACTION_REVIEW_ANSWERS
            else -> availableActions.first()
        }
        val distinctAvailableActions = availableActions.distinct()
        return InterviewRecordReviewActionRecommendationsDto(
            primaryAction = primaryAction,
            primaryActionLabel = resolveReviewLaneActionLabel(primaryAction),
            primaryActionTarget = resolveGlobalActionTarget(primaryAction),
            primaryActionTargetPayload = resolveGlobalActionTargetPayload(primaryAction),
            availableActions = distinctAvailableActions,
            availableActionLabels = distinctAvailableActions.associateWith(::resolveReviewLaneActionLabel),
            availableActionTargets = distinctAvailableActions.associateWith(::resolveGlobalActionTarget),
            availableActionTargetPayloads = distinctAvailableActions.associateWith(::resolveGlobalActionTargetPayload),
            blockingReasons = blockingReasons.distinct(),
            blockingReasonDetails = blockingReasons.distinct().map(::buildReviewActionBlockerDetail),
            canConfirm = editedSegmentCount == 0 && recordStructuringStage != STRUCTURING_STAGE_CONFIRMED,
            canReplay = replayReadiness.ready,
        )
    }

    private fun buildReviewActionBlockerDetail(code: String): InterviewRecordReviewActionBlockerDetailDto = when (code) {
        REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS -> InterviewRecordReviewActionBlockerDetailDto(
            code = code,
            label = "Pending transcript edits",
            description = "Transcript edits must be reviewed before confirmation or replay can safely proceed.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
        REPLAY_BLOCKER_NO_QUESTIONS -> InterviewRecordReviewActionBlockerDetailDto(
            code = code,
            label = "No replayable questions",
            description = "This record needs imported or confirmed questions before replay can start.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
        REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE -> InterviewRecordReviewActionBlockerDetailDto(
            code = code,
            label = "Missing interviewer profile",
            description = "Confirm or enrich interviewer metadata before replay so follow-up behavior stays realistic.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_CONFIRM,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_CONFIRM),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_CONFIRM),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_CONFIRM),
        )
        else -> InterviewRecordReviewActionBlockerDetailDto(
            code = code,
            label = code,
            description = "Resolve this review blocker before continuing.",
            severity = resolveReviewBlockerSeverity(code),
            priority = resolveReviewBlockerPriority(code),
            highlightVariant = resolveReviewBlockerHighlightVariant(code),
            sortOrder = resolveReviewBlockerSortOrder(code),
            recommendedAction = REVIEW_ACTION_REVIEW_TRANSCRIPT,
            recommendedActionLabel = resolveReviewLaneActionLabel(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTarget = resolveGlobalActionTarget(REVIEW_ACTION_REVIEW_TRANSCRIPT),
            recommendedActionTargetPayload = resolveGlobalActionTargetPayload(REVIEW_ACTION_REVIEW_TRANSCRIPT),
        )
    }

    private fun buildReplayLaunchPreset(
        recordId: Long,
        questionSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
        replayReadiness: InterviewRecordReplayReadinessDto,
    ): InterviewRecordReplayLaunchPresetDto {
        val rootSeedQuestionIds = questionSummaries
            .filter { !it.isFollowUp }
            .mapNotNull { it.linkedQuestionId }
            .take(DEFAULT_REPLAY_SEED_COUNT)
        val recommendedQuestionCount = when {
            replayReadiness.replayableQuestionCount <= 3 -> replayReadiness.replayableQuestionCount.coerceAtLeast(1)
            else -> DEFAULT_REPLAY_QUESTION_COUNT
        }
        return InterviewRecordReplayLaunchPresetDto(
            sessionType = REVIEW_REPLAY_SESSION_TYPE,
            sourceInterviewRecordId = recordId,
            replayMode = replayReadiness.recommendedReplayMode,
            recommendedReplayModeLabel = resolveReplayModeLabel(replayReadiness.recommendedReplayMode),
            recommendedQuestionCount = recommendedQuestionCount,
            seedQuestionIds = rootSeedQuestionIds,
            availableReplayModes = listOf(
                REVIEW_REPLAY_MODE_ORIGINAL,
                REVIEW_REPLAY_MODE_PATTERN_SIMILAR,
                REVIEW_REPLAY_MODE_PRESSURE_VARIANT,
            ),
            availableReplayModeLabels = buildReplayModeLabels(),
            presetTitle = "Replay this interview pattern",
            presetDescription = "Start from the imported interview flow with the recommended mode and seed questions already selected.",
            launchButtonLabel = "Start recommended replay",
        )
    }

    private fun resolveSegmentIssueSeverity(issueTypes: Set<String>): String = when {
        SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes -> SEGMENT_SEVERITY_HIGH
        SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes -> SEGMENT_SEVERITY_MEDIUM
        else -> SEGMENT_SEVERITY_LOW
    }

    private fun resolveSegmentIssuePriority(issueTypes: Set<String>): String = when {
        SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes -> SEGMENT_PRIORITY_P0
        SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes -> SEGMENT_PRIORITY_P1
        else -> SEGMENT_PRIORITY_P2
    }

    private fun resolveSegmentReviewerLane(
        issueTypes: Set<String>,
        linkedQuestionId: Long?,
        threadRootQuestionId: Long?,
    ): String = when {
        SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes -> SEGMENT_REVIEWER_LANE_TRANSCRIPT
        SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes && linkedQuestionId != null -> SEGMENT_REVIEWER_LANE_QUESTION
        threadRootQuestionId != null -> SEGMENT_REVIEWER_LANE_THREAD
        else -> SEGMENT_REVIEWER_LANE_TRANSCRIPT
    }

    private fun resolveSegmentTriageReason(issueTypes: Set<String>): String = when {
        SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes ->
            "Confirmed transcript text differs from the cleaned baseline and should be reviewed first."
        SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes ->
            "Speaker attribution looks inconsistent and may affect question or answer structuring."
        else ->
            "This segment has low transcript confidence and should be verified before confirmation."
    }

    private fun resolveSegmentCtaLabel(issueTypes: Set<String>): String = when {
        SEGMENT_ISSUE_CONFIRMED_OVERRIDE in issueTypes -> "Review transcript edit"
        SEGMENT_ISSUE_SPEAKER_OVERRIDE in issueTypes -> "Open linked question"
        else -> "Inspect follow-up thread"
    }

    private fun buildProvenanceComparisonSummary(
        record: InterviewRecordEntity,
        questions: List<InterviewRecordQuestionEntity>,
        answers: List<InterviewRecordAnswerEntity>,
        interviewerProfileSource: String?,
    ): InterviewRecordProvenanceComparisonSummaryDto {
        val aiRefinementApplied = record.aiEnrichedSummary != null ||
            questions.any { it.structuringSource == STRUCTURING_STAGE_AI_ENRICHED } ||
            answers.any { it.structuringSource == STRUCTURING_STAGE_AI_ENRICHED } ||
            interviewerProfileSource == STRUCTURING_STAGE_AI_ENRICHED
        return InterviewRecordProvenanceComparisonSummaryDto(
            aiRefinementApplied = aiRefinementApplied,
            confirmedVersionAvailable = record.confirmedAt != null || record.structuringStage == STRUCTURING_STAGE_CONFIRMED,
            summaryChangedFromDeterministic = hasSummaryChangedFromDeterministic(record),
            changedQuestionCountFromDeterministic = questions.count { it.structuringSource != STRUCTURING_STAGE_DETERMINISTIC },
            changedAnswerCountFromDeterministic = answers.count { it.structuringSource != STRUCTURING_STAGE_DETERMINISTIC },
            currentQuestionSource = resolveCurrentStructuringSource(questions.map { it.structuringSource }),
            currentAnswerSource = resolveCurrentStructuringSource(answers.map { it.structuringSource }),
            currentInterviewerProfileSource = interviewerProfileSource,
        )
    }

    private fun hasSummaryChangedFromDeterministic(record: InterviewRecordEntity): Boolean {
        val deterministicSummary = record.deterministicSummary?.trim().orEmpty()
        val currentSummary = (record.aiEnrichedSummary ?: record.overallSummary)?.trim().orEmpty()
        return deterministicSummary.isNotBlank() && currentSummary.isNotBlank() && deterministicSummary != currentSummary
    }

    private fun resolveCurrentStructuringSource(sources: List<String>): String = when {
        sources.any { it == STRUCTURING_STAGE_CONFIRMED } -> STRUCTURING_STAGE_CONFIRMED
        sources.any { it == STRUCTURING_STAGE_AI_ENRICHED } -> STRUCTURING_STAGE_AI_ENRICHED
        else -> STRUCTURING_STAGE_DETERMINISTIC
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

    private fun resolveQuestionThreadRootId(
        question: InterviewRecordQuestionEntity,
        questionById: Map<Long, InterviewRecordQuestionEntity>,
    ): Long {
        var current = question
        val visited = linkedSetOf(current.id)
        while (current.parentQuestionId != null) {
            val parent = questionById[current.parentQuestionId] ?: break
            if (!visited.add(parent.id)) {
                break
            }
            current = parent
        }
        return current.id
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
                answeredQuestionCount = sortedThreadSummaries.count { it.answerSummary != null },
                quantifiedQuestionCount = sortedThreadSummaries.count {
                    "quantified" in it.strengthTags
                },
                structuredQuestionCount = sortedThreadSummaries.count {
                    "structured" in it.strengthTags
                },
                tradeoffAwareQuestionCount = sortedThreadSummaries.count {
                    "tradeoff_aware" in it.strengthTags
                },
                uncertainQuestionCount = sortedThreadSummaries.count {
                    "uncertain" in it.confidenceMarkers
                },
                recommendedAction = resolveThreadRecommendedAction(sortedThreadSummaries),
                replayLaunchPreset = buildThreadReplayLaunchPresetFromSummaries(
                    recordId = rootSummary.deepLink.sourceInterviewRecordId,
                    threadSummaries = sortedThreadSummaries,
                ),
                structuringSources = sortedThreadSummaries
                    .flatMap { listOfNotNull(it.questionStructuringSource, it.answerStructuringSource) }
                    .distinct()
                    .sorted(),
            )
        }
    }

    private fun buildThreadReplayLaunchPresetFromSummaries(
        recordId: Long,
        threadSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): InterviewRecordReplayLaunchPresetDto {
        val linkedQuestionIds = threadSummaries.mapNotNull { it.linkedQuestionId }.distinct()
        return InterviewRecordReplayLaunchPresetDto(
            sessionType = REVIEW_REPLAY_SESSION_TYPE,
            sourceInterviewRecordId = recordId,
            replayMode = REVIEW_REPLAY_MODE_ORIGINAL,
            recommendedReplayModeLabel = resolveReplayModeLabel(REVIEW_REPLAY_MODE_ORIGINAL),
            recommendedQuestionCount = threadSummaries.size.coerceAtLeast(1),
            seedQuestionIds = linkedQuestionIds.take(DEFAULT_REPLAY_SEED_COUNT),
            availableReplayModes = listOf(
                REVIEW_REPLAY_MODE_ORIGINAL,
                REVIEW_REPLAY_MODE_PATTERN_SIMILAR,
                REVIEW_REPLAY_MODE_PRESSURE_VARIANT,
            ),
            availableReplayModeLabels = buildReplayModeLabels(),
            presetTitle = "Replay this follow-up chain",
            presetDescription = "Re-run this thread with its linked questions as the seed set so you can practice the same probing path.",
            launchButtonLabel = "Replay this chain",
        )
    }

    private fun buildThreadReplayLaunchPreset(
        recordId: Long,
        threadQuestions: List<InterviewRecordQuestionEntity>,
    ): InterviewRecordReplayLaunchPresetDto {
        val linkedQuestionIds = threadQuestions.mapNotNull { it.linkedQuestionId }.distinct()
        return InterviewRecordReplayLaunchPresetDto(
            sessionType = REVIEW_REPLAY_SESSION_TYPE,
            sourceInterviewRecordId = recordId,
            replayMode = REVIEW_REPLAY_MODE_ORIGINAL,
            recommendedReplayModeLabel = resolveReplayModeLabel(REVIEW_REPLAY_MODE_ORIGINAL),
            recommendedQuestionCount = threadQuestions.size.coerceAtLeast(1),
            seedQuestionIds = linkedQuestionIds.take(DEFAULT_REPLAY_SEED_COUNT),
            availableReplayModes = listOf(
                REVIEW_REPLAY_MODE_ORIGINAL,
                REVIEW_REPLAY_MODE_PATTERN_SIMILAR,
                REVIEW_REPLAY_MODE_PRESSURE_VARIANT,
            ),
            availableReplayModeLabels = buildReplayModeLabels(),
            presetTitle = "Replay from this transcript issue",
            presetDescription = "Launch replay from the linked question chain so you can verify the flagged segment in context.",
            launchButtonLabel = "Open replay from here",
        )
    }

    private fun resolveReplayModeLabel(replayMode: String): String = when (replayMode) {
        REVIEW_REPLAY_MODE_ORIGINAL -> "Original replay"
        REVIEW_REPLAY_MODE_PATTERN_SIMILAR -> "Pattern-similar replay"
        REVIEW_REPLAY_MODE_PRESSURE_VARIANT -> "Pressure-variant replay"
        else -> replayMode
    }

    private fun buildReplayModeLabels(): Map<String, String> = listOf(
        REVIEW_REPLAY_MODE_ORIGINAL,
        REVIEW_REPLAY_MODE_PATTERN_SIMILAR,
        REVIEW_REPLAY_MODE_PRESSURE_VARIANT,
    ).associateWith(::resolveReplayModeLabel)

    private fun InterviewRecordQuestionEntity.toReviewQuestionDeepLink(recordId: Long): InterviewRecordReviewQuestionDeepLinkDto =
        InterviewRecordReviewQuestionDeepLinkDto(
            questionDetailQuestionId = linkedQuestionId,
            archiveSourceType = "real_interview",
            sourceInterviewRecordId = recordId,
            sourceInterviewQuestionId = id,
            canStartReplayMock = true,
            replaySessionType = REVIEW_REPLAY_SESSION_TYPE,
        )

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

    private fun resolveThreadRecommendedAction(
        threadSummaries: List<InterviewRecordReviewQuestionSummaryDto>,
    ): String {
        val hasWeak = threadSummaries.any { it.hasWeakAnswer }
        val quantifiedCount = threadSummaries.count { "quantified" in it.strengthTags }
        val answeredCount = threadSummaries.count { it.answerSummary != null }
        return when {
            hasWeak -> THREAD_ACTION_REVIEW_WEAK_CHAIN
            answeredCount > 0 && quantifiedCount > 0 -> THREAD_ACTION_REPLAY_CHAIN
            else -> THREAD_ACTION_STABLE_CHAIN
        }
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
                transcriptErrorCode = null,
                transcriptErrorMessage = null,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = null,
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

    @Transactional
    fun getInterviewerProfile(userId: Long, recordId: Long): InterviewerProfileDto {
        val record = requireOwnedRecord(userId, recordId)
        val profile = interviewerProfileRepository.findBySourceInterviewRecordId(recordId)
            ?: ensureInterviewerProfile(record)
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
                transcriptErrorCode = record.transcriptErrorCode,
                transcriptErrorMessage = record.transcriptErrorMessage,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = record.transcriptProcessingStartedAt,
                transcriptNextRetryAt = record.transcriptNextRetryAt,
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
        if (file.size > interviewAudioUploadMaxSizeBytes) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Interview audio file must be 50 MB or smaller")
        }
        val fileName = file.originalFilename?.lowercase().orEmpty()
        val supported = fileName.endsWith(".mp3") || fileName.endsWith(".m4a") || fileName.endsWith(".wav") || fileName.endsWith(".mp4")
        if (!supported) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported interview uploads are mp3, m4a, wav, or mp4")
        }
    }

    private fun buildInterviewAudioFileUrl(storageKey: String): String = "/uploads/interview-audio/$storageKey"

    private fun publishTranscriptRequested(recordId: Long) {
        eventPublisher.publishEvent(PracticalInterviewTranscriptRequestedEvent(recordId))
    }

    private fun resolveStoredAudioPath(record: InterviewRecordEntity): Path {
        val storageKey = record.sourceAudioFileUrl
            ?.removePrefix("/uploads/interview-audio/")
            ?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Interview audio file is not available for transcription: ${record.id}",
            )
        return interviewAudioStorageService.resolveStoredPath(storageKey)
    }

    private fun isProcessingTimedOut(record: InterviewRecordEntity, now: Instant): Boolean {
        val processingStartedAt = record.transcriptProcessingStartedAt ?: return false
        return processingStartedAt <= now.minusSeconds(transcriptProcessingTimeoutSeconds)
    }

    private fun markTranscriptionFailure(
        record: InterviewRecordEntity,
        errorCode: String,
        errorMessage: String,
        now: Instant,
        incrementRetryCount: Boolean = true,
    ): InterviewRecordEntity {
        val nextRetryCount = if (incrementRetryCount) record.transcriptRetryCount + 1 else record.transcriptRetryCount
        val nextRetryAt = if (practicalInterviewTranscriptExtractionService.isConfigured() && nextRetryCount <= transcriptMaxAutoRetries) {
            now.plusSeconds(calculateRetryDelaySeconds(nextRetryCount))
        } else {
            null
        }
        return interviewRecordRepository.save(
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
                transcriptStatus = TRANSCRIPT_STATUS_FAILED,
                transcriptErrorCode = errorCode,
                transcriptErrorMessage = errorMessage,
                transcriptRetryCount = nextRetryCount,
                transcriptLastAttemptAt = now,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = nextRetryAt,
                analysisStatus = ANALYSIS_STATUS_FAILED,
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
    }

    private fun calculateRetryDelaySeconds(retryCount: Int): Long {
        val exponentialFactor = when {
            retryCount <= 1 -> 1L
            retryCount >= 8 -> 128L
            else -> 1L shl (retryCount - 1)
        }
        return transcriptRetryBaseDelaySeconds * exponentialFactor
    }

    private fun rebuildStructuredData(
        record: InterviewRecordEntity,
        transcriptText: String,
        now: Instant,
        isUserConfirmed: Boolean = false,
    ) {
        logger.debug(
            "Rebuilding structured data recordId={}, isUserConfirmed={}, transcriptLength={}",
            record.id,
            isUserConfirmed,
            transcriptText.length,
        )
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
        logger.debug(
            "Cleared previous structured data recordId={}, deletedSegments={}, deletedQuestions={}, deletedAnswers={}",
            record.id,
            existingSegments.size,
            existingQuestions.size,
            existingAnswers.size,
        )

        val deterministicParsed = parseTranscript(transcriptText, now)
        logger.debug(
            "Deterministic parsing finished recordId={}, segmentCount={}, questionCount={}",
            record.id,
            deterministicParsed.segments.size,
            deterministicParsed.questions.size,
        )
        val parsed = try {
            practicalInterviewStructuringEnrichmentService.enrich(
                record = record,
                transcriptText = transcriptText,
                parsedTranscript = deterministicParsed,
            )
        } catch (ex: Exception) {
            logger.error("Structuring enrichment failed recordId={}", record.id, ex)
            throw ex
        }
        logger.debug(
            "Structuring enrichment finished recordId={}, structuringSource={}, segmentCount={}, questionCount={}",
            record.id,
            parsed.structuringSource,
            parsed.segments.size,
            parsed.questions.size,
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
        logger.debug(
            "Persisted structured assets recordId={}, segmentCount={}, questionCount={}, answerCount={}",
            record.id,
            segments.size,
            persistedQuestions.size,
            answersToPersist.size,
        )
        if (persistedQuestions.isEmpty()) {
            logger.warn(
                "Structured parsing produced zero questions recordId={}, transcriptLength={}, structuringSource={}",
                record.id,
                transcriptText.length,
                persistedStructuringSource,
            )
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
                    rawTranscript = record.rawTranscript ?: transcriptText,
                    cleanedTranscript = transcriptText,
                    confirmedTranscript = transcriptText,
                    transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                    transcriptErrorCode = null,
                    transcriptErrorMessage = null,
                    transcriptRetryCount = record.transcriptRetryCount,
                    transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                    transcriptProcessingStartedAt = null,
                    transcriptNextRetryAt = null,
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
                rawTranscript = record.rawTranscript ?: transcriptText,
                cleanedTranscript = transcriptText,
                confirmedTranscript = transcriptText,
                transcriptStatus = TRANSCRIPT_STATUS_CONFIRMED,
                transcriptErrorCode = null,
                transcriptErrorMessage = null,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = null,
                transcriptNextRetryAt = null,
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
        logger.debug(
            "Rebuild completed recordId={}, analysisStatus={}, transcriptStatus={}, structuringStage={}, interviewerProfileId={}",
            record.id,
            ANALYSIS_STATUS_COMPLETED,
            TRANSCRIPT_STATUS_CONFIRMED,
            persistedStructuringSource,
            savedProfile.id,
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
        logger.debug(
            "Parsed transcript record stage=deterministic lineCount={}, segmentCount={}, questionCount={}",
            lines.size,
            segments.size,
            questions.size,
        )
        if (questions.isEmpty()) {
            logger.warn("Deterministic parser extracted zero questions lineCount={}, segmentCount={}", lines.size, segments.size)
        }
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

    private fun ensureInterviewerProfile(record: InterviewRecordEntity): InterviewerProfileEntity? {
        val questions = interviewRecordQuestionRepository.findByInterviewRecordIdOrderByOrderIndexAsc(record.id)
        if (questions.isEmpty() && record.analysisStatus != ANALYSIS_STATUS_COMPLETED) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Interviewer profile is not available until analysis completes for record: ${record.id}",
            )
        }
        if (questions.isEmpty()) {
            return null
        }
        val questionById = questions.associateBy { it.id }
        val parsedQuestions = questions.map { question ->
            ParsedQuestion(
                orderIndex = question.orderIndex,
                segmentStartSequence = 0,
                segmentEndSequence = 0,
                text = question.text,
                normalizedText = question.normalizedText ?: normalize(question.text),
                questionType = question.questionType,
                topicTags = decodeStringList(question.topicTagsJson),
                intentTags = decodeStringList(question.intentTagsJson),
                derivedFromResumeSection = question.derivedFromResumeSection,
                derivedFromResumeRecordType = question.derivedFromResumeRecordType,
                derivedFromResumeRecordId = question.derivedFromResumeRecordId,
                parentOrderIndex = question.parentQuestionId?.let { parentId -> questionById[parentId]?.orderIndex },
                answer = null,
            )
        }
        val now = clockService.now()
        val generatedProfile = try {
            interviewerProfileRepository.save(
                buildInterviewerProfile(
                    userId = record.userId,
                    recordId = record.id,
                    questions = parsedQuestions,
                    profileOverride = null,
                    structuringSource = record.structuringStage,
                    now = now,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            // Another request generated the same profile concurrently.
            return interviewerProfileRepository.findBySourceInterviewRecordId(record.id)
        }
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
                cleanedTranscript = record.cleanedTranscript,
                confirmedTranscript = record.confirmedTranscript,
                transcriptStatus = record.transcriptStatus,
                transcriptErrorCode = record.transcriptErrorCode,
                transcriptErrorMessage = record.transcriptErrorMessage,
                transcriptRetryCount = record.transcriptRetryCount,
                transcriptLastAttemptAt = record.transcriptLastAttemptAt,
                transcriptProcessingStartedAt = record.transcriptProcessingStartedAt,
                transcriptNextRetryAt = record.transcriptNextRetryAt,
                analysisStatus = record.analysisStatus,
                linkedResumeVersionId = record.linkedResumeVersionId,
                linkedJobPostingId = record.linkedJobPostingId,
                interviewerProfileId = generatedProfile.id,
                deterministicSummary = record.deterministicSummary,
                aiEnrichedSummary = record.aiEnrichedSummary,
                overallSummary = record.overallSummary,
                structuringStage = record.structuringStage,
                confirmedAt = record.confirmedAt,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
        return generatedProfile
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InterviewRecordService::class.java)
        private const val STRUCTURING_STAGE_DETERMINISTIC = "deterministic"
        private const val STRUCTURING_STAGE_AI_ENRICHED = "ai_enriched"
        private const val STRUCTURING_STAGE_CONFIRMED = "confirmed"
        private const val INTERVIEW_TYPE_GENERAL = "general"
        private const val TRANSCRIPT_STATUS_PENDING = "pending"
        private const val TRANSCRIPT_STATUS_PROCESSING = "processing"
        private const val TRANSCRIPT_STATUS_FAILED = "failed"
        private const val TRANSCRIPT_STATUS_CONFIRMED = "confirmed"
        private const val ANALYSIS_STATUS_PENDING = "pending"
        private const val ANALYSIS_STATUS_FAILED = "failed"
        private const val ANALYSIS_STATUS_COMPLETED = "completed"
        private const val TRANSCRIPT_ERROR_NOT_CONFIGURED = "transcription_not_configured"
        private const val TRANSCRIPT_ERROR_EXTRACTION_FAILED = "transcription_failed"
        private const val TRANSCRIPT_ERROR_EMPTY_TRANSCRIPT = "empty_transcript"
        private const val TRANSCRIPT_ERROR_PROCESSING_TIMEOUT = "processing_timeout"
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
        private const val REVIEW_REPLAY_MODE_PATTERN_SIMILAR = "pattern_similar"
        private const val REVIEW_REPLAY_MODE_PRESSURE_VARIANT = "pressure_variant"
        private const val DEFAULT_REPLAY_QUESTION_COUNT = 3
        private const val DEFAULT_REPLAY_SEED_COUNT = 3
        private const val REPLAY_BLOCKER_NO_QUESTIONS = "no_questions"
        private const val REPLAY_BLOCKER_NO_INTERVIEWER_PROFILE = "no_interviewer_profile"
        private val LOW_CONFIDENCE_THRESHOLD = BigDecimal("0.80")
        private const val SEGMENT_ISSUE_LOW_CONFIDENCE = "low_confidence"
        private const val SEGMENT_ISSUE_SPEAKER_OVERRIDE = "speaker_override"
        private const val SEGMENT_ISSUE_CONFIRMED_OVERRIDE = "confirmed_override"
        private const val SEGMENT_ACTION_REVIEW_NOW = "review_now"
        private const val SEGMENT_ACTION_JUMP_TO_QUESTION = "jump_to_question"
        private const val SEGMENT_ACTION_JUMP_TO_THREAD = "jump_to_thread"
        private const val SEGMENT_SEVERITY_HIGH = "high"
        private const val SEGMENT_SEVERITY_MEDIUM = "medium"
        private const val SEGMENT_SEVERITY_LOW = "low"
        private const val SEGMENT_PRIORITY_P0 = "p0"
        private const val SEGMENT_PRIORITY_P1 = "p1"
        private const val SEGMENT_PRIORITY_P2 = "p2"
        private const val SEGMENT_REVIEWER_LANE_TRANSCRIPT = "transcript_review"
        private const val SEGMENT_REVIEWER_LANE_QUESTION = "question_review"
        private const val SEGMENT_REVIEWER_LANE_THREAD = "thread_review"
        private const val MAX_TOP_PRIORITY_SEGMENT_ACTIONS = 5
        private const val TRANSCRIPT_CONFIRMATION_READY = "ready"
        private const val TRANSCRIPT_CONFIRMATION_NEEDS_REVIEW = "needs_review"
        private const val REVIEW_LANE_READY = "ready"
        private const val REVIEW_LANE_NEEDS_REVIEW = "needs_review"
        private const val REVIEW_LANE_KEY_TRANSCRIPT = "transcript"
        private const val REVIEW_LANE_KEY_QUESTION = "question"
        private const val REVIEW_LANE_KEY_THREAD = "thread"
        private const val REVIEW_LANE_TAB_OVERVIEW = "overview"
        private const val REVIEW_LANE_TAB_ISSUES = "issues"
        private const val REVIEW_LANE_TAB_QUESTIONS = "questions"
        private const val REVIEW_LANE_TAB_THREADS = "threads"
        private const val REVIEW_LANE_HIGHLIGHT_DANGER = "danger"
        private const val REVIEW_LANE_HIGHLIGHT_WARNING = "warning"
        private const val REVIEW_LANE_HIGHLIGHT_NEUTRAL = "neutral"
        private const val REVIEW_LANE_HIGHLIGHT_SUCCESS = "success"
        private const val REVIEW_ACTION_REVIEW_TRANSCRIPT = "review_transcript"
        private const val REVIEW_ACTION_REVIEW_ANSWERS = "review_answers"
        private const val REVIEW_ACTION_CONFIRM = "confirm"
        private const val REVIEW_ACTION_START_REPLAY = "start_replay"
        private const val REVIEW_BLOCKING_REASON_PENDING_TRANSCRIPT_EDITS = "pending_transcript_edits"
        private const val REVIEW_BLOCKING_REASON_WEAK_ANSWERS_PRESENT = "weak_answers_present"
        private const val REVIEW_BLOCKING_REASON_UNCONFIRMED_QUESTIONS_PRESENT = "unconfirmed_questions_present"
        private const val REVIEW_BLOCKING_REASON_WEAK_THREADS_PRESENT = "weak_threads_present"
        private const val REVIEW_BLOCKING_REASON_NO_THREADS_AVAILABLE = "no_threads_available"
        private const val THREAD_ACTION_REVIEW_WEAK_CHAIN = "review_weak_chain"
        private const val THREAD_ACTION_REPLAY_CHAIN = "replay_chain"
        private const val THREAD_ACTION_STABLE_CHAIN = "stable_chain"
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
