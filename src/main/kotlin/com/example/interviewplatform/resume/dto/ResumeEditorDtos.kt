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
    val supportedViewModes: List<String>,
    val document: ResumeEditorDocumentDto,
    val comments: List<ResumeEditorCommentThreadDto>,
    val questionCards: List<ResumeEditorQuestionCardDto>,
    val commentSummary: ResumeEditorCommentSummaryDto,
    val questionCardSummary: ResumeEditorQuestionCardSummaryDto,
    val heatmapAvailable: Boolean,
    val heatmapSummary: ResumeQuestionHeatmapSummaryDto?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorDocumentDto(
    val astVersion: Int,
    val markdownSource: String?,
    val blocks: List<ResumeEditorBlockDto>,
    val layoutMetadata: Map<String, String>,
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
)

data class ResumeEditorCommentThreadDto(
    val id: Long,
    val blockId: String,
    val fieldPath: String?,
    val selectionStartOffset: Int?,
    val selectionEndOffset: Int?,
    val selectedText: String?,
    val body: String,
    val status: String,
    val resolvedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ResumeEditorQuestionCardDto(
    val id: Long,
    val blockId: String,
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
)

data class ResumeEditorQuestionCardSummaryDto(
    val totalCount: Int,
    val activeCount: Int,
    val archivedCount: Int,
)

data class ResumeEditorQuestionSuggestionResponseDto(
    val resumeVersionId: Long,
    val blockId: String,
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
    @field:NotEmpty
    @field:Valid
    val blocks: List<ResumeEditorBlockDto>,
    val markdownSource: String? = null,
    val layoutMetadata: Map<String, String> = emptyMap(),
)

data class CreateResumeEditorCommentRequest(
    @field:NotBlank
    val blockId: String,
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

data class CreateResumeEditorQuestionCardRequest(
    @field:NotBlank
    val blockId: String,
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
    @field:NotBlank
    val blockId: String,
    val fieldPath: String? = null,
    val selectedText: String? = null,
    @field:Min(1)
    @field:Max(10)
    val maxSuggestions: Int = 3,
)

data class CreateResumeEditorRewriteSuggestionRequest(
    @field:NotBlank
    val blockId: String,
    val fieldPath: String? = null,
    val selectedText: String? = null,
)
