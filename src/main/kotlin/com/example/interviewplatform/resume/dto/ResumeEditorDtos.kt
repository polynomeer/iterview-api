package com.example.interviewplatform.resume.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.Instant

data class ResumeEditorWorkspaceDto(
    val workspaceId: Long,
    val resumeVersionId: Long,
    val sourceVersionNo: Int,
    val sourceFileName: String?,
    val workspaceStatus: String,
    val revisionNo: Int,
    val documentModel: String,
    val selectionCapabilities: ResumeEditorSelectionCapabilitiesDto,
    val contextMenuActions: List<String>,
    val supportedViewModes: List<String>,
    val document: ResumeEditorDocumentDto,
    val comments: List<ResumeEditorCommentThreadDto>,
    val questionCards: List<ResumeEditorQuestionCardDto>,
    val commentSummary: ResumeEditorCommentSummaryDto,
    val questionCardSummary: ResumeEditorQuestionCardSummaryDto,
    val heatmapAvailable: Boolean,
    val heatmapSummary: ResumeQuestionHeatmapSummaryDto?,
    val activePresence: List<ResumeEditorPresenceDto>,
    val latestRevision: ResumeEditorRevisionListItemDto?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorDocumentDto(
    val astVersion: Int,
    val markdownSource: String?,
    val blocks: List<ResumeEditorBlockDto>,
    val rootNodeId: String? = null,
    val nodes: List<ResumeEditorNodeDto> = emptyList(),
    val tableOfContents: List<ResumeEditorTableOfContentsItemDto> = emptyList(),
    val layoutMetadata: Map<String, String>,
)

data class ResumeEditorSelectionCapabilitiesDto(
    val supportsRichTree: Boolean,
    val supportsOperations: Boolean,
    val supportsInlineSelections: Boolean,
    val supportsContextualComments: Boolean,
    val supportsContextualQuestionCards: Boolean,
    val supportsContextualSuggestions: Boolean,
)

data class ResumeEditorTableOfContentsItemDto(
    val nodeId: String,
    val title: String,
    val depth: Int,
    val fieldPath: String?,
)

data class ResumeEditorTextRunDto(
    val text: String,
    val marks: List<String> = emptyList(),
    val href: String? = null,
)

data class ResumeEditorNodeDto(
    val nodeId: String,
    val parentNodeId: String?,
    val nodeType: String,
    val text: String?,
    val textRuns: List<ResumeEditorTextRunDto> = emptyList(),
    val children: List<String> = emptyList(),
    val collapsed: Boolean = false,
    val depth: Int,
    val sourceAnchorType: String?,
    val sourceAnchorRecordId: Long?,
    val sourceAnchorKey: String?,
    val fieldPath: String?,
    val displayOrder: Int,
    val metadata: Map<String, String> = emptyMap(),
)

data class ResumeEditorInlineMarkDto(
    val markType: String,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val href: String? = null,
)

data class ResumeEditorBlockDto(
    val blockId: String,
    val blockType: String,
    val title: String?,
    val text: String?,
    val lines: List<String>,
    val sourceAnchorType: String?,
    val sourceAnchorRecordId: Long?,
    val sourceAnchorKey: String?,
    val fieldPath: String?,
    val displayOrder: Int,
    val metadata: Map<String, String>,
    val inlineMarks: List<ResumeEditorInlineMarkDto> = emptyList(),
)

data class ResumeEditorPrintPreviewDto(
    val resumeVersionId: Long,
    val workspaceId: Long,
    val title: String,
    val pageEstimate: Int,
    val plainText: String,
    val sections: List<ResumeEditorPrintPreviewSectionDto>,
    val pages: List<ResumeEditorPrintPreviewPageDto>,
    val layoutItems: List<ResumeEditorPrintLayoutItemDto>,
)

data class ResumeEditorPrintPreviewSectionDto(
    val sectionKey: String,
    val title: String,
    val lines: List<String>,
)

data class ResumeEditorPrintPreviewPageDto(
    val pageNumber: Int,
    val sectionKeys: List<String>,
    val lineCount: Int,
)

data class ResumeEditorPrintLayoutItemDto(
    val pageNumber: Int,
    val sectionKey: String,
    val blockId: String,
    val blockType: String,
    val yOffsetLines: Int,
    val estimatedLineSpan: Int,
)

data class ResumeEditorPresenceDto(
    val sessionKey: String,
    val userId: Long,
    val userLabel: String,
    val viewMode: String?,
    val selectedBlockId: String?,
    val isCurrentUser: Boolean,
    val updatedAt: Instant,
)

data class ResumeEditorChangeSummaryDto(
    val addedBlockCount: Int,
    val removedBlockCount: Int,
    val updatedBlockCount: Int,
    val inlineMarkDelta: Int,
    val changedBlockIds: List<String>,
)

data class ResumeEditorRevisionListItemDto(
    val id: Long,
    val revisionNo: Int,
    val changeSource: String,
    val changeSummary: ResumeEditorChangeSummaryDto,
    val createdAt: Instant,
)

data class ResumeEditorRevisionDto(
    val id: Long,
    val workspaceId: Long,
    val resumeVersionId: Long,
    val revisionNo: Int,
    val changeSource: String,
    val changeSummary: ResumeEditorChangeSummaryDto,
    val document: ResumeEditorDocumentDto,
    val createdAt: Instant,
)

data class ResumeEditorTrackedChangesDto(
    val resumeVersionId: Long,
    val fromRevisionId: Long,
    val toRevisionId: Long,
    val changeSummary: ResumeEditorChangeSummaryDto,
    val changes: List<ResumeEditorTrackedChangeDto>,
)

data class ResumeEditorTrackedChangeDto(
    val blockId: String,
    val nodeId: String,
    val changeType: String,
    val beforeBlockType: String?,
    val afterBlockType: String?,
    val beforeText: String?,
    val afterText: String?,
    val fieldPath: String?,
    val beforeParentNodeId: String? = null,
    val afterParentNodeId: String? = null,
    val beforeDepth: Int? = null,
    val afterDepth: Int? = null,
    val textChanged: Boolean = false,
    val structureChanged: Boolean = false,
    val moveRelated: Boolean = false,
    val beforeTextLines: List<String> = emptyList(),
    val afterTextLines: List<String> = emptyList(),
)

data class ResumeEditorMergePreviewDto(
    val resumeVersionId: Long,
    val baseRevisionId: Long,
    val currentRevisionId: Long,
    val mergeStatus: String,
    val mergedDocument: ResumeEditorDocumentDto,
    val conflicts: List<ResumeEditorMergeConflictDto>,
    val changeSummary: ResumeEditorChangeSummaryDto,
)

data class ResumeEditorMergeConflictDto(
    val blockId: String,
    val nodeId: String,
    val conflictType: String,
    val baseText: String?,
    val currentText: String?,
    val proposedText: String?,
    val conflictScopes: List<String> = emptyList(),
    val baseParentNodeId: String? = null,
    val currentParentNodeId: String? = null,
    val proposedParentNodeId: String? = null,
    val baseTextLines: List<String> = emptyList(),
    val currentTextLines: List<String> = emptyList(),
    val proposedTextLines: List<String> = emptyList(),
)

data class ResumeEditorSelectionAnchorDto(
    val nodeId: String,
    val anchorPath: String? = null,
    val fieldPath: String? = null,
    val selectionStartOffset: Int? = null,
    val selectionEndOffset: Int? = null,
    val selectedText: String? = null,
    val anchorQuote: String? = null,
    val sentenceIndex: Int? = null,
)

data class ResumeEditorCommentReplyDto(
    val id: Long,
    val commentThreadId: Long,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorCommentThreadDto(
    val id: Long,
    val blockId: String,
    val selectionAnchor: ResumeEditorSelectionAnchorDto?,
    val fieldPath: String?,
    val selectionStartOffset: Int?,
    val selectionEndOffset: Int?,
    val selectedText: String?,
    val body: String,
    val status: String,
    val resolvedAt: Instant?,
    val replyCount: Int = 0,
    val replies: List<ResumeEditorCommentReplyDto> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorQuestionCardDto(
    val id: Long,
    val blockId: String,
    val selectionAnchor: ResumeEditorSelectionAnchorDto?,
    val fieldPath: String?,
    val selectionStartOffset: Int?,
    val selectionEndOffset: Int?,
    val selectedText: String?,
    val title: String?,
    val questionText: String,
    val questionType: String,
    val sourceType: String,
    val linkedQuestionId: Long?,
    val status: String,
    val followUpSuggestions: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorCommentSummaryDto(
    val totalCount: Int,
    val openCount: Int,
    val resolvedCount: Int,
    val totalReplyCount: Int = 0,
)

data class ResumeEditorQuestionCardSummaryDto(
    val totalCount: Int,
    val activeCount: Int,
    val archivedCount: Int,
)

data class ResumeEditorQuestionSuggestionResponseDto(
    val resumeVersionId: Long,
    val blockId: String,
    val selectionAnchor: ResumeEditorSelectionAnchorDto?,
    val selectedText: String,
    val sourceType: String,
    val suggestions: List<ResumeEditorSuggestedQuestionDto>,
)

data class ResumeEditorSuggestedQuestionDto(
    val title: String,
    val questionText: String,
    val questionType: String,
    val rationale: String,
    val followUpSuggestions: List<String>,
)

data class ResumeEditorRewriteSuggestionResponseDto(
    val resumeVersionId: Long,
    val blockId: String,
    val selectionAnchor: ResumeEditorSelectionAnchorDto?,
    val selectedText: String,
    val sourceType: String,
    val suggestions: List<ResumeEditorRewriteSuggestionDto>,
)

data class ResumeEditorRewriteSuggestionDto(
    val suggestedText: String,
    val rationale: String,
    val focusArea: String,
    val partialApplyAllowed: Boolean,
)

data class UpdateResumeEditorDocumentRequest(
    @field:Valid
    val blocks: List<ResumeEditorBlockDto>? = null,
    val rootNodeId: String? = null,
    @field:Valid
    val nodes: List<ResumeEditorNodeDto>? = null,
    val tableOfContents: List<ResumeEditorTableOfContentsItemDto>? = null,
    val markdownSource: String? = null,
    val layoutMetadata: Map<String, String> = emptyMap(),
    @field:Min(1)
    val baseRevisionNo: Int? = null,
    val changeSource: String? = null,
)

data class ResumeEditorDocumentOperationDto(
    @field:NotBlank
    val operationType: String,
    val nodeId: String? = null,
    val parentNodeId: String? = null,
    val referenceNodeId: String? = null,
    val text: String? = null,
    @field:Min(0)
    val startOffset: Int? = null,
    @field:Min(0)
    val endOffset: Int? = null,
    val nodeType: String? = null,
    val markType: String? = null,
    val href: String? = null,
    val collapsed: Boolean? = null,
)

data class PatchResumeEditorDocumentOperationsRequest(
    @field:NotEmpty
    @field:Valid
    val operations: List<ResumeEditorDocumentOperationDto>,
    @field:Min(1)
    val baseRevisionNo: Int? = null,
    val changeSource: String? = null,
    val clientSessionKey: String? = null,
    val clientChangeId: String? = null,
)

data class CreateResumeEditorCommentRequest(
    val blockId: String? = null,
    val selectionAnchor: ResumeEditorSelectionAnchorDto? = null,
    val fieldPath: String? = null,
    @field:Min(0)
    val selectionStartOffset: Int? = null,
    @field:Min(1)
    val selectionEndOffset: Int? = null,
    val selectedText: String? = null,
    @field:NotBlank
    val body: String,
)

data class UpdateResumeEditorCommentRequest(
    val body: String? = null,
    val status: String? = null,
)

data class CreateResumeEditorCommentReplyRequest(
    @field:NotBlank
    val body: String,
)

data class CreateResumeEditorQuestionCardRequest(
    val blockId: String? = null,
    val selectionAnchor: ResumeEditorSelectionAnchorDto? = null,
    val fieldPath: String? = null,
    @field:Min(0)
    val selectionStartOffset: Int? = null,
    @field:Min(1)
    val selectionEndOffset: Int? = null,
    val selectedText: String? = null,
    val title: String? = null,
    @field:NotBlank
    val questionText: String,
    @field:NotBlank
    val questionType: String,
    val linkedQuestionId: Long? = null,
    val followUpSuggestions: List<String> = emptyList(),
)

data class UpdateResumeEditorQuestionCardRequest(
    val title: String? = null,
    val questionText: String? = null,
    val questionType: String? = null,
    val linkedQuestionId: Long? = null,
    val status: String? = null,
    val followUpSuggestions: List<String>? = null,
)

data class CreateResumeEditorQuestionSuggestionRequest(
    val blockId: String? = null,
    val selectionAnchor: ResumeEditorSelectionAnchorDto? = null,
    val fieldPath: String? = null,
    val selectedText: String? = null,
    @field:Min(1)
    @field:Max(10)
    val maxSuggestions: Int = 3,
)

data class CreateResumeEditorRewriteSuggestionRequest(
    val blockId: String? = null,
    val selectionAnchor: ResumeEditorSelectionAnchorDto? = null,
    val fieldPath: String? = null,
    val selectedText: String? = null,
)

data class ImportResumeEditorMarkdownRequest(
    @field:NotBlank
    val markdownSource: String,
    val replaceDocument: Boolean = true,
    @field:Min(1)
    val baseRevisionNo: Int? = null,
    val changeSource: String? = null,
)

data class CreateResumeEditorPresenceRequest(
    @field:NotBlank
    val sessionKey: String,
    val viewMode: String? = null,
    val selectedBlockId: String? = null,
)

data class ResumeEditorMergePreviewRequest(
    @field:Valid
    val blocks: List<ResumeEditorBlockDto>? = null,
    val rootNodeId: String? = null,
    @field:Valid
    val nodes: List<ResumeEditorNodeDto>? = null,
    val tableOfContents: List<ResumeEditorTableOfContentsItemDto>? = null,
    val markdownSource: String? = null,
    val layoutMetadata: Map<String, String> = emptyMap(),
    @field:Min(1)
    val baseRevisionNo: Int,
)
