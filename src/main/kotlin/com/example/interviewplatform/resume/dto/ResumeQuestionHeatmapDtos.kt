package com.example.interviewplatform.resume.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ResumeQuestionHeatmapDto(
    val resumeVersionId: Long,
    val scope: String,
    val appliedFilters: ResumeQuestionHeatmapAppliedFiltersDto,
    val filterSummary: ResumeQuestionHeatmapFilterSummaryDto,
    val summary: ResumeQuestionHeatmapSummaryDto,
    val items: List<ResumeQuestionHeatmapItemDto>,
)

data class ResumeQuestionHeatmapOverlayTargetListDto(
    val resumeVersionId: Long,
    val scope: String,
    val appliedFilters: ResumeQuestionHeatmapAppliedFiltersDto,
    val filterSummary: ResumeQuestionHeatmapFilterSummaryDto,
    val items: List<ResumeQuestionHeatmapOverlayTargetDto>,
)

data class ResumeQuestionHeatmapAppliedFiltersDto(
    val scope: String,
    val weakOnly: Boolean,
    val companyName: String?,
    val interviewDateFrom: LocalDate?,
    val interviewDateTo: LocalDate?,
    val targetType: String?,
)

data class ResumeQuestionHeatmapFilterSummaryDto(
    val totalQuestions: Int,
    val weakQuestionCount: Int,
    val pressureQuestionCount: Int,
    val followUpQuestionCount: Int,
    val distinctInterviewCount: Int,
    val distinctCompanyCount: Int,
    val companyNames: List<String>,
    val availableTargetTypes: List<String>,
    val targetTypeCounts: Map<String, Int>,
    val earliestInterviewDate: LocalDate?,
    val latestInterviewDate: LocalDate?,
)

data class ResumeQuestionHeatmapSummaryDto(
    val totalAnchors: Int,
    val totalLinkedQuestions: Int,
    val hottestAnchorLabel: String?,
    val mostFollowedUpAnchorLabel: String?,
    val weakestAnchorLabel: String?,
)

data class ResumeQuestionHeatmapItemDto(
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
    val label: String,
    val snippet: String?,
    val heatScore: Double,
    val normalizedHeatLevel: String,
    val directQuestionCount: Int,
    val followUpCount: Int,
    val distinctInterviewCount: Int,
    val pressureQuestionCount: Int,
    val weaknessCount: Int,
    val recentQuestionAt: Instant?,
    val overlayTargets: List<ResumeQuestionHeatmapOverlayTargetDto>,
    val linkedQuestions: List<ResumeQuestionHeatmapQuestionDto>,
)

data class ResumeQuestionHeatmapOverlayTargetDto(
    val id: Long?,
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
    val targetType: String,
    val targetKey: String,
    val fieldPath: String,
    val textSnippet: String,
    val textStartOffset: Int?,
    val textEndOffset: Int?,
    val sentenceIndex: Int?,
    val paragraphIndex: Int?,
    val heatScore: Double,
    val normalizedHeatLevel: String,
    val questionCount: Int,
    val followUpCount: Int,
    val pressureQuestionCount: Int,
    val weaknessCount: Int,
    val linkedQuestions: List<ResumeQuestionHeatmapQuestionDto>,
)

data class ResumeQuestionHeatmapQuestionDto(
    val interviewRecordQuestionId: Long,
    val sourceInterviewRecordId: Long,
    val linkedQuestionId: Long?,
    val text: String,
    val questionType: String,
    val isFollowUp: Boolean,
    val followUpCount: Int,
    val pressureQuestion: Boolean,
    val weakAnswer: Boolean,
    val weaknessTags: List<String>,
    val interviewDate: LocalDate?,
    val linkSource: String,
    val confidenceScore: BigDecimal?,
)

data class ResumeQuestionHeatmapLinkDto(
    val id: Long,
    val resumeVersionId: Long,
    val interviewRecordQuestionId: Long,
    val anchorType: String,
    val anchorRecordId: Long?,
    val anchorKey: String?,
    val overlayTargetType: String?,
    val overlayFieldPath: String?,
    val overlaySentenceIndex: Int?,
    val overlayTextSnippet: String?,
    val linkSource: String,
    val confidenceScore: BigDecimal?,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreateResumeQuestionHeatmapLinkRequest(
    val interviewRecordQuestionId: Long,
    @field:NotBlank
    val anchorType: String,
    val anchorRecordId: Long? = null,
    val anchorKey: String? = null,
    val overlayTargetType: String? = null,
    val overlayFieldPath: String? = null,
    val overlaySentenceIndex: Int? = null,
    val overlayTextSnippet: String? = null,
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val confidenceScore: BigDecimal? = null,
)

data class UpdateResumeQuestionHeatmapLinkRequest(
    val anchorType: String? = null,
    val anchorRecordId: Long? = null,
    val anchorKey: String? = null,
    val overlayTargetType: String? = null,
    val overlayFieldPath: String? = null,
    val overlaySentenceIndex: Int? = null,
    val overlayTextSnippet: String? = null,
    @field:DecimalMin("0.0")
    @field:DecimalMax("1.0")
    val confidenceScore: BigDecimal? = null,
    val active: Boolean? = null,
)
