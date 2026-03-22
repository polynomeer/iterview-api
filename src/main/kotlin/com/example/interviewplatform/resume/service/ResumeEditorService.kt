package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.resume.dto.*
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
    private val resumeEditorDocumentModelService: ResumeEditorDocumentModelService,
    private val resumeEditorSelectionAnchorService: ResumeEditorSelectionAnchorService,
    private val resumeEditorDiffService: ResumeEditorDiffService,
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
        val changes = resumeEditorDiffService.buildTrackedChanges(fromRevision.toDocument(), toRevision.toDocument())
        return ResumeEditorTrackedChangesDto(
            resumeVersionId = versionId,
            fromRevisionId = fromRevisionId,
            toRevisionId = toRevisionId,
            changeSummary = resumeEditorDiffService.buildChangeSummary(fromRevision.toDocument(), toRevision.toDocument()),
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
        val currentDocument = decodeDocument(workspace)
        val proposedDocument = resumeEditorDocumentModelService.materializeDocument(
            currentDocument = currentDocument,
            blocks = request.blocks,
            rootNodeId = request.rootNodeId,
            nodes = request.nodes,
            tableOfContents = request.tableOfContents,
            markdownSource = request.markdownSource,
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
            changeSummary = resumeEditorDiffService.buildChangeSummary(currentDocument, mergeResult.document),
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
        val document = resumeEditorDocumentModelService.materializeDocument(
            currentDocument = decodeDocument(existing),
            blocks = request.blocks,
            rootNodeId = request.rootNodeId,
            nodes = request.nodes,
            tableOfContents = request.tableOfContents,
            markdownSource = request.markdownSource,
            layoutMetadata = request.layoutMetadata,
        )
        saveWorkspace(existing, document, userId, request.changeSource?.trim()?.takeIf { it.isNotEmpty() } ?: CHANGE_SOURCE_MANUAL_EDIT)
        return getWorkspace(userId, versionId)
    }

    @Transactional
    fun patchDocumentOperations(
        userId: Long,
        versionId: Long,
        request: PatchResumeEditorDocumentOperationsRequest,
    ): ResumeEditorWorkspaceDto {
        val version = requireOwnedVersion(userId, versionId)
        val existing = getOrCreateWorkspace(userId, version)
        enforceRevision(request.baseRevisionNo, existing.revisionNo)
        val currentDocument = decodeDocument(existing)
        val updatedDocument = resumeEditorDocumentModelService.applyOperations(currentDocument, request.operations)
        val source = request.changeSource?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (!request.clientChangeId.isNullOrBlank()) {
                "operations:${request.clientChangeId.trim()}"
            } else {
                CHANGE_SOURCE_OPERATION_PATCH
            }
        saveWorkspace(existing, updatedDocument, userId, source)
        return getWorkspace(userId, versionId)
    }

    @Transactional
    fun importMarkdown(userId: Long, versionId: Long, request: ImportResumeEditorMarkdownRequest): ResumeEditorWorkspaceDto {
        val version = requireOwnedVersion(userId, versionId)
        val existing = getOrCreateWorkspace(userId, version)
        enforceRevision(request.baseRevisionNo, existing.revisionNo)
        val imported = resumeEditorMarkdownService.parse(request.markdownSource)
        val document = if (request.replaceDocument) {
            resumeEditorDocumentModelService.normalizeDocument(imported)
        } else {
            val current = decodeDocument(existing)
            val appendedBlocks = imported.blocks.mapIndexed { index, block ->
                block.copy(
                    blockId = "${block.blockId}-appended-$index",
                    displayOrder = current.blocks.size + index,
                )
            }
            resumeEditorDocumentModelService.normalizeDocument(
                ResumeEditorDocumentDto(
                    astVersion = 1,
                    markdownSource = listOfNotNull(current.markdownSource, imported.markdownSource).joinToString("\n\n").trim(),
                    blocks = current.blocks + appendedBlocks,
                    layoutMetadata = current.layoutMetadata + imported.layoutMetadata,
                ),
            )
        }
        saveWorkspace(existing, document, userId, request.changeSource?.trim()?.takeIf { it.isNotEmpty() } ?: CHANGE_SOURCE_MARKDOWN_IMPORT)
        return getWorkspace(userId, versionId)
    }

    @Transactional
    fun createComment(userId: Long, versionId: Long, request: CreateResumeEditorCommentRequest): ResumeEditorCommentThreadDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val document = decodeDocument(workspace)
        val selectionAnchor = resumeEditorSelectionAnchorService.resolveSelectionAnchor(
            document = document,
            blockId = request.blockId,
            selectionAnchor = request.selectionAnchor,
            fieldPath = request.fieldPath,
            selectionStartOffset = request.selectionStartOffset,
            selectionEndOffset = request.selectionEndOffset,
            selectedText = request.selectedText,
        )
        val now = clockService.now()
        val saved = resumeEditorCommentThreadRepository.save(
            ResumeEditorCommentThreadEntity(
                userId = userId,
                resumeEditorWorkspaceId = workspace.id,
                resumeVersionId = versionId,
                blockId = selectionAnchor.nodeId,
                fieldPath = selectionAnchor.fieldPath,
                selectionStartOffset = selectionAnchor.selectionStartOffset,
                selectionEndOffset = selectionAnchor.selectionEndOffset,
                selectedText = selectionAnchor.selectedText ?: selectionAnchor.anchorQuote,
                anchorPath = selectionAnchor.anchorPath,
                anchorQuote = selectionAnchor.anchorQuote,
                sentenceIndex = selectionAnchor.sentenceIndex,
                body = request.body.trim(),
                status = COMMENT_STATUS_OPEN,
                resolvedAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        touchWorkspace(workspace)
        return resumeEditorSelectionAnchorService.toCommentDto(saved, emptyList())
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
        return resumeEditorSelectionAnchorService.toCommentDto(comment, replies)
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
                anchorPath = existing.anchorPath,
                anchorQuote = existing.anchorQuote,
                sentenceIndex = existing.sentenceIndex,
                body = request.body?.trim()?.takeIf { it.isNotEmpty() } ?: existing.body,
                status = status,
                resolvedAt = if (status == COMMENT_STATUS_RESOLVED) clockService.now() else null,
                createdAt = existing.createdAt,
                updatedAt = clockService.now(),
            ),
        )
        val replies = resumeEditorCommentReplyRepository.findByResumeEditorCommentThreadIdInOrderByCreatedAtAsc(listOf(updated.id))
            .map { it.toDto() }
        return resumeEditorSelectionAnchorService.toCommentDto(updated, replies)
    }

    @Transactional
    fun createQuestionCard(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorQuestionCardRequest,
    ): ResumeEditorQuestionCardDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val document = decodeDocument(workspace)
        val selectionAnchor = resumeEditorSelectionAnchorService.resolveSelectionAnchor(
            document = document,
            blockId = request.blockId,
            selectionAnchor = request.selectionAnchor,
            fieldPath = request.fieldPath,
            selectionStartOffset = request.selectionStartOffset,
            selectionEndOffset = request.selectionEndOffset,
            selectedText = request.selectedText,
        )
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
                blockId = selectionAnchor.nodeId,
                fieldPath = selectionAnchor.fieldPath,
                selectionStartOffset = selectionAnchor.selectionStartOffset,
                selectionEndOffset = selectionAnchor.selectionEndOffset,
                selectedText = selectionAnchor.selectedText ?: selectionAnchor.anchorQuote,
                anchorPath = selectionAnchor.anchorPath,
                anchorQuote = selectionAnchor.anchorQuote,
                sentenceIndex = selectionAnchor.sentenceIndex,
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
        return resumeEditorSelectionAnchorService.toQuestionCardDto(saved, decodeList(saved.followUpSuggestionsJson))
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
                anchorPath = existing.anchorPath,
                anchorQuote = existing.anchorQuote,
                sentenceIndex = existing.sentenceIndex,
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
        return resumeEditorSelectionAnchorService.toQuestionCardDto(updated, decodeList(updated.followUpSuggestionsJson))
    }

    @Transactional
    fun generateQuestionSuggestions(
        userId: Long,
        versionId: Long,
        request: CreateResumeEditorQuestionSuggestionRequest,
    ): ResumeEditorQuestionSuggestionResponseDto {
        val workspace = getOrCreateWorkspace(userId, requireOwnedVersion(userId, versionId))
        val document = decodeDocument(workspace)
        val selectionAnchor = resumeEditorSelectionAnchorService.resolveSelectionAnchor(
            document = document,
            blockId = request.blockId,
            selectionAnchor = request.selectionAnchor,
            fieldPath = request.fieldPath,
            selectionStartOffset = null,
            selectionEndOffset = null,
            selectedText = request.selectedText,
        )
        val node = resumeEditorSelectionAnchorService.requireNode(document, selectionAnchor.nodeId)
        val selectedText = resolveSelectedText(node, selectionAnchor.selectedText)
        return ResumeEditorQuestionSuggestionResponseDto(
            resumeVersionId = versionId,
            blockId = selectionAnchor.nodeId,
            selectionAnchor = selectionAnchor,
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
        val document = decodeDocument(workspace)
        val selectionAnchor = resumeEditorSelectionAnchorService.resolveSelectionAnchor(
            document = document,
            blockId = request.blockId,
            selectionAnchor = request.selectionAnchor,
            fieldPath = request.fieldPath,
            selectionStartOffset = null,
            selectionEndOffset = null,
            selectedText = request.selectedText,
        )
        val node = resumeEditorSelectionAnchorService.requireNode(document, selectionAnchor.nodeId)
        val selectedText = resolveSelectedText(node, selectionAnchor.selectedText)
        return ResumeEditorRewriteSuggestionResponseDto(
            resumeVersionId = versionId,
            blockId = selectionAnchor.nodeId,
            selectionAnchor = selectionAnchor,
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
            resumeEditorSelectionAnchorService.toCommentDto(entity, repliesByThreadId[entity.id].orEmpty().map { reply -> reply.toDto() })
        }
        val questionCards = resumeEditorQuestionCardRepository.findByResumeEditorWorkspaceIdOrderByCreatedAtAsc(workspace.id)
            .map { resumeEditorSelectionAnchorService.toQuestionCardDto(it, decodeList(it.followUpSuggestionsJson)) }
        val heatmap = resumeQuestionHeatmapService.getHeatmap(userId, version.id, "all")
        return ResumeEditorWorkspaceDto(
            workspaceId = workspace.id,
            resumeVersionId = version.id,
            sourceVersionNo = version.versionNo,
            sourceFileName = version.fileName,
            workspaceStatus = workspace.workspaceStatus,
            revisionNo = workspace.revisionNo,
            documentModel = DOCUMENT_MODEL_RICH_TREE,
            selectionCapabilities = ResumeEditorSelectionCapabilitiesDto(
                supportsRichTree = true,
                supportsOperations = true,
                supportsInlineSelections = true,
                supportsContextualComments = true,
                supportsContextualQuestionCards = true,
                supportsContextualSuggestions = true,
            ),
            contextMenuActions = contextMenuActions,
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
        return resumeEditorDocumentModelService.normalizeDocument(
            ResumeEditorDocumentDto(
                astVersion = 1,
                markdownSource = resumeEditorMarkdownService.render(blocks),
                blocks = blocks,
                layoutMetadata = mapOf(
                    "printProfile" to "a4_resume",
                    "workspaceMode" to "resume_editor",
                ),
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

    private fun validateSelection(contentLength: Int, startOffset: Int?, endOffset: Int?) {
        if ((startOffset == null) != (endOffset == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionStartOffset and selectionEndOffset must be provided together")
        }
        if (startOffset != null && endOffset != null) {
            if (endOffset <= startOffset) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "selectionEndOffset must be greater than selectionStartOffset")
            }
            if (endOffset > contentLength) {
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
        return resumeEditorDocumentModelService.normalizeDocument(stored.copy(
            markdownSource = workspace.markdownSource ?: stored.markdownSource ?: resumeEditorMarkdownService.render(stored.blocks),
            layoutMetadata = decodeMap(workspace.layoutMetadataJson).ifEmpty { stored.layoutMetadata },
        ))
    }

    private fun materializeDocument(
        currentDocument: ResumeEditorDocumentDto,
        blocks: List<ResumeEditorBlockDto>?,
        rootNodeId: String?,
        nodes: List<ResumeEditorNodeDto>?,
        tableOfContents: List<ResumeEditorTableOfContentsItemDto>?,
        markdownSource: String?,
        layoutMetadata: Map<String, String>,
    ): ResumeEditorDocumentDto {
        val effectiveBlocks = when {
            !blocks.isNullOrEmpty() -> blocks
            !nodes.isNullOrEmpty() -> deriveBlocksFromNodes(rootNodeId ?: currentDocument.rootNodeId ?: ROOT_NODE_ID, nodes)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Either blocks or nodes must be provided")
        }
        requireValidBlocks(effectiveBlocks)
        return normalizeDocument(
            ResumeEditorDocumentDto(
                astVersion = currentDocument.astVersion + 1,
                markdownSource = markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(effectiveBlocks),
                blocks = effectiveBlocks,
                rootNodeId = rootNodeId ?: currentDocument.rootNodeId,
                nodes = nodes.orEmpty(),
                tableOfContents = tableOfContents.orEmpty(),
                layoutMetadata = layoutMetadata,
            ),
        )
    }

    private fun normalizeDocument(document: ResumeEditorDocumentDto): ResumeEditorDocumentDto {
        val normalizedBlocks = document.blocks
            .mapIndexed { index, block ->
                block.copy(
                    displayOrder = index,
                    metadata = block.metadata.filterKeys { it.isNotBlank() },
                )
            }
        requireValidBlocks(normalizedBlocks)
        val rootNodeId = document.rootNodeId ?: ROOT_NODE_ID
        val normalizedNodes = when {
            document.nodes.isNotEmpty() -> normalizeNodes(rootNodeId, document.nodes)
            else -> buildNodesFromBlocks(normalizedBlocks, rootNodeId)
        }
        val blocksFromNodes = deriveBlocksFromNodes(rootNodeId, normalizedNodes)
        val finalBlocks = if (normalizedBlocks.isEmpty()) blocksFromNodes else blocksFromNodes
        return ResumeEditorDocumentDto(
            astVersion = document.astVersion.coerceAtLeast(DOCUMENT_AST_VERSION),
            markdownSource = document.markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(finalBlocks),
            blocks = finalBlocks,
            rootNodeId = rootNodeId,
            nodes = normalizedNodes,
            tableOfContents = if (document.tableOfContents.isNotEmpty()) {
                document.tableOfContents
                    .filter { it.nodeId.isNotBlank() }
                    .map { toc ->
                        val node = normalizedNodes.firstOrNull { it.nodeId == toc.nodeId }
                        toc.copy(
                            title = node?.text ?: toc.title,
                            depth = node?.depth ?: toc.depth,
                            fieldPath = node?.fieldPath ?: toc.fieldPath,
                        )
                    }
            } else {
                buildTableOfContents(normalizedNodes)
            },
            layoutMetadata = document.layoutMetadata,
        )
    }

    private fun applyOperations(
        currentDocument: ResumeEditorDocumentDto,
        operations: List<ResumeEditorDocumentOperationDto>,
    ): ResumeEditorDocumentDto {
        var workingBlocks = currentDocument.blocks.map { it.copy(metadata = it.metadata.toMutableMap()) }.toMutableList()
        operations.forEachIndexed { index, operation ->
            val operationType = operation.operationType.trim().lowercase()
            when (operationType) {
                "text_insert" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    if (startOffset > text.length) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "text_insert startOffset exceeds node text length")
                    }
                    val inserted = operation.text.orEmpty()
                    workingBlocks[blockIndex] = rewriteBlockContent(block, text.substring(0, startOffset) + inserted + text.substring(startOffset))
                }

                "text_replace" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    validateSelection(text.length, startOffset, endOffset)
                    workingBlocks[blockIndex] = rewriteBlockContent(
                        block,
                        text.substring(0, startOffset) + operation.text.orEmpty() + text.substring(endOffset),
                    )
                }

                "text_delete" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    validateSelection(text.length, startOffset, endOffset)
                    workingBlocks[blockIndex] = rewriteBlockContent(block, text.removeRange(startOffset, endOffset))
                }

                "block_split" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    if (startOffset !in 0..text.length) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "block_split startOffset exceeds node text length")
                    }
                    val beforeText = text.substring(0, startOffset).trimEnd()
                    val afterText = text.substring(startOffset).trimStart()
                    workingBlocks[blockIndex] = rewriteBlockContent(block, beforeText)
                    val newBlockId = generateOperationNodeId(block.blockId, "split", index)
                    val siblingBlock = rewriteBlockContent(
                        block.copy(
                            blockId = newBlockId,
                            metadata = block.metadata + (METADATA_SPLIT_ORIGIN to block.blockId),
                        ),
                        afterText,
                    )
                    workingBlocks.add(blockIndex + 1, siblingBlock)
                }

                "block_merge" -> {
                    val primaryIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val referenceIndex = operation.referenceNodeId?.let { requireBlockIndex(workingBlocks, it) }
                        ?: (primaryIndex + 1).takeIf { it < workingBlocks.size }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "block_merge requires a following block or referenceNodeId")
                    val firstIndex = minOf(primaryIndex, referenceIndex)
                    val secondIndex = maxOf(primaryIndex, referenceIndex)
                    val primary = workingBlocks[firstIndex]
                    val secondary = workingBlocks[secondIndex]
                    val mergedText = listOf(blockContent(primary), blockContent(secondary))
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    workingBlocks[firstIndex] = rewriteBlockContent(primary, mergedText)
                    workingBlocks.removeAt(secondIndex)
                }

                "block_move" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks.removeAt(blockIndex)
                    val targetIndex = operation.referenceNodeId?.let { refId ->
                        requireBlockIndex(workingBlocks, refId)
                    } ?: workingBlocks.size
                    val updatedBlock = if (!operation.parentNodeId.isNullOrBlank()) {
                        block.copy(metadata = block.metadata + (METADATA_PARENT_NODE_ID to operation.parentNodeId.trim()))
                    } else {
                        block
                    }
                    workingBlocks.add(targetIndex.coerceIn(0, workingBlocks.size), updatedBlock)
                }

                "block_duplicate" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val source = workingBlocks[blockIndex]
                    val duplicate = source.copy(
                        blockId = generateOperationNodeId(source.blockId, "copy", index),
                        metadata = source.metadata + (METADATA_DUPLICATED_FROM to source.blockId),
                    )
                    workingBlocks.add(blockIndex + 1, duplicate)
                }

                "block_remove" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    workingBlocks.removeAt(blockIndex)
                }

                "block_type_change" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val nodeType = operation.nodeType?.trim()?.takeIf { it.isNotEmpty() }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "block_type_change requires nodeType")
                    workingBlocks[blockIndex] = workingBlocks[blockIndex].copy(blockType = nodeType)
                }

                "indent" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val current = workingBlocks[blockIndex]
                    val previous = workingBlocks.getOrNull(blockIndex - 1)
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "indent requires a previous node")
                    val previousDepth = previous.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1
                    workingBlocks[blockIndex] = current.copy(
                        metadata = current.metadata + mapOf(
                            METADATA_PARENT_NODE_ID to previous.blockId,
                            METADATA_DEPTH to (previousDepth + 1).toString(),
                        ),
                    )
                }

                "outdent" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val current = workingBlocks[blockIndex]
                    val currentDepth = current.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1
                    val parentNodeId = current.metadata[METADATA_PARENT_NODE_ID]
                    val parentParentNodeId = parentNodeId?.let { parentId ->
                        workingBlocks.firstOrNull { it.blockId == parentId }?.metadata?.get(METADATA_PARENT_NODE_ID)
                    }
                    val updatedMetadata = current.metadata
                        .minus(METADATA_PARENT_NODE_ID)
                        .plus(
                            mapOf(
                                METADATA_DEPTH to (currentDepth - 1).coerceAtLeast(1).toString(),
                            ),
                        )
                        .let { metadata ->
                            if (parentParentNodeId.isNullOrBlank()) {
                                metadata - METADATA_PARENT_NODE_ID
                            } else {
                                metadata + (METADATA_PARENT_NODE_ID to parentParentNodeId)
                            }
                        }
                    workingBlocks[blockIndex] = current.copy(metadata = updatedMetadata)
                }

                "inline_mark_add" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    val markType = operation.markType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "inline_mark_add requires markType")
                    validateSelection(text.length, startOffset, endOffset)
                    val newMark = ResumeEditorInlineMarkDto(
                        markType = markType,
                        startOffset = startOffset,
                        endOffset = endOffset,
                        text = text.substring(startOffset, endOffset),
                        href = operation.href?.trim()?.takeIf { it.isNotEmpty() },
                    )
                    val marks = (block.inlineMarks + newMark)
                        .distinctBy { Triple(it.markType, it.startOffset, it.endOffset) to it.href }
                        .sortedWith(compareBy({ it.startOffset }, { it.endOffset }, { it.markType }))
                    workingBlocks[blockIndex] = block.copy(inlineMarks = marks)
                }

                "inline_mark_remove" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    val markType = operation.markType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                    workingBlocks[blockIndex] = block.copy(
                        inlineMarks = block.inlineMarks.filterNot { mark ->
                            (markType == null || mark.markType == markType) &&
                                mark.startOffset == startOffset &&
                                mark.endOffset == endOffset
                        },
                    )
                }

                "collapse_toggle" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val currentCollapsed = block.metadata[METADATA_COLLAPSED] == "true"
                    workingBlocks[blockIndex] = block.copy(
                        metadata = block.metadata + (METADATA_COLLAPSED to (operation.collapsed ?: !currentCollapsed).toString()),
                    )
                }

                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported editor operationType: ${operation.operationType}")
            }
        }

        return normalizeDocument(
            ResumeEditorDocumentDto(
                astVersion = currentDocument.astVersion + 1,
                markdownSource = resumeEditorMarkdownService.render(workingBlocks),
                blocks = workingBlocks.mapIndexed { index, block -> block.copy(displayOrder = index) },
                rootNodeId = currentDocument.rootNodeId,
                nodes = emptyList(),
                tableOfContents = emptyList(),
                layoutMetadata = currentDocument.layoutMetadata,
            ),
        )
    }

    private fun normalizeNodes(rootNodeId: String, nodes: List<ResumeEditorNodeDto>): List<ResumeEditorNodeDto> {
        val baseNodes = nodes.filter { it.nodeId.isNotBlank() }.associateBy { it.nodeId }.toMutableMap()
        if (rootNodeId !in baseNodes) {
            baseNodes[rootNodeId] = ResumeEditorNodeDto(
                nodeId = rootNodeId,
                parentNodeId = null,
                nodeType = NODE_TYPE_ROOT,
                text = null,
                textRuns = emptyList(),
                children = emptyList(),
                collapsed = false,
                depth = 0,
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = null,
                displayOrder = -1,
                metadata = emptyMap(),
            )
        }
        val nonRootNodes = baseNodes.values.filter { it.nodeId != rootNodeId }.sortedWith(
            compareBy<ResumeEditorNodeDto>({ it.depth }, { it.displayOrder }, { it.nodeId }),
        )
        val normalized = mutableListOf<ResumeEditorNodeDto>()
        val childrenByParentId = linkedMapOf<String, MutableList<ResumeEditorNodeDto>>()
        nonRootNodes.forEach { node ->
            val parentId = node.parentNodeId?.takeIf { it in baseNodes && it != node.nodeId } ?: rootNodeId
            childrenByParentId.computeIfAbsent(parentId) { mutableListOf() }.add(node.copy(parentNodeId = parentId))
        }
        normalized += baseNodes.getValue(rootNodeId).copy(
            parentNodeId = null,
            nodeType = NODE_TYPE_ROOT,
            text = null,
            textRuns = emptyList(),
            children = childrenByParentId[rootNodeId].orEmpty().sortedBy { it.displayOrder }.map { it.nodeId },
            collapsed = false,
            depth = 0,
            displayOrder = -1,
            metadata = emptyMap(),
        )
        fun visit(parentId: String, depth: Int) {
            childrenByParentId[parentId].orEmpty()
                .sortedBy { it.displayOrder }
                .forEachIndexed { index, child ->
                    val normalizedChild = child.copy(
                        parentNodeId = parentId,
                        depth = depth,
                        displayOrder = index,
                        children = childrenByParentId[child.nodeId].orEmpty().sortedBy { it.displayOrder }.map { it.nodeId },
                    )
                    normalized += normalizedChild
                    visit(child.nodeId, depth + 1)
                }
        }
        visit(rootNodeId, 1)
        return normalized
    }

    private fun buildNodesFromBlocks(blocks: List<ResumeEditorBlockDto>, rootNodeId: String): List<ResumeEditorNodeDto> {
        val nodes = mutableListOf(
            ResumeEditorNodeDto(
                nodeId = rootNodeId,
                parentNodeId = null,
                nodeType = NODE_TYPE_ROOT,
                text = null,
                textRuns = emptyList(),
                children = emptyList(),
                collapsed = false,
                depth = 0,
                sourceAnchorType = null,
                sourceAnchorRecordId = null,
                sourceAnchorKey = null,
                fieldPath = null,
                displayOrder = -1,
                metadata = emptyMap(),
            ),
        )
        blocks.forEach { block ->
            val text = when {
                block.blockType in titleOnlyBlockTypes -> block.title
                block.lines.isNotEmpty() -> block.lines.joinToString("\n")
                else -> block.text ?: block.title
            }
            nodes += ResumeEditorNodeDto(
                nodeId = block.blockId,
                parentNodeId = block.metadata[METADATA_PARENT_NODE_ID]?.takeIf { it.isNotBlank() } ?: rootNodeId,
                nodeType = block.blockType,
                text = text,
                textRuns = buildTextRuns(text.orEmpty(), block.inlineMarks),
                children = emptyList(),
                collapsed = block.metadata[METADATA_COLLAPSED] == "true",
                depth = block.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1,
                sourceAnchorType = block.sourceAnchorType,
                sourceAnchorRecordId = block.sourceAnchorRecordId,
                sourceAnchorKey = block.sourceAnchorKey,
                fieldPath = block.fieldPath,
                displayOrder = block.displayOrder,
                metadata = block.metadata - setOf(METADATA_PARENT_NODE_ID, METADATA_COLLAPSED, METADATA_DEPTH),
            )
        }
        return normalizeNodes(rootNodeId, nodes)
    }

    private fun deriveBlocksFromNodes(rootNodeId: String, nodes: List<ResumeEditorNodeDto>): List<ResumeEditorBlockDto> {
        val normalizedNodes = normalizeNodes(rootNodeId, nodes)
        return normalizedNodes
            .filter { it.nodeId != rootNodeId }
            .mapIndexed { index, node ->
                val (text, lines, title) = when {
                    node.nodeType in titleOnlyBlockTypes -> Triple(null, emptyList(), node.text)
                    node.nodeType in lineBasedBlockTypes -> Triple(null, node.text?.split('\n')?.filter { it.isNotBlank() }.orEmpty(), null)
                    else -> Triple(node.text, emptyList(), null)
                }
                ResumeEditorBlockDto(
                    blockId = node.nodeId,
                    blockType = node.nodeType,
                    title = title,
                    text = text,
                    lines = lines,
                    sourceAnchorType = node.sourceAnchorType,
                    sourceAnchorRecordId = node.sourceAnchorRecordId,
                    sourceAnchorKey = node.sourceAnchorKey,
                    fieldPath = node.fieldPath,
                    displayOrder = index,
                    metadata = node.metadata + mapOf(
                        METADATA_PARENT_NODE_ID to (node.parentNodeId ?: rootNodeId),
                        METADATA_COLLAPSED to node.collapsed.toString(),
                        METADATA_DEPTH to node.depth.toString(),
                    ),
                    inlineMarks = buildInlineMarks(node.textRuns),
                )
            }
    }

    private fun buildTableOfContents(nodes: List<ResumeEditorNodeDto>): List<ResumeEditorTableOfContentsItemDto> =
        nodes.filter { it.nodeId != ROOT_NODE_ID && it.nodeType in tableOfContentsNodeTypes && !it.text.isNullOrBlank() }
            .map {
                ResumeEditorTableOfContentsItemDto(
                    nodeId = it.nodeId,
                    title = it.text.orEmpty(),
                    depth = it.depth,
                    fieldPath = it.fieldPath,
                )
            }

    private fun buildTextRuns(text: String, inlineMarks: List<ResumeEditorInlineMarkDto>): List<ResumeEditorTextRunDto> {
        if (text.isEmpty()) {
            return emptyList()
        }
        if (inlineMarks.isEmpty()) {
            return listOf(ResumeEditorTextRunDto(text = text))
        }
        val boundaries = mutableSetOf(0, text.length)
        inlineMarks.forEach {
            boundaries += it.startOffset.coerceIn(0, text.length)
            boundaries += it.endOffset.coerceIn(0, text.length)
        }
        val sortedBoundaries = boundaries.sorted()
        return sortedBoundaries.zipWithNext()
            .mapNotNull { (start, end) ->
                if (end <= start) {
                    null
                } else {
                    val activeMarks = inlineMarks.filter { start >= it.startOffset && end <= it.endOffset }
                    ResumeEditorTextRunDto(
                        text = text.substring(start, end),
                        marks = activeMarks.map { it.markType }.distinct(),
                        href = activeMarks.firstOrNull { it.markType == "link" }?.href,
                    )
                }
            }
    }

    private fun buildInlineMarks(textRuns: List<ResumeEditorTextRunDto>): List<ResumeEditorInlineMarkDto> {
        val marks = mutableListOf<ResumeEditorInlineMarkDto>()
        var offset = 0
        val activeMarkRanges = linkedMapOf<Pair<String, String?>, Pair<Int, String>>()
        textRuns.forEach { run ->
            val nextOffset = offset + run.text.length
            val currentKeys = run.marks.map { it to if (it == "link") run.href else null }.toSet()
            val expiredKeys = activeMarkRanges.keys - currentKeys
            expiredKeys.forEach expiredLoop@{ key ->
                val removedRange = activeMarkRanges.remove(key) ?: return@expiredLoop
                val (startOffset, text) = removedRange
                marks += ResumeEditorInlineMarkDto(
                    markType = key.first,
                    startOffset = startOffset,
                    endOffset = offset,
                    text = text,
                    href = key.second,
                )
            }
            run.marks.forEach { markType ->
                val key = markType to if (markType == "link") run.href else null
                val existing = activeMarkRanges[key]
                if (existing == null) {
                    activeMarkRanges[key] = offset to run.text
                } else {
                    activeMarkRanges[key] = existing.first to (existing.second + run.text)
                }
            }
            offset = nextOffset
        }
        activeMarkRanges.forEach { (key, value) ->
            marks += ResumeEditorInlineMarkDto(
                markType = key.first,
                startOffset = value.first,
                endOffset = offset,
                text = value.second,
                href = key.second,
            )
        }
        return marks.sortedWith(compareBy({ it.startOffset }, { it.endOffset }, { it.markType }))
    }

    private fun requireNode(document: ResumeEditorDocumentDto, nodeId: String): ResumeEditorNodeDto =
        document.nodes.firstOrNull { it.nodeId == nodeId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resume editor nodeId: $nodeId")

    private fun resolveSelectionAnchor(
        document: ResumeEditorDocumentDto,
        blockId: String?,
        selectionAnchor: ResumeEditorSelectionAnchorDto?,
        fieldPath: String?,
        selectionStartOffset: Int?,
        selectionEndOffset: Int?,
        selectedText: String?,
    ): ResumeEditorSelectionAnchorDto {
        val nodeId = selectionAnchor?.nodeId?.trim()?.takeIf { it.isNotEmpty() }
            ?: blockId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Either blockId or selectionAnchor.nodeId is required")
        val node = requireNode(document, nodeId)
        val text = node.text.orEmpty()
        val start = selectionAnchor?.selectionStartOffset ?: selectionStartOffset
        val end = selectionAnchor?.selectionEndOffset ?: selectionEndOffset
        validateSelection(text.length, start, end)
        val resolvedSelectedText = selectedText?.trim()?.takeIf { it.isNotEmpty() }
            ?: selectionAnchor?.selectedText?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (start != null && end != null && end <= text.length) text.substring(start, end) else null
        return ResumeEditorSelectionAnchorDto(
            nodeId = nodeId,
            anchorPath = selectionAnchor?.anchorPath ?: buildAnchorPath(document, nodeId),
            fieldPath = fieldPath?.trim()?.takeIf { it.isNotEmpty() } ?: selectionAnchor?.fieldPath ?: node.fieldPath,
            selectionStartOffset = start,
            selectionEndOffset = end,
            selectedText = resolvedSelectedText,
            anchorQuote = selectionAnchor?.anchorQuote ?: resolvedSelectedText,
            sentenceIndex = selectionAnchor?.sentenceIndex ?: start?.let { computeSentenceIndex(text, it) },
        )
    }

    private fun buildAnchorPath(document: ResumeEditorDocumentDto, nodeId: String): String {
        val byId = document.nodes.associateBy { it.nodeId }
        val path = mutableListOf<String>()
        var current: ResumeEditorNodeDto? = byId[nodeId]
        while (current != null) {
            path += current.nodeId
            current = current.parentNodeId?.let(byId::get)
        }
        return path.reversed().joinToString("/")
    }

    private fun computeSentenceIndex(text: String, offset: Int): Int {
        if (text.isBlank()) {
            return 0
        }
        val clamped = offset.coerceIn(0, text.length)
        return text.substring(0, clamped).split(sentenceBoundaryRegex).count { it.isNotBlank() }.coerceAtLeast(1) - 1
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

    private fun resolveSelectedText(node: ResumeEditorNodeDto, selectedText: String?): String =
        selectedText?.trim()?.takeIf { it.isNotEmpty() }
            ?: node.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected text is required for this node")

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
        resumeEditorDocumentModelService.emptyDocument()

    private fun createRevision(
        workspace: ResumeEditorWorkspaceEntity,
        userId: Long,
        changeSource: String,
        previous: ResumeEditorDocumentDto,
        current: ResumeEditorDocumentDto,
        createdAt: java.time.Instant,
    ) {
        val summary = resumeEditorDiffService.buildChangeSummary(previous, current)
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
        val previousById = previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val currentById = current.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val added = currentById.keys - previousById.keys
        val removed = previousById.keys - currentById.keys
        val updated = currentById.keys.intersect(previousById.keys).filter { key ->
            previousById[key] != currentById[key]
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
        val previousById = previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val currentById = current.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val orderedIds = (previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId } +
            current.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId }).distinct()
        return orderedIds.mapNotNull { nodeId ->
            val before = previousById[nodeId]
            val after = currentById[nodeId]
            when {
                before == null && after != null -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = "added",
                    beforeBlockType = null,
                    afterBlockType = after.nodeType,
                    beforeText = null,
                    afterText = after.text,
                    fieldPath = after.fieldPath,
                    beforeParentNodeId = null,
                    afterParentNodeId = after.parentNodeId,
                    beforeDepth = null,
                    afterDepth = after.depth,
                    textChanged = true,
                    structureChanged = true,
                    moveRelated = false,
                )
                before != null && after == null -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = "removed",
                    beforeBlockType = before.nodeType,
                    afterBlockType = null,
                    beforeText = before.text,
                    afterText = null,
                    fieldPath = before.fieldPath,
                    beforeParentNodeId = before.parentNodeId,
                    afterParentNodeId = null,
                    beforeDepth = before.depth,
                    afterDepth = null,
                    textChanged = true,
                    structureChanged = true,
                    moveRelated = false,
                )
                before != null && after != null && before != after -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = when {
                        before.parentNodeId != after.parentNodeId || before.depth != after.depth -> "moved"
                        before.text != after.text -> "text_updated"
                        else -> "structure_updated"
                    },
                    beforeBlockType = before.nodeType,
                    afterBlockType = after.nodeType,
                    beforeText = before.text,
                    afterText = after.text,
                    fieldPath = after.fieldPath ?: before.fieldPath,
                    beforeParentNodeId = before.parentNodeId,
                    afterParentNodeId = after.parentNodeId,
                    beforeDepth = before.depth,
                    afterDepth = after.depth,
                    textChanged = before.text != after.text,
                    structureChanged = before.nodeType != after.nodeType ||
                        before.parentNodeId != after.parentNodeId ||
                        before.depth != after.depth ||
                        before.collapsed != after.collapsed,
                    moveRelated = before.parentNodeId != after.parentNodeId || before.depth != after.depth,
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
        val rootNodeId = current.rootNodeId ?: base.rootNodeId ?: proposed.rootNodeId ?: ROOT_NODE_ID
        val baseById = base.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val currentById = current.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val proposedById = proposed.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val orderedIds = (current.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId } +
            proposed.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId } +
            base.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId }).distinct()
        val mergedNodes = mutableListOf<ResumeEditorNodeDto>()
        val conflicts = mutableListOf<ResumeEditorMergeConflictDto>()

        orderedIds.forEach { nodeId ->
            val baseNode = baseById[nodeId]
            val currentNode = currentById[nodeId]
            val proposedNode = proposedById[nodeId]
            val mergedNode = when {
                currentNode == baseNode -> proposedNode
                proposedNode == baseNode -> currentNode
                currentNode == proposedNode -> currentNode
                currentNode == null && proposedNode != null && baseNode == null -> proposedNode
                proposedNode == null && currentNode != null && baseNode == null -> currentNode
                else -> {
                    conflicts += resumeEditorDiffService.buildConflict(nodeId, baseNode, currentNode, proposedNode)
                    currentNode
                }
            }
            if (mergedNode != null) {
                mergedNodes += mergedNode.copy(displayOrder = mergedNodes.size)
            }
        }

        return MergeResult(
            document = resumeEditorDocumentModelService.normalizeDocument(
                ResumeEditorDocumentDto(
                    astVersion = current.astVersion,
                    markdownSource = current.markdownSource ?: proposed.markdownSource,
                    blocks = emptyList(),
                    rootNodeId = rootNodeId,
                    nodes = listOf(
                        ResumeEditorNodeDto(
                            nodeId = rootNodeId,
                            parentNodeId = null,
                            nodeType = NODE_TYPE_ROOT,
                            text = null,
                            children = emptyList(),
                            collapsed = false,
                            depth = 0,
                            sourceAnchorType = null,
                            sourceAnchorRecordId = null,
                            sourceAnchorKey = null,
                            fieldPath = null,
                            displayOrder = -1,
                            metadata = emptyMap(),
                        ),
                    ) + mergedNodes,
                    tableOfContents = emptyList(),
                    layoutMetadata = current.layoutMetadata + proposed.layoutMetadata,
                ),
            ),
            conflicts = conflicts,
        )
    }

    private fun requireBlockIndex(blocks: List<ResumeEditorBlockDto>, nodeId: String?): Int {
        val normalizedNodeId = nodeId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "nodeId is required for this editor operation")
        return blocks.indexOfFirst { it.blockId == normalizedNodeId }
            .takeIf { it >= 0 }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resume editor nodeId: $normalizedNodeId")
    }

    private fun requireOffset(value: Int?, fieldName: String): Int =
        value ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName is required for this editor operation")

    private fun rewriteBlockContent(block: ResumeEditorBlockDto, newContent: String): ResumeEditorBlockDto =
        when (block.blockType) {
            in titleOnlyBlockTypes -> block.copy(title = newContent, text = null, lines = emptyList())
            in lineBasedBlockTypes -> block.copy(title = null, text = null, lines = splitContentLines(newContent))
            else -> block.copy(text = newContent, lines = emptyList())
        }

    private fun splitContentLines(text: String): List<String> =
        text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }

    private fun generateOperationNodeId(baseNodeId: String, suffix: String, index: Int): String =
        "${baseNodeId}-${suffix}-${index + 1}"

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
            selectionAnchor = ResumeEditorSelectionAnchorDto(
                nodeId = blockId,
                anchorPath = blockId,
                fieldPath = fieldPath,
                selectionStartOffset = selectionStartOffset,
                selectionEndOffset = selectionEndOffset,
                selectedText = selectedText,
                anchorQuote = selectedText,
                sentenceIndex = selectionStartOffset?.let { computeSentenceIndex(selectedText ?: "", it.coerceAtMost((selectedText ?: "").length)) } ?: 0,
            ),
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
            selectionAnchor = ResumeEditorSelectionAnchorDto(
                nodeId = blockId,
                anchorPath = blockId,
                fieldPath = fieldPath,
                selectionStartOffset = selectionStartOffset,
                selectionEndOffset = selectionEndOffset,
                selectedText = selectedText,
                anchorQuote = selectedText,
                sentenceIndex = selectionStartOffset?.let { computeSentenceIndex(selectedText ?: "", it.coerceAtMost((selectedText ?: "").length)) } ?: 0,
            ),
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
        return resumeEditorDocumentModelService.normalizeDocument(stored.copy(
            markdownSource = markdownSource ?: stored.markdownSource ?: resumeEditorMarkdownService.render(stored.blocks),
            layoutMetadata = decodeMap(layoutMetadataJson).ifEmpty { stored.layoutMetadata },
        ))
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
        private val sentenceBoundaryRegex = Regex("""(?<=[.!?。！？]|다\.)\s+""")
        private val supportedViewModes = listOf("edit", "review", "heatmap", "print_preview")
        private val contextMenuActions = listOf(
            "add_comment",
            "add_question_card",
            "generate_questions",
            "generate_rewrite",
            "toggle_collapse",
            "copy_markdown",
        )
        private val titleOnlyBlockTypes = setOf("header", "section_heading")
        private val lineBasedBlockTypes = setOf("bullet_item", "skills_group", "contact")
        private val tableOfContentsNodeTypes = setOf("header", "section_heading")
        private const val DOCUMENT_MODEL_RICH_TREE = "rich_tree"
        private const val DOCUMENT_AST_VERSION = 2
        private const val ROOT_NODE_ID = "root"
        private const val NODE_TYPE_ROOT = "root"
        private const val CHANGE_SOURCE_OPERATION_PATCH = "operation_patch"
        private const val METADATA_PARENT_NODE_ID = "parentNodeId"
        private const val METADATA_COLLAPSED = "collapsed"
        private const val METADATA_DEPTH = "depth"
        private const val METADATA_SPLIT_ORIGIN = "splitOrigin"
        private const val METADATA_DUPLICATED_FROM = "duplicatedFrom"
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
