package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.resume.dto.CreateResumeEditorCommentReplyRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorCommentRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorPresenceRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorQuestionCardRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorQuestionSuggestionRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorRewriteSuggestionRequest
import com.example.interviewplatform.resume.dto.ImportResumeEditorMarkdownRequest
import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorChangeSummaryDto
import com.example.interviewplatform.resume.dto.ResumeEditorCommentReplyDto
import com.example.interviewplatform.resume.dto.ResumeEditorCommentSummaryDto
import com.example.interviewplatform.resume.dto.ResumeEditorCommentThreadDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergeConflictDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergePreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergePreviewRequest
import com.example.interviewplatform.resume.dto.ResumeEditorPresenceDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionCardDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionCardSummaryDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionSuggestionResponseDto
import com.example.interviewplatform.resume.dto.ResumeEditorRevisionDto
import com.example.interviewplatform.resume.dto.ResumeEditorRevisionListItemDto
import com.example.interviewplatform.resume.dto.ResumeEditorRewriteSuggestionDto
import com.example.interviewplatform.resume.dto.ResumeEditorRewriteSuggestionResponseDto
import com.example.interviewplatform.resume.dto.ResumeEditorSuggestedQuestionDto
import com.example.interviewplatform.resume.dto.ResumeEditorTrackedChangeDto
import com.example.interviewplatform.resume.dto.ResumeEditorTrackedChangesDto
import com.example.interviewplatform.resume.dto.ResumeEditorWorkspaceDto
import com.example.interviewplatform.resume.dto.UpdateResumeEditorCommentRequest
import com.example.interviewplatform.resume.dto.UpdateResumeEditorDocumentRequest
import com.example.interviewplatform.resume.dto.UpdateResumeEditorQuestionCardRequest
import com.example.interviewplatform.resume.entity.ResumeContactPointEntity
import com.example.interviewplatform.resume.entity.ResumeEditorCommentReplyEntity
import com.example.interviewplatform.resume.entity.ResumeEditorCommentThreadEntity
import com.example.interviewplatform.resume.entity.ResumeEditorPresenceSessionEntity
import com.example.interviewplatform.resume.entity.ResumeEditorQuestionCardEntity
import com.example.interviewplatform.resume.entity.ResumeEditorWorkspaceEntity
import com.example.interviewplatform.resume.entity.ResumeEditorWorkspaceRevisionEntity
import com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProjectTagEntity
import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.example.interviewplatform.resume.repository.ResumeAchievementItemRepository
import com.example.interviewplatform.resume.repository.ResumeAwardItemRepository
import com.example.interviewplatform.resume.repository.ResumeCertificationItemRepository
import com.example.interviewplatform.resume.repository.ResumeCompetencyItemRepository
import com.example.interviewplatform.resume.repository.ResumeContactPointRepository
import com.example.interviewplatform.resume.repository.ResumeEducationItemRepository
import com.example.interviewplatform.resume.repository.ResumeEditorCommentReplyRepository
import com.example.interviewplatform.resume.repository.ResumeEditorCommentThreadRepository
import com.example.interviewplatform.resume.repository.ResumeEditorPresenceSessionRepository
import com.example.interviewplatform.resume.repository.ResumeEditorQuestionCardRepository
import com.example.interviewplatform.resume.repository.ResumeEditorWorkspaceRepository
import com.example.interviewplatform.resume.repository.ResumeEditorWorkspaceRevisionRepository
import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProfileSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectTagRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.user.repository.UserRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.temporal.ChronoUnit

