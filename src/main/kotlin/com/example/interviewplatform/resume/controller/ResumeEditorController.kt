package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.CreateResumeEditorCommentReplyRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorCommentRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorPresenceRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorQuestionCardRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorQuestionSuggestionRequest
import com.example.interviewplatform.resume.dto.CreateResumeEditorRewriteSuggestionRequest
import com.example.interviewplatform.resume.dto.ImportResumeEditorMarkdownRequest
import com.example.interviewplatform.resume.dto.PatchResumeEditorDocumentOperationsRequest
import com.example.interviewplatform.resume.dto.ResumeEditorCommentThreadDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergePreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergePreviewRequest
import com.example.interviewplatform.resume.dto.ResumeEditorPresenceDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionCardDto
import com.example.interviewplatform.resume.dto.ResumeEditorQuestionSuggestionResponseDto
import com.example.interviewplatform.resume.dto.ResumeEditorRevisionDto
import com.example.interviewplatform.resume.dto.ResumeEditorRevisionListItemDto
import com.example.interviewplatform.resume.dto.ResumeEditorRewriteSuggestionResponseDto
import com.example.interviewplatform.resume.dto.ResumeEditorTrackedChangesDto
import com.example.interviewplatform.resume.dto.ResumeEditorWorkspaceDto
import com.example.interviewplatform.resume.dto.UpdateResumeEditorCommentRequest
import com.example.interviewplatform.resume.dto.UpdateResumeEditorDocumentRequest
import com.example.interviewplatform.resume.dto.UpdateResumeEditorQuestionCardRequest
import com.example.interviewplatform.resume.service.ResumeEditorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Resume Editor")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume-versions/{versionId}/editor")
class ResumeEditorController(
    private val resumeEditorService: ResumeEditorService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Get or initialize resume editor workspace")
    fun getWorkspace(@PathVariable versionId: Long): ResumeEditorWorkspaceDto =
        resumeEditorService.getWorkspace(currentUserProvider.currentUserId(), versionId)

    @PutMapping("/document")
    @Operation(summary = "Replace resume editor document")
    fun updateDocument(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: UpdateResumeEditorDocumentRequest,
    ): ResumeEditorWorkspaceDto =
        resumeEditorService.updateDocument(currentUserProvider.currentUserId(), versionId, request)

    @PatchMapping("/document/operations")
    @Operation(summary = "Apply granular operations to one resume editor document")
    fun patchDocumentOperations(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: PatchResumeEditorDocumentOperationsRequest,
    ): ResumeEditorWorkspaceDto =
        resumeEditorService.patchDocumentOperations(currentUserProvider.currentUserId(), versionId, request)

    @PostMapping("/import-markdown")
    @Operation(summary = "Import markdown into resume editor workspace")
    fun importMarkdown(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: ImportResumeEditorMarkdownRequest,
    ): ResumeEditorWorkspaceDto =
        resumeEditorService.importMarkdown(currentUserProvider.currentUserId(), versionId, request)

    @PostMapping("/comments")
    @Operation(summary = "Create resume editor comment thread")
    fun createComment(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeEditorCommentRequest,
    ): ResumeEditorCommentThreadDto =
        resumeEditorService.createComment(currentUserProvider.currentUserId(), versionId, request)

    @PatchMapping("/comments/{commentId}")
    @Operation(summary = "Update resume editor comment thread")
    fun updateComment(
        @PathVariable versionId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: UpdateResumeEditorCommentRequest,
    ): ResumeEditorCommentThreadDto =
        resumeEditorService.updateComment(currentUserProvider.currentUserId(), versionId, commentId, request)

    @PostMapping("/comments/{commentId}/replies")
    @Operation(summary = "Create resume editor comment reply")
    fun createCommentReply(
        @PathVariable versionId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody request: CreateResumeEditorCommentReplyRequest,
    ): ResumeEditorCommentThreadDto =
        resumeEditorService.createCommentReply(currentUserProvider.currentUserId(), versionId, commentId, request)

    @PostMapping("/presence")
    @Operation(summary = "Upsert resume editor presence heartbeat")
    fun upsertPresence(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeEditorPresenceRequest,
    ): List<ResumeEditorPresenceDto> =
        resumeEditorService.upsertPresence(currentUserProvider.currentUserId(), versionId, request)

    @PostMapping("/question-cards")
    @Operation(summary = "Create resume editor question card")
    fun createQuestionCard(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeEditorQuestionCardRequest,
    ): ResumeEditorQuestionCardDto =
        resumeEditorService.createQuestionCard(currentUserProvider.currentUserId(), versionId, request)

    @PatchMapping("/question-cards/{cardId}")
    @Operation(summary = "Update resume editor question card")
    fun updateQuestionCard(
        @PathVariable versionId: Long,
        @PathVariable cardId: Long,
        @Valid @RequestBody request: UpdateResumeEditorQuestionCardRequest,
    ): ResumeEditorQuestionCardDto =
        resumeEditorService.updateQuestionCard(currentUserProvider.currentUserId(), versionId, cardId, request)

    @PostMapping("/auto-question-suggestions")
    @Operation(summary = "Generate resume editor question suggestions for a selected block or sentence")
    fun generateQuestionSuggestions(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeEditorQuestionSuggestionRequest,
    ): ResumeEditorQuestionSuggestionResponseDto =
        resumeEditorService.generateQuestionSuggestions(currentUserProvider.currentUserId(), versionId, request)

    @PostMapping("/rewrite-suggestions")
    @Operation(summary = "Generate resume editor rewrite suggestions for a selected block or sentence")
    fun generateRewriteSuggestions(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: CreateResumeEditorRewriteSuggestionRequest,
    ): ResumeEditorRewriteSuggestionResponseDto =
        resumeEditorService.generateRewriteSuggestions(currentUserProvider.currentUserId(), versionId, request)

    @GetMapping("/print-preview")
    @Operation(summary = "Build resume editor print preview")
    fun getPrintPreview(@PathVariable versionId: Long): ResumeEditorPrintPreviewDto =
        resumeEditorService.getPrintPreview(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/revisions")
    @Operation(summary = "List resume editor workspace revisions")
    fun getRevisions(@PathVariable versionId: Long): List<ResumeEditorRevisionListItemDto> =
        resumeEditorService.getRevisions(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/revisions/{revisionId}")
    @Operation(summary = "Get one resume editor workspace revision")
    fun getRevision(
        @PathVariable versionId: Long,
        @PathVariable revisionId: Long,
    ): ResumeEditorRevisionDto =
        resumeEditorService.getRevision(currentUserProvider.currentUserId(), versionId, revisionId)

    @GetMapping("/tracked-changes")
    @Operation(summary = "Compare two resume editor revisions")
    fun getTrackedChanges(
        @PathVariable versionId: Long,
        @RequestParam fromRevisionId: Long,
        @RequestParam toRevisionId: Long,
    ): ResumeEditorTrackedChangesDto =
        resumeEditorService.getTrackedChanges(currentUserProvider.currentUserId(), versionId, fromRevisionId, toRevisionId)

    @PostMapping("/merge-preview")
    @Operation(summary = "Preview server-assisted merge for a stale resume editor write")
    fun getMergePreview(
        @PathVariable versionId: Long,
        @Valid @RequestBody request: ResumeEditorMergePreviewRequest,
    ): ResumeEditorMergePreviewDto =
        resumeEditorService.getMergePreview(currentUserProvider.currentUserId(), versionId, request)
}
