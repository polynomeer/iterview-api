package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorCommentReplyDto
import com.example.interviewplatform.resume.dto.ResumeEditorCommentThreadDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentDto
import com.example.interviewplatform.resume.dto.ResumeEditorNodeDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionCardDto
import com.example.interviewplatform.resume.dto.ResumeEditorSelectionAnchorDto
import com.example.interviewplatform.resume.entity.ResumeEditorCommentThreadEntity
import com.example.interviewplatform.resume.entity.ResumeEditorQuestionCardEntity
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ResumeEditorSelectionAnchorService {
    fun resolveSelectionAnchor(
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

    fun toCommentDto(
        entity: ResumeEditorCommentThreadEntity,
        replies: List<ResumeEditorCommentReplyDto>,
    ): ResumeEditorCommentThreadDto =
        ResumeEditorCommentThreadDto(
            id = entity.id,
            blockId = entity.blockId,
            selectionAnchor = ResumeEditorSelectionAnchorDto(
                nodeId = entity.blockId,
                anchorPath = entity.anchorPath ?: entity.blockId,
                fieldPath = entity.fieldPath,
                selectionStartOffset = entity.selectionStartOffset,
                selectionEndOffset = entity.selectionEndOffset,
                selectedText = entity.selectedText,
                anchorQuote = entity.anchorQuote ?: entity.selectedText,
                sentenceIndex = entity.sentenceIndex,
            ),
            fieldPath = entity.fieldPath,
            selectionStartOffset = entity.selectionStartOffset,
            selectionEndOffset = entity.selectionEndOffset,
            selectedText = entity.selectedText,
            body = entity.body,
            status = entity.status,
            resolvedAt = entity.resolvedAt,
            replyCount = replies.size,
            replies = replies,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun toQuestionCardDto(entity: ResumeEditorQuestionCardEntity, followUpSuggestions: List<String>): ResumeEditorQuestionCardDto =
        ResumeEditorQuestionCardDto(
            id = entity.id,
            blockId = entity.blockId,
            selectionAnchor = ResumeEditorSelectionAnchorDto(
                nodeId = entity.blockId,
                anchorPath = entity.anchorPath ?: entity.blockId,
                fieldPath = entity.fieldPath,
                selectionStartOffset = entity.selectionStartOffset,
                selectionEndOffset = entity.selectionEndOffset,
                selectedText = entity.selectedText,
                anchorQuote = entity.anchorQuote ?: entity.selectedText,
                sentenceIndex = entity.sentenceIndex,
            ),
            fieldPath = entity.fieldPath,
            selectionStartOffset = entity.selectionStartOffset,
            selectionEndOffset = entity.selectionEndOffset,
            selectedText = entity.selectedText,
            title = entity.title,
            questionText = entity.questionText,
            questionType = entity.questionType,
            sourceType = entity.sourceType,
            linkedQuestionId = entity.linkedQuestionId,
            status = entity.status,
            followUpSuggestions = followUpSuggestions,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    fun requireNode(document: ResumeEditorDocumentDto, nodeId: String): ResumeEditorNodeDto =
        document.nodes.firstOrNull { it.nodeId == nodeId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resume editor nodeId: $nodeId")

    fun buildAnchorPath(document: ResumeEditorDocumentDto, nodeId: String): String {
        val byId = document.nodes.associateBy { it.nodeId }
        val path = mutableListOf<String>()
        var current: ResumeEditorNodeDto? = byId[nodeId]
        while (current != null) {
            path += current.nodeId
            current = current.parentNodeId?.let(byId::get)
        }
        return path.reversed().joinToString("/")
    }

    fun computeSentenceIndex(text: String, offset: Int): Int {
        if (text.isBlank()) {
            return 0
        }
        val clamped = offset.coerceIn(0, text.length)
        return text.substring(0, clamped).split(sentenceBoundaryRegex).count { it.isNotBlank() }.coerceAtLeast(1) - 1
    }

    fun validateSelection(contentLength: Int, startOffset: Int?, endOffset: Int?) {
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

    companion object {
        private val sentenceBoundaryRegex = Regex("""(?<=[.!?。！？]|다\.)\s+""")
    }
}