@Service
class ResumeEditorService(
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeProfileSnapshotRepository: ResumeProfileSnapshotRepository,
    private val resumeContactPointRepository: ResumeContactPointRepository,
    private val resumeCompetencyItemRepository: ResumeCompetencyItemRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeExperienceSnapshotRepository: ResumeExperienceSnapshotRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeProjectTagRepository: ResumeProjectTagRepository,
    private val resumeAchievementItemRepository: ResumeAchievementItemRepository,
    private val resumeEducationItemRepository: ResumeEducationItemRepository,
    private val resumeCertificationItemRepository: ResumeCertificationItemRepository,
    private val resumeAwardItemRepository: ResumeAwardItemRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val resumeEditorWorkspaceRepository: ResumeEditorWorkspaceRepository,
    private val resumeEditorCommentThreadRepository: ResumeEditorCommentThreadRepository,
    private val resumeEditorCommentReplyRepository: ResumeEditorCommentReplyRepository,
    private val resumeEditorPresenceSessionRepository: ResumeEditorPresenceSessionRepository,
    private val resumeEditorQuestionCardRepository: ResumeEditorQuestionCardRepository,
    private val resumeEditorWorkspaceRevisionRepository: ResumeEditorWorkspaceRevisionRepository,
    private val questionRepository: QuestionRepository,
    private val resumeQuestionHeatmapService: ResumeQuestionHeatmapService,
    private val resumeEditorMarkdownService: ResumeEditorMarkdownService,
    private val resumeEditorPrintPreviewService: ResumeEditorPrintPreviewService,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
    private val clockService: ClockService,
) {
    @Transactional
    fun getWorkspace(userId: Long, versionId: Long): ResumeEditorWorkspaceDto {
        val version = requireOwnedVersion(userId, versionId)
        val workspace = getOrCreateWorkspace(userId, version)
        return toWorkspaceDto(userId, version, workspace)
    }

    @Transactional
    fun getRevisions(userId: Long, versionId: Long): List<ResumeEditorRevisionListItemDto> {
        val version = requireOwnedVersion(userId, versionId)
        val workspace = getOrCreateWorkspace(userId, version)
        return resumeEditorWorkspaceRevisionRepository.findByResumeEditorWorkspaceIdOrderByRevisionNoDesc(workspace.id)
            .map { it.toListItemDto() }
    }

    @Transactional
    fun getRevision(userId: Long, versionId: Long, revisionId: Long): ResumeEditorRevisionDto {
        requireOwnedVersion(userId, versionId)
        val revision = resumeEditorWorkspaceRevisionRepository.findByIdAndResumeVersionId(revisionId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor revision not found: $revisionId")
        return revision.toDetailDto()
    }

    @Transactional
    fun getTrackedChanges(
        userId: Long,
        versionId: Long,
        fromRevisionId: Long,
        toRevisionId: Long,
    ): ResumeEditorTrackedChangesDto {
        requireOwnedVersion(userId, versionId)
        val fromRevision = resumeEditorWorkspaceRevisionRepository.findByIdAndResumeVersionId(fromRevisionId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor revision not found: $fromRevisionId")
        val toRevision = resumeEditorWorkspaceRevisionRepository.findByIdAndResumeVersionId(toRevisionId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor revision not found: $toRevisionId")
        val changes = buildTrackedChanges(fromRevision.toDocument(), toRevision.toDocument())
        return ResumeEditorTrackedChangesDto(
            resumeVersionId = versionId,
            fromRevisionId = fromRevisionId,
            toRevisionId = toRevisionId,
            changeSummary = buildChangeSummary(fromRevision.toDocument(), toRevision.toDocument()),
            changes = changes,
        )
    }

    @Transactional
    fun getMergePreview(
        userId: Long,
        versionId: Long,
        request: ResumeEditorMergePreviewRequest,
    ): ResumeEditorMergePreviewDto {
        val version = requireOwnedVersion(userId, versionId)
        val workspace = getOrCreateWorkspace(userId, version)
        val baseRevision = resumeEditorWorkspaceRevisionRepository.findByResumeEditorWorkspaceIdAndRevisionNo(workspace.id, request.baseRevisionNo)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Base revision not found: ${request.baseRevisionNo}")
        requireValidBlocks(request.blocks)
        val currentDocument = decodeDocument(workspace)
        val proposedDocument = ResumeEditorDocumentDto(
            astVersion = 1,
            markdownSource = request.markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(request.blocks),
            blocks = request.blocks,
            layoutMetadata = request.layoutMetadata,
        )
        val mergeResult = mergeDocuments(baseRevision.toDocument(), currentDocument, proposedDocument)
        val currentRevision = resumeEditorWorkspaceRevisionRepository.findByResumeEditorWorkspaceIdOrderByRevisionNoDesc(workspace.id)
            .firstOrNull()
            ?: baseRevision
        return ResumeEditorMergePreviewDto(
            resumeVersionId = versionId,
            baseRevisionId = baseRevision.id,
            currentRevisionId = currentRevision.id,
            mergeStatus = if (mergeResult.conflicts.isEmpty()) "clean" else "conflicted",
            mergedDocument = mergeResult.document,
            conflicts = mergeResult.conflicts,
            changeSummary = buildChangeSummary(currentDocument, mergeResult.document),
        )
    }

    @Transactional
    fun upsertPresence(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorPresenceRequest,
    ): List<ResumeEditorPresenceDto> {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val now = clockService.now()
        val existing = resumeEditorPresenceSessionRepository.findByResumeEditorWorkspaceIdAndSessionKey(workspace.id, request.sessionKey.trim())
        resumeEditorPresenceSessionRepository.save(
            ResumeEditorPresenceSessionEntity(
                id = existing?.id ?: 0,
                resumeEditorWorkspaceId = workspace.id,
                userId = userId,
                sessionKey = request.sessionKey.trim(),
                viewMode = request.viewMode?.trim()?.takeIf { it.isNotEmpty() },
                selectedBlockId = request.selectedBlockId?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return activePresence(workspace.id, userId)
    }

    @Transactional
    fun updateDocument(userId: Long, versionId: Long, request: UpdateResumeEditorDocumentRequest): ResumeEditorWorkspaceDto {
        val version = requireOwnedVersion(userId, versionId)
        val existing = getOrCreateWorkspace(userId, version)
        enforceRevision(request.baseRevisionNo, existing.revisionNo)
        requireValidBlocks(request.blocks)
        val document = ResumeEditorDocumentDto(
            astVersion = 1,
            markdownSource = request.markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(request.blocks),
            blocks = request.blocks,
            layoutMetadata = request.layoutMetadata,
        )
        saveWorkspace(existing, document, userId, request.changeSource?.trim()?.takeIf { it.isNotEmpty() } ?: CHANGE_SOURCE_MANUAL_EDIT)
        return getWorkspace(userId, versionId)
    }

    @Transactional
    fun importMarkdown(userId: Long, versionId: Long, request: ImportResumeEditorMarkdownRequest): ResumeEditorWorkspaceDto {
        val version = requireOwnedVersion(userId, versionId)
        val existing = getOrCreateWorkspace(userId, version)
        enforceRevision(request.baseRevisionNo, existing.revisionNo)
        val imported = resumeEditorMarkdownService.parse(request.markdownSource)
        val document = if (request.replaceDocument) {
            imported
        } else {
            val current = decodeDocument(existing)
            val appendedBlocks = imported.blocks.mapIndexed { index, block ->
                block.copy(
                    blockId = "${block.blockId}-appended-$index",
                    displayOrder = current.blocks.size + index,
                )
            }
            ResumeEditorDocumentDto(
                astVersion = 1,
                markdownSource = listOfNotNull(current.markdownSource, imported.markdownSource).joinToString("\n\n").trim(),
                blocks = current.blocks + appendedBlocks,
                layoutMetadata = current.layoutMetadata + imported.layoutMetadata,
            )
        }
        saveWorkspace(existing, document, userId, request.changeSource?.trim()?.takeIf { it.isNotEmpty() } ?: CHANGE_SOURCE_MARKDOWN_IMPORT)
        return getWorkspace(userId, versionId)
    }

    @Transactional
    fun createComment(userId: Long, versionId: Long, request: CreateResumeEditorCommentRequest): ResumeEditorCommentThreadDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val block = requireBlock(workspace, request.blockId)
        validateSelection(block, request.selectionStartOffset, request.selectionEndOffset)
        val now = clockService.now()
        val saved = resumeEditorCommentThreadRepository.save(
            ResumeEditorCommentThreadEntity(
                userId = userId,
                resumeEditorWorkspaceId = workspace.id,
                resumeVersionId = versionId,
                blockId = request.blockId.trim(),
                fieldPath = request.fieldPath?.trim()?.takeIf { it.isNotEmpty() } ?: block.fieldPath,
                selectionStartOffset = request.selectionStartOffset,
                selectionEndOffset = request.selectionEndOffset,
                selectedText = request.selectedText?.trim()?.takeIf { it.isNotEmpty() },
                body = request.body.trim(),
                status = COMMENT_STATUS_OPEN,
                resolvedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        touchWorkspace(workspace)
        return saved.toDto()
    }

    @Transactional
    fun createCommentReply(
        userId: Long,
        versionId: Long,
        commentId: Long,
        request: CreateResumeEditorCommentReplyRequest,
    ): ResumeEditorCommentThreadDto {
        requireOwnedVersion(userId, versionId)
        val comment = resumeEditorCommentThreadRepository.findByIdAndResumeVersionId(commentId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor comment not found: $commentId")
        if (comment.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor comment not found: $commentId")
        }
        val now = clockService.now()
        resumeEditorCommentReplyRepository.save(
            ResumeEditorCommentReplyEntity(
                userId = userId,
                resumeEditorCommentThreadId = comment.id,
                body = request.body.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        val replies = resumeEditorCommentReplyRepository.findByResumeEditorCommentThreadIdInOrderByCreatedAtAsc(listOf(comment.id))
            .map { it.toDto() }
        return comment.toDto(replies)
    }

    @Transactional
    fun updateComment(
        userId: Long,
        versionId: Long,
        commentId: Long,
        request: UpdateResumeEditorCommentRequest,
    ): ResumeEditorCommentThreadDto {
        requireOwnedVersion(userId, versionId)
        val existing = resumeEditorCommentThreadRepository.findByIdAndResumeVersionId(commentId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor comment not found: $commentId")
        if (existing.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor comment not found: $commentId")
        }
        val status = request.status?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: existing.status
        validateCommentStatus(status)
        val updated = resumeEditorCommentThreadRepository.save(
            ResumeEditorCommentThreadEntity(
                id = existing.id,
                userId = existing.userId,
                resumeEditorWorkspaceId = existing.resumeEditorWorkspaceId,
                resumeVersionId = existing.resumeVersionId,
                blockId = existing.blockId,
                fieldPath = existing.fieldPath,
                selectionStartOffset = existing.selectionStartOffset,
                selectionEndOffset = existing.selectionEndOffset,
                selectedText = existing.selectedText,
                body = request.body?.trim()?.takeIf { it.isNotEmpty() } ?: existing.body,
                status = status,
                resolvedAt = if (status == COMMENT_STATUS_RESOLVED) clockService.now() else null,
                createdAt = existing.createdAt,
                updatedAt = clockService.now(),
            ),
        )
        val replies = resumeEditorCommentReplyRepository.findByResumeEditorCommentThreadIdInOrderByCreatedAtAsc(listOf(updated.id))
            .map { it.toDto() }
        return updated.toDto(replies)
    }

    @Transactional
    fun createQuestionCard(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorQuestionCardRequest,
    ): ResumeEditorQuestionCardDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val block = requireBlock(workspace, request.blockId)
        validateSelection(block, request.selectionStartOffset, request.selectionEndOffset)
        request.linkedQuestionId?.let { linkedId ->
            if (!questionRepository.existsById(linkedId)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Linked question not found: $linkedId")
            }
        }
        val now = clockService.now()
        val saved = resumeEditorQuestionCardRepository.save(
            ResumeEditorQuestionCardEntity(
                userId = userId,
                resumeEditorWorkspaceId = workspace.id,
                resumeVersionId = versionId,
                blockId = request.blockId.trim(),
                fieldPath = request.fieldPath?.trim()?.takeIf { it.isNotEmpty() } ?: block.fieldPath,
                selectionStartOffset = request.selectionStartOffset,
                selectionEndOffset = request.selectionEndOffset,
                selectedText = request.selectedText?.trim()?.takeIf { it.isNotEmpty() },
                title = request.title?.trim()?.takeIf { it.isNotEmpty() },
                questionText = request.questionText.trim(),
                questionType = request.questionType.trim().lowercase(),
                sourceType = QUESTION_CARD_SOURCE_MANUAL,
                linkedQuestionId = request.linkedQuestionId,
                status = QUESTION_CARD_STATUS_ACTIVE,
                followUpSuggestionsJson = encodeList(request.followUpSuggestions),
                createdAt = now,
                updatedAt = now,
            ),
        )
        touchWorkspace(workspace)
        return saved.toDto()
    }

    @Transactional
    fun updateQuestionCard(
        userId: Long,
        versionId: Long,
        cardId: Long,
        request: UpdateResumeEditorQuestionCardRequest,
    ): ResumeEditorQuestionCardDto {
        requireOwnedVersion(userId, versionId)
        val existing = resumeEditorQuestionCardRepository.findByIdAndResumeVersionId(cardId, versionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor question card not found: $cardId")
        if (existing.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume editor question card not found: $cardId")
        }
        request.linkedQuestionId?.let { linkedId ->
            if (!questionRepository.existsById(linkedId)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Linked question not found: $linkedId")
            }
        }
        val status = request.status?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: existing.status
        validateQuestionCardStatus(status)
        val updated = resumeEditorQuestionCardRepository.save(
            ResumeEditorQuestionCardEntity(
                id = existing.id,
                userId = existing.userId,
                resumeEditorWorkspaceId = existing.resumeEditorWorkspaceId,
                resumeVersionId = existing.resumeVersionId,
                blockId = existing.blockId,
                fieldPath = existing.fieldPath,
                selectionStartOffset = existing.selectionStartOffset,
                selectionEndOffset = existing.selectionEndOffset,
                selectedText = existing.selectedText,
                title = request.title?.trim()?.takeIf { it.isNotEmpty() } ?: existing.title,
                questionText = request.questionText?.trim()?.takeIf { it.isNotEmpty() } ?: existing.questionText,
                questionType = request.questionType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: existing.questionType,
                sourceType = existing.sourceType,
                linkedQuestionId = request.linkedQuestionId ?: existing.linkedQuestionId,
                status = status,
                followUpSuggestionsJson = request.followUpSuggestions?.let(::encodeList) ?: existing.followUpSuggestionsJson,
                createdAt = existing.createdAt,
                updatedAt = clockService.now(),
            ),
        )
        return updated.toDto()
    }

    @Transactional
    fun generateQuestionSuggestions(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorQuestionSuggestionRequest,
    ): ResumeEditorQuestionSuggestionResponseDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val block = requireBlock(workspace, request.blockId)
        val selectedText = resolveSelectedText(block, request.selectedText)
        return ResumeEditorQuestionSuggestionResponseDto(
            resumeVersionId = versionId,
            blockId = block.blockId,
            selectedText = selectedText,
            sourceType = "heuristic",
            suggestions = buildQuestionSuggestions(selectedText).take(request.maxSuggestions),
        )
    }

    @Transactional
    fun generateRewriteSuggestions(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorRewriteSuggestionRequest,
    ): ResumeEditorRewriteSuggestionResponseDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val block = requireBlock(workspace, request.blockId)
        val selectedText = resolveSelectedText(block, request.selectedText)
        return ResumeEditorRewriteSuggestionResponseDto(
            resumeVersionId = versionId,
            blockId = block.blockId,
            selectedText = selectedText,
            sourceType = "heuristic",
            suggestions = buildRewriteSuggestions(selectedText),
        )
    }

    @Transactional
    fun getPrintPreview(userId: Long, versionId: Long): ResumeEditorPrintPreviewDto {
        val version = requireOwnedVersion(userId, versionId)
        val workspace = getOrCreateWorkspace(userId, version)
        val document = decodeDocument(workspace)
        return resumeEditorPrintPreviewService.build(
            resumeVersionId = versionId,
            workspaceId = workspace.id,
            title = document.blocks.firstOrNull { it.blockType == "header" }?.title ?: version.fileName ?: "Resume print preview",
            blocks = document.blocks,
        )
    }

    private fun getOrCreateWorkspace(userId: Long, version: ResumeVersionEntity): ResumeEditorWorkspaceEntity {
        resumeEditorWorkspaceRepository.findByResumeVersionId(version.id)?.let { return it }
        val now = clockService.now()
        val initialDocument = buildInitialDocument(version)
        val saved = resumeEditorWorkspaceRepository.save(
            ResumeEditorWorkspaceEntity(
                userId = userId,
                resumeVersionId = version.id,
                workspaceStatus = WORKSPACE_STATUS_DRAFT,
                revisionNo = 1,
                markdownSource = initialDocument.markdownSource,
                documentJson = objectMapper.writeValueAsString(initialDocument.copy(markdownSource = null)),
                layoutMetadataJson = encodeMap(initialDocument.layoutMetadata),
                createdAt = now,
                updatedAt = now,
            ),
        )
        createRevision(saved, userId, CHANGE_SOURCE_BOOTSTRAP, emptyDocument(), initialDocument, now)
        return saved
    }

    private fun saveWorkspace(
        existing: ResumeEditorWorkspaceEntity,
        document: ResumeEditorDocumentDto,
        userId: Long,
        changeSource: String,
    ) {
        val previous = decodeDocument(existing)
        val now = clockService.now()
        val saved = resumeEditorWorkspaceRepository.save(
            ResumeEditorWorkspaceEntity(
                id = existing.id,
                userId = existing.userId,
                resumeVersionId = existing.resumeVersionId,
                workspaceStatus = WORKSPACE_STATUS_DRAFT,
                revisionNo = existing.revisionNo + 1,
                markdownSource = document.markdownSource,
                documentJson = objectMapper.writeValueAsString(document.copy(markdownSource = null)),
                layoutMetadataJson = encodeMap(document.layoutMetadata),
                createdAt = existing.createdAt,
                updatedAt = now,
            ),
        )
        createRevision(saved, userId, changeSource, previous, document, now)
    }

    private fun toWorkspaceDto(userId: Long, version: ResumeVersionEntity, workspace: ResumeEditorWorkspaceEntity): ResumeEditorWorkspaceDto {
        val document = decodeDocument(workspace)
        val commentEntities = resumeEditorCommentThreadRepository.findByResumeEditorWorkspaceIdOrderByCreatedAtAsc(workspace.id)
        val repliesByThreadId = if (commentEntities.isEmpty()) {
            emptyMap()
        } else {
            resumeEditorCommentReplyRepository.findByResumeEditorCommentThreadIdInOrderByCreatedAtAsc(commentEntities.map { it.id })
                .groupBy { it.resumeEditorCommentThreadId }
        }
        val comments = commentEntities.map { entity ->
            entity.toDto(repliesByThreadId[entity.id].orEmpty().map { reply -> reply.toDto() })
        }
        val questionCards = resumeEditorQuestionCardRepository.findByResumeEditorWorkspaceIdOrderByCreatedAtAsc(workspace.id)
            .map { it.toDto() }
        val heatmap = resumeQuestionHeatmapService.getHeatmap(userId, version.id, "all")
        return ResumeEditorWorkspaceDto(
            workspaceId = workspace.id,
            resumeVersionId = version.id,
            sourceVersionNo = version.versionNo,
            sourceFileName = version.fileName,
            workspaceStatus = workspace.workspaceStatus,
            revisionNo = workspace.revisionNo,
            supportedViewModes = supportedViewModes,
            document = document,
            comments = comments,
            questionCards = questionCards,
            commentSummary = ResumeEditorCommentSummaryDto(
                totalCount = comments.size,
                openCount = comments.count { it.status == COMMENT_STATUS_OPEN },
                resolvedCount = comments.count { it.status == COMMENT_STATUS_RESOLVED },
                totalReplyCount = comments.sumOf { it.replyCount },
            ),
            questionCardSummary = ResumeEditorQuestionCardSummaryDto(
                totalCount = questionCards.size,
                activeCount = questionCards.count { it.status == QUESTION_CARD_STATUS_ACTIVE },
                archivedCount = questionCards.count { it.status == QUESTION_CARD_STATUS_ARCHIVED },
            ),
            heatmapAvailable = heatmap.summary.totalLinkedQuestions > 0,
            heatmapSummary = if (heatmap.summary.totalLinkedQuestions > 0) heatmap.summary else null,
            activePresence = activePresence(workspace.id, userId),
            latestRevision = resumeEditorWorkspaceRevisionRepository.findByResumeEditorWorkspaceIdOrderByRevisionNoDesc(workspace.id)
                .firstOrNull()
                ?.toListItemDto(),
            createdAt = workspace.createdAt,
            updatedAt = workspace.updatedAt,
        )
    }

    private fun buildInitialDocument(version: ResumeVersionEntity): ResumeEditorDocumentDto {
        val blocks = mutableListOf<ResumeEditorBlockDto>()
        val profile = resumeProfileSnapshotRepository.findByResumeVersionId(version.id)
        val contacts = resumeContactPointRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val competencies = resumeCompetencyItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val skills = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(version.id)
        val experiences = resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val projects = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val tagsByProjectId = if (projects.isEmpty()) {
            emptyMap()
        } else {
            resumeProjectTagRepository.findByResumeProjectSnapshotIdInOrderByResumeProjectSnapshotIdAscDisplayOrderAscIdAsc(projects.map { it.id })
                .groupBy { it.resumeProjectSnapshotId }
        }
        val achievements = resumeAchievementItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val education = resumeEducationItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val certifications = resumeCertificationItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val awards = resumeAwardItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(version.id)
        val risks = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(version.id)

        var order = 0
        profile?.let {
            blocks += ResumeEditorBlockDto(
                blockId = "header-profile",
                blockType = "header",
                title = it.fullName,
                text = listOfNotNull(it.headline, it.locationText).joinToString(" · ").takeIf(String::isNotBlank),
                lines = emptyList(),
                sourceAnchorType = "profile",
                sourceAnchorRecordId = null,
                sourceAnchorKey = "profile",
                fieldPath = "profile.headline",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        if (contacts.isNotEmpty()) {
            blocks += ResumeEditorBlockDto(
                blockId = "contact-points",
                blockType = "contact",
                title = "Contact",
                text = null,
                lines = contacts.map(::formatContactLine),
                sourceAnchorType = "profile",
                sourceAnchorRecordId = null,
                sourceAnchorKey = "contacts",
                fieldPath = "profile.contacts",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        val summaryText = version.summaryText?.takeIf { it.isNotBlank() } ?: profile?.summaryText ?: profile?.headline
        if (!summaryText.isNullOrBlank()) {
            blocks += ResumeEditorBlockDto(
                blockId = "summary-main",
                blockType = "summary",
                title = "Summary",
                text = summaryText,
                lines = emptyList(),
                sourceAnchorType = "summary",
                sourceAnchorRecordId = null,
                sourceAnchorKey = "summary",
                fieldPath = "resume.summaryText",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        competencies.forEach { competency ->
            blocks += ResumeEditorBlockDto(
                blockId = "competency-${competency.id}",
                blockType = "callout",
                title = competency.title,
                text = competency.description.ifBlank { competency.sourceText.orEmpty() },
                lines = emptyList(),
                sourceAnchorType = "competency",
                sourceAnchorRecordId = competency.id,
                sourceAnchorKey = null,
                fieldPath = "competency.sourceText",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        experiences.forEach { experience ->
            blocks += ResumeEditorBlockDto(
                blockId = "experience-${experience.id}",
                blockType = "experience_item",
                title = listOfNotNull(experience.companyName, experience.roleName).joinToString(" | ").takeIf { it.isNotBlank() },
                text = experience.sourceText.ifBlank { experience.summaryText },
                lines = listOf(experience.summaryText).plus(listOfNotNull(experience.impactText)).filter { it.isNotBlank() }.distinct(),
                sourceAnchorType = "experience",
                sourceAnchorRecordId = experience.id,
                sourceAnchorKey = null,
                fieldPath = "experience.sourceText",
                displayOrder = order++,
                metadata = mapOf(
                    "employmentType" to (experience.employmentType ?: ""),
                    "riskLevel" to experience.riskLevel,
                ).filterValues { it.isNotBlank() },
            )
        }
        projects.forEach { project ->
            blocks += ResumeEditorBlockDto(
                blockId = "project-${project.id}",
                blockType = "project_item",
                title = project.title,
                text = project.sourceText ?: project.contentText ?: project.summaryText,
                lines = buildProjectLines(project, tagsByProjectId[project.id].orEmpty()),
                sourceAnchorType = "project",
                sourceAnchorRecordId = project.id,
                sourceAnchorKey = null,
                fieldPath = "project.sourceText",
                displayOrder = order++,
                metadata = mapOf(
                    "roleName" to (project.roleName ?: ""),
                    "organizationName" to (project.organizationName ?: ""),
                    "projectCategoryName" to (project.projectCategoryName ?: ""),
                ).filterValues { it.isNotBlank() },
            )
        }
        if (skills.isNotEmpty()) {
            blocks += ResumeEditorBlockDto(
                blockId = "skills-group",
                blockType = "skills_group",
                title = "Skills",
                text = null,
                lines = skills.map { it.skillName },
                sourceAnchorType = "skill",
                sourceAnchorRecordId = null,
                sourceAnchorKey = "skills",
                fieldPath = "skill.skillName",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        achievements.forEach { achievement ->
            blocks += ResumeEditorBlockDto(
                blockId = "achievement-${achievement.id}",
                blockType = "bullet_item",
                title = achievement.title,
                text = achievement.impactSummary.ifBlank { achievement.metricText ?: achievement.sourceText.orEmpty() },
                lines = emptyList(),
                sourceAnchorType = "experience",
                sourceAnchorRecordId = achievement.resumeExperienceSnapshotId,
                sourceAnchorKey = null,
                fieldPath = "achievement.sourceText",
                displayOrder = order++,
                metadata = mapOf("severityHint" to (achievement.severityHint ?: "")).filterValues { it.isNotBlank() },
            )
        }
        education.forEach { item ->
            blocks += ResumeEditorBlockDto(
                blockId = "education-${item.id}",
                blockType = "education_item",
                title = item.institutionName,
                text = listOfNotNull(item.degreeName, item.fieldOfStudy, item.description).joinToString(" · ").takeIf { it.isNotBlank() },
                lines = emptyList(),
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = "education.sourceText",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        certifications.forEach { item ->
            blocks += ResumeEditorBlockDto(
                blockId = "certification-${item.id}",
                blockType = "certification_item",
                title = item.name,
                text = listOfNotNull(item.issuerName, item.scoreText).joinToString(" · ").takeIf { it.isNotBlank() },
                lines = emptyList(),
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = "certification.sourceText",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        awards.forEach { item ->
            blocks += ResumeEditorBlockDto(
                blockId = "award-${item.id}",
                blockType = "note",
                title = item.title,
                text = listOfNotNull(item.issuerName, item.description).joinToString(" · ").takeIf { it.isNotBlank() },
                lines = emptyList(),
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = "award.sourceText",
                displayOrder = order++,
                metadata = emptyMap(),
            )
        }
        risks.forEach { risk ->
            blocks += ResumeEditorBlockDto(
                blockId = "risk-${risk.id}",
                blockType = "callout",
                title = risk.title,
                text = risk.description,
                lines = emptyList(),
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = "risk.description",
                displayOrder = order++,
                metadata = mapOf("severity" to risk.severity, "riskType" to risk.riskType),
            )
        }
        if (blocks.isEmpty()) {
            blocks += ResumeEditorBlockDto(
                blockId = "raw-text-fallback",
                blockType = "body",
                title = "Imported resume",
                text = version.rawText ?: version.summaryText.orEmpty(),
                lines = emptyList(),
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = "resume.rawText",
                displayOrder = 0,
                metadata = emptyMap(),
            )
        }
        return ResumeEditorDocumentDto(
            astVersion = 1,
            markdownSource = resumeEditorMarkdownService.render(blocks),
            blocks = blocks,
            layoutMetadata = mapOf(
                "printProfile" to "a4_resume",
                "workspaceMode" to "resume_editor",
            ),
        )
    }

    private fun requireOwnedVersion(userId: Long, versionId: Long): ResumeVersionEntity =
        resumeVersionRepository.findById(versionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId") }
            .also {
                if (resumeVersionRepository.findResumeOwnerIdByVersionId(versionId) != userId) {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId")
                }
            }

    private fun requireBlock(workspace: ResumeEditorWorkspaceEntity, blockId: String): ResumeEditorBlockDto =
        decodeDocument(workspace).blocks.firstOrNull { it.blockId == blockId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resume editor blockId: $blockId")

    private fun validateSelection(block: ResumeEditorBlockDto, startOffset: Int?, endOffset: Int?) {
        if ((startOffset == null) != (endOffset == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionStartOffset and selectionEndOffset must be provided together")
        }
        if (startOffset != null && endOffset != null) {
            if (endOffset <= startOffset) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionEndOffset must be greater than selectionStartOffset")
            }
            if (endOffset > blockContent(block).length) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selection exceeds block content length")
            }
        }
    }

    private fun requireValidBlocks(blocks: List<ResumeEditorBlockDto>) {
        if (blocks.map { it.blockId }.toSet().size != blocks.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume editor blockId values must be unique")
        }
        if (blocks.any { it.blockId.isBlank() || it.blockType.isBlank() }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume editor blocks require non-blank blockId and blockType")
        }
        blocks.forEach { block ->
            block.inlineMarks.forEach { mark ->
                if (mark.endOffset <= mark.startOffset || mark.endOffset > blockContent(block).length) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline mark range is invalid for block ${block.blockId}")
                }
            }
        }
    }

    private fun decodeDocument(workspace: ResumeEditorWorkspaceEntity): ResumeEditorDocumentDto {
        val stored = objectMapper.readValue(workspace.documentJson, ResumeEditorDocumentDto::class.java)
        return stored.copy(
            markdownSource = workspace.markdownSource ?: stored.markdownSource ?: resumeEditorMarkdownService.render(stored.blocks),
            layoutMetadata = decodeMap(workspace.layoutMetadataJson).ifEmpty { stored.layoutMetadata },
        )
    }

    private fun touchWorkspace(workspace: ResumeEditorWorkspaceEntity) {
        resumeEditorWorkspaceRepository.save(
            ResumeEditorWorkspaceEntity(
                id = workspace.id,
                userId = workspace.userId,
                resumeVersionId = workspace.resumeVersionId,
                workspaceStatus = workspace.workspaceStatus,
                revisionNo = workspace.revisionNo,
                markdownSource = workspace.markdownSource,
                documentJson = workspace.documentJson,
                layoutMetadataJson = workspace.layoutMetadataJson,
                createdAt = workspace.createdAt,
                updatedAt = clockService.now(),
            ),
        )
    }

    private fun buildProjectLines(project: ResumeProjectSnapshotEntity, tags: List<ResumeProjectTagEntity>): List<String> =
        buildList {
            project.summaryText.takeIf { it.isNotBlank() }?.let(::add)
            project.contentText?.takeIf { it.isNotBlank() }?.let(::add)
            if (tags.isNotEmpty()) {
                add("Tech: ${tags.joinToString(", ") { it.tagName }}")
            } else {
                project.techStackText?.takeIf { it.isNotBlank() }?.let { add("Tech: $it") }
            }
        }

    private fun formatContactLine(contact: ResumeContactPointEntity): String =
        listOfNotNull(
            contact.label?.takeIf { it.isNotBlank() },
            contact.valueText?.takeIf { it.isNotBlank() },
            contact.url?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    private fun blockContent(block: ResumeEditorBlockDto): String =
        listOfNotNull(block.title?.takeIf { it.isNotBlank() }, block.text?.takeIf { it.isNotBlank() })
            .plus(block.lines)
            .joinToString("\n")

    private fun activePresence(workspaceId: Long, currentUserId: Long): List<ResumeEditorPresenceDto> {
        val cutoff = clockService.now().minus(PRESENCE_TTL_MINUTES, ChronoUnit.MINUTES)
        val sessions = resumeEditorPresenceSessionRepository.findByResumeEditorWorkspaceIdOrderByUpdatedAtDesc(workspaceId)
            .filter { !it.updatedAt.isBefore(cutoff) }
        if (sessions.isEmpty()) {
            return emptyList()
        }
        val usersById = userRepository.findAllById(sessions.map { it.userId }.toSet()).associateBy { it.id }
        return sessions.map { session ->
            ResumeEditorPresenceDto(
                sessionKey = session.sessionKey,
                userId = session.userId,
                userLabel = usersById[session.userId]?.email ?: "User ${session.userId}",
                viewMode = session.viewMode,
                selectedBlockId = session.selectedBlockId,
                isCurrentUser = session.userId == currentUserId,
                updatedAt = session.updatedAt,
            )
        }
    }

    private fun enforceRevision(baseRevisionNo: Int?, currentRevisionNo: Int) {
        if (baseRevisionNo != null && baseRevisionNo != currentRevisionNo) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Resume editor workspace is out of date. Expected revision $baseRevisionNo but current revision is $currentRevisionNo",
            )
        }
    }

    private fun resolveSelectedText(block: ResumeEditorBlockDto, selectedText: String?): String =
        selectedText?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: block.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected text is required for this block")

    private fun buildQuestionSuggestions(selectedText: String): List<ResumeEditorSuggestedQuestionDto> {
        val normalized = selectedText.lowercase()
        val suggestions = mutableListOf<ResumeEditorSuggestedQuestionDto>()
        if (numericRegex.containsMatchIn(selectedText)) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "수치 검증",
                questionText = "이 수치는 어떤 기준과 기간으로 측정했나요?",
                questionType = "verification",
                rationale = "정량 성과가 들어간 문장은 측정 기준과 비교 대상이 바로 이어져야 신뢰도가 올라갑니다.",
                followUpSuggestions = listOf("베이스라인은 무엇이었나요?", "실서비스 지표와 어떻게 연결됐나요?"),
            )
        }
        if (normalized.contains("redis") || normalized.contains("cache")) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "캐시 전략",
                questionText = "캐시 무효화와 정합성 문제는 어떻게 설계했나요?",
                questionType = "technical_deep_dive",
                rationale = "캐시 개선 문장은 invalidation, consistency, hit-rate 설명이 없으면 꼬리질문이 붙기 쉽습니다.",
                followUpSuggestions = listOf("장애 시 fallback은 어떻게 했나요?", "TTL은 어떤 기준으로 정했나요?"),
            )
        }
        if (normalized.contains("pipeline") || normalized.contains("workflow") || normalized.contains("queue")) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "처리 흐름 안정성",
                questionText = "중간 단계 실패가 발생했을 때 복구와 재처리는 어떻게 보장했나요?",
                questionType = "technical_deep_dive",
                rationale = "파이프라인/워크플로우 문장은 실패 복구, idempotency, 상태 전이 질문으로 이어지기 쉽습니다.",
                followUpSuggestions = listOf("중복 처리 방지는 어떻게 했나요?", "운영 모니터링 포인트는 무엇이었나요?"),
            )
        }
        if (normalized.contains("api") || normalized.contains("backend") || normalized.contains("spring")) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "설계 트레이드오프",
                questionText = "이 구조를 선택할 때 포기한 대안은 무엇이었나요?",
                questionType = "tradeoff",
                rationale = "백엔드 설계 문장은 선택 이유와 대안 비교가 없으면 깊이 검증 질문이 붙습니다.",
                followUpSuggestions = listOf("병목은 어디였나요?", "관측 지표는 어떻게 잡았나요?"),
            )
        }
        if (normalized.contains("개선") || normalized.contains("구축") || normalized.contains("설계") || normalized.contains("주도")) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "본인 기여도",
                questionText = "이 작업에서 본인이 직접 결정하고 책임진 범위는 어디까지였나요?",
                questionType = "ownership",
                rationale = "개선/구축/설계 문장은 ownership을 분리해서 말하지 않으면 공헌도 질문이 뒤따릅니다.",
                followUpSuggestions = listOf("협업자는 어떤 역할이었나요?", "의사결정에서 가장 어려웠던 지점은 무엇이었나요?"),
            )
        }
        if (suggestions.isEmpty()) {
            suggestions += ResumeEditorSuggestedQuestionDto(
                title = "핵심 설명 보강",
                questionText = "이 문장을 면접에서 1분 안에 설명한다면 어떤 구조로 답변하시겠어요?",
                questionType = "storytelling",
                rationale = "선택 문장에 특수 패턴이 없을 때는 구조화된 설명 능력을 검증하는 질문이 기본값으로 유효합니다.",
                followUpSuggestions = listOf("문제 상황은 무엇이었나요?", "결과를 어떻게 확인했나요?"),
            )
        }
        return suggestions.distinctBy { it.questionText }
    }

    private fun buildRewriteSuggestions(selectedText: String): List<ResumeEditorRewriteSuggestionDto> {
        val hasNumber = numericRegex.containsMatchIn(selectedText)
        val suggestions = mutableListOf<ResumeEditorRewriteSuggestionDto>()
        if (!hasNumber) {
            suggestions += ResumeEditorRewriteSuggestionDto(
                suggestedText = selectedText.replace("개선", "개선하고 지표로 검증").replace("담당", "주도"),
                rationale = "정량 근거가 없는 문장은 결과 검증과 본인 기여도가 보이도록 강화하는 편이 안전합니다.",
                focusArea = "quantification",
                partialApplyAllowed = true,
            )
        }
        suggestions += ResumeEditorRewriteSuggestionDto(
            suggestedText = buildStructuredRewrite(selectedText, hasNumber),
            rationale = "문제-행동-결과 구조로 다시 쓰면 면접 답변과 PDF 제출본 모두에서 전달력이 좋아집니다.",
            focusArea = "clarity",
            partialApplyAllowed = true,
        )
        if (selectedText.contains("서비스") || selectedText.contains("시스템") || selectedText.contains("플랫폼")) {
            suggestions += ResumeEditorRewriteSuggestionDto(
                suggestedText = "$selectedText 운영 지표와 장애 대응 관점까지 한 문장 덧붙이면 설계 깊이가 더 잘 드러납니다.",
                rationale = "플랫폼/시스템 문장은 운영 안정성까지 보여줘야 실전 질문 방어력이 올라갑니다.",
                focusArea = "operational_depth",
                partialApplyAllowed = true,
            )
        }
        return suggestions.distinctBy { it.suggestedText }
    }

    private fun buildStructuredRewrite(selectedText: String, hasNumber: Boolean): String {
        val base = selectedText.trim().removeSuffix(".")
        return if (hasNumber) {
            "$base, 이 과정에서 문제 원인을 직접 분석하고 설계 결정을 주도해 결과를 재현 가능한 지표로 설명할 수 있도록 정리했습니다."
        } else {
            "$base. 특히 문제 상황, 내가 취한 핵심 조치, 확인된 결과를 한 문장 안에서 드러내도록 다시 쓰는 것을 권장합니다."
        }
    }

    private fun emptyDocument(): ResumeEditorDocumentDto =
        ResumeEditorDocumentDto(
            astVersion = 1,
            markdownSource = null,
            blocks = emptyList(),
            layoutMetadata = emptyMap(),
        )

    private fun createRevision(
        workspace: ResumeEditorWorkspaceEntity,
        userId: Long,
        changeSource: String,
        previous: ResumeEditorDocumentDto,
        current: ResumeEditorDocumentDto,
        createdAt: java.time.Instant,
    ) {
        val summary = buildChangeSummary(previous, current)
        resumeEditorWorkspaceRevisionRepository.save(
            ResumeEditorWorkspaceRevisionEntity(
                resumeEditorWorkspaceId = workspace.id,
                userId = userId,
                resumeVersionId = workspace.resumeVersionId,
                revisionNo = workspace.revisionNo,
                changeSource = changeSource,
                changeSummaryJson = objectMapper.writeValueAsString(summary),
                markdownSource = current.markdownSource,
                documentJson = objectMapper.writeValueAsString(current.copy(markdownSource = null)),
                layoutMetadataJson = encodeMap(current.layoutMetadata),
                createdAt = createdAt,
            ),
        )
    }

    private fun buildChangeSummary(
        previous: ResumeEditorDocumentDto,
        current: ResumeEditorDocumentDto,
    ): ResumeEditorChangeSummaryDto {
        val previousById = previous.blocks.associateBy { it.blockId }
        val currentById = current.blocks.associateBy { it.blockId }
        val added = currentById.keys - previousById.keys
        val removed = previousById.keys - currentById.keys
        val updated = currentById.keys.intersect(previousById.keys).filter { key ->
            currentById[key] != previousById[key]
        }
        val previousMarkCount = previous.blocks.sumOf { it.inlineMarks.size }
        val currentMarkCount = current.blocks.sumOf { it.inlineMarks.size }
        return ResumeEditorChangeSummaryDto(
            addedBlockCount = added.size,
            removedBlockCount = removed.size,
            updatedBlockCount = updated.size,
            inlineMarkDelta = currentMarkCount - previousMarkCount,
            changedBlockIds = (added + removed + updated).sorted(),
        )
    }

    private fun buildTrackedChanges(
        previous: ResumeEditorDocumentDto,
        current: ResumeEditorDocumentDto,
    ): List<ResumeEditorTrackedChangeDto> {
        val previousById = previous.blocks.associateBy { it.blockId }
        val currentById = current.blocks.associateBy { it.blockId }
        val orderedIds = (previous.blocks.map { it.blockId } + current.blocks.map { it.blockId }).distinct()
        return orderedIds.mapNotNull { blockId ->
            val before = previousById[blockId]
            val after = currentById[blockId]
            when {
                before == null && after != null -> ResumeEditorTrackedChangeDto(
                    blockId = blockId,
                    changeType = "added",
                    beforeBlockType = null,
                    afterBlockType = after.blockType,
                    beforeText = null,
                    afterText = blockContent(after),
                    fieldPath = after.fieldPath,
                )
                before != null && after == null -> ResumeEditorTrackedChangeDto(
                    blockId = blockId,
                    changeType = "removed",
                    beforeBlockType = before.blockType,
                    afterBlockType = null,
                    beforeText = blockContent(before),
                    afterText = null,
                    fieldPath = before.fieldPath,
                )
                before != null && after != null && before != after -> ResumeEditorTrackedChangeDto(
                    blockId = blockId,
                    changeType = "updated",
                    beforeBlockType = before.blockType,
                    afterBlockType = after.blockType,
                    beforeText = blockContent(before),
                    afterText = blockContent(after),
                    fieldPath = after.fieldPath ?: before.fieldPath,
                )
                else -> null
            }
        }
    }

    private fun mergeDocuments(
        base: ResumeEditorDocumentDto,
        current: ResumeEditorDocumentDto,
        proposed: ResumeEditorDocumentDto,
    ): MergeResult {
        if (current == base) {
            return MergeResult(proposed, emptyList())
        }
        val baseById = base.blocks.associateBy { it.blockId }
        val currentById = current.blocks.associateBy { it.blockId }
        val proposedById = proposed.blocks.associateBy { it.blockId }
        val orderedIds = (current.blocks.map { it.blockId } + proposed.blocks.map { it.blockId } + base.blocks.map { it.blockId }).distinct()
        val mergedBlocks = mutableListOf<ResumeEditorBlockDto>()
        val conflicts = mutableListOf<ResumeEditorMergeConflictDto>()

        orderedIds.forEach { blockId ->
            val baseBlock = baseById[blockId]
            val currentBlock = currentById[blockId]
            val proposedBlock = proposedById[blockId]
            val mergedBlock = when {
                currentBlock == baseBlock -> proposedBlock
                proposedBlock == baseBlock -> currentBlock
                currentBlock == proposedBlock -> currentBlock
                currentBlock == null && proposedBlock != null && baseBlock == null -> proposedBlock
                proposedBlock == null && currentBlock != null && baseBlock == null -> currentBlock
                else -> {
                    conflicts += ResumeEditorMergeConflictDto(
                        blockId = blockId,
                        conflictType = when {
                            currentBlock == null || proposedBlock == null -> "deletion_conflict"
                            else -> "content_conflict"
                        },
                        baseText = baseBlock?.let(::blockContent),
                        currentText = currentBlock?.let(::blockContent),
                        proposedText = proposedBlock?.let(::blockContent),
                    )
                    currentBlock
                }
            }
            if (mergedBlock != null) {
                mergedBlocks += mergedBlock.copy(displayOrder = mergedBlocks.size)
            }
        }

        return MergeResult(
            document = ResumeEditorDocumentDto(
                astVersion = current.astVersion,
                markdownSource = resumeEditorMarkdownService.render(mergedBlocks),
                blocks = mergedBlocks,
                layoutMetadata = current.layoutMetadata + proposed.layoutMetadata,
            ),
            conflicts = conflicts,
        )
    }

    private fun encodeMap(value: Map<String, String>): String? =
        value.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString)

    private fun decodeMap(raw: String?): Map<String, String> =
        raw?.takeIf { it.isNotBlank() }?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {})
        } ?: emptyMap()

    private fun encodeList(value: List<String>): String? =
        value.map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let(objectMapper::writeValueAsString)

    private fun decodeList(raw: String?): List<String> =
        raw?.takeIf { it.isNotBlank() }?.let {
            objectMapper.readValue(it, object : TypeReference<List<String>>() {})
        } ?: emptyList()

    private fun decodeChangeSummary(raw: String): ResumeEditorChangeSummaryDto =
        objectMapper.readValue(raw, ResumeEditorChangeSummaryDto::class.java)

    private fun ResumeEditorCommentThreadEntity.toDto(replies: List<ResumeEditorCommentReplyDto> = emptyList()): ResumeEditorCommentThreadDto =
        ResumeEditorCommentThreadDto(
            id = id,
            blockId = blockId,
            fieldPath = fieldPath,
            selectionStartOffset = selectionStartOffset,
            selectionEndOffset = selectionEndOffset,
            selectedText = selectedText,
            body = body,
            status = status,
            resolvedAt = resolvedAt,
            replyCount = replies.size,
            replies = replies,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ResumeEditorCommentReplyEntity.toDto(): ResumeEditorCommentReplyDto =
        ResumeEditorCommentReplyDto(
            id = id,
            commentThreadId = resumeEditorCommentThreadId,
            body = body,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ResumeEditorQuestionCardEntity.toDto(): ResumeEditorQuestionCardDto =
        ResumeEditorQuestionCardDto(
            id = id,
            blockId = blockId,
            fieldPath = fieldPath,
            selectionStartOffset = selectionStartOffset,
            selectionEndOffset = selectionEndOffset,
            selectedText = selectedText,
            title = title,
            questionText = questionText,
            questionType = questionType,
            sourceType = sourceType,
            linkedQuestionId = linkedQuestionId,
            status = status,
            followUpSuggestions = decodeList(followUpSuggestionsJson),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ResumeEditorWorkspaceRevisionEntity.toListItemDto(): ResumeEditorRevisionListItemDto =
        ResumeEditorRevisionListItemDto(
            id = id,
            revisionNo = revisionNo,
            changeSource = changeSource,
            changeSummary = decodeChangeSummary(changeSummaryJson),
            createdAt = createdAt,
        )

    private fun ResumeEditorWorkspaceRevisionEntity.toDocument(): ResumeEditorDocumentDto {
        val stored = objectMapper.readValue(documentJson, ResumeEditorDocumentDto::class.java)
        return stored.copy(
            markdownSource = markdownSource ?: stored.markdownSource ?: resumeEditorMarkdownService.render(stored.blocks),
            layoutMetadata = decodeMap(layoutMetadataJson).ifEmpty { stored.layoutMetadata },
        )
    }

    private fun ResumeEditorWorkspaceRevisionEntity.toDetailDto(): ResumeEditorRevisionDto {
        return ResumeEditorRevisionDto(
            id = id,
            workspaceId = resumeEditorWorkspaceId,
            resumeVersionId = resumeVersionId,
            revisionNo = revisionNo,
            changeSource = changeSource,
            changeSummary = decodeChangeSummary(changeSummaryJson),
            document = toDocument(),
            createdAt = createdAt,
        )
    }

    private fun validateCommentStatus(status: String) {
        if (status !in setOf(COMMENT_STATUS_OPEN, COMMENT_STATUS_RESOLVED)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported comment status: $status")
        }
    }

    private fun validateQuestionCardStatus(status: String) {
        if (status !in setOf(QUESTION_CARD_STATUS_ACTIVE, QUESTION_CARD_STATUS_ARCHIVED)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported question card status: $status")
        }
    }

    companion object {
        private val numericRegex = Regex("""\b\d+([.,]\d+)?(%|배|ms|초|분|시간|건|명)?\b""")
        private val supportedViewModes = listOf("edit", "review", "heatmap", "print_preview")
        private const val WORKSPACE_STATUS_DRAFT = "draft"
        private const val CHANGE_SOURCE_BOOTSTRAP = "bootstrap"
        private const val CHANGE_SOURCE_MANUAL_EDIT = "manual_edit"
        private const val CHANGE_SOURCE_MARKDOWN_IMPORT = "markdown_import"
        private const val COMMENT_STATUS_OPEN = "open"
        private const val COMMENT_STATUS_RESOLVED = "resolved"
        private const val QUESTION_CARD_STATUS_ACTIVE = "active"
        private const val QUESTION_CARD_STATUS_ARCHIVED = "archived"
        private const val QUESTION_CARD_SOURCE_MANUAL = "manual"
        private const val PRESENCE_TTL_MINUTES = 10L
    }

    private data class MergeResult(
        val document: ResumeEditorDocumentDto,
        val conflicts: List<ResumeEditorMergeConflictDto>,
    )
}
