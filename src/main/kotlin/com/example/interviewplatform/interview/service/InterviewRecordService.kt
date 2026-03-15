package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.interview.dto.InterviewRecordAnalysisDto
import com.example.interviewplatform.interview.dto.InterviewRecordDetailDto
import com.example.interviewplatform.interview.dto.InterviewRecordListItemDto
import com.example.interviewplatform.interview.dto.InterviewRecordQuestionsResponseDto
import com.example.interviewplatform.interview.dto.InterviewRecordTranscriptDto
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
                overallSummary = null,
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
                overallSummary = record.overallSummary,
                createdAt = record.createdAt,
                updatedAt = now,
            ),
        )
        rebuildStructuredData(updatedRecord, rebuiltTranscript, now)
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

    private fun rebuildStructuredData(record: InterviewRecordEntity, transcriptText: String, now: Instant) {
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

        val parsed = practicalInterviewStructuringEnrichmentService.enrich(
            record = record,
            transcriptText = transcriptText,
            parsedTranscript = parseTranscript(transcriptText, now),
        )
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

        val overallSummary = parsed.overallSummaryOverride ?: buildOverallSummary(parsed.questions)
        val profile = buildInterviewerProfile(
            userId = record.userId,
            recordId = record.id,
            questions = parsed.questions,
            profileOverride = parsed.interviewerProfileOverride,
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
                    overallSummary = overallSummary,
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
                overallSummary = overallSummary,
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

    companion object {
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
    }
}

data class ParsedInterviewTranscript(
    val segments: List<ParsedSegment>,
    val questions: List<ParsedQuestion>,
    val overallSummaryOverride: String? = null,
    val interviewerProfileOverride: PracticalInterviewInterviewerProfileOverride? = null,
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
