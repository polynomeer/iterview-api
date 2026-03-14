package com.example.interviewplatform.interview.dto

data class InterviewSessionCoverageEvidenceItemDto(
    val id: Long,
    val section: String,
    val label: String?,
    val snippet: String,
    val facet: String,
    val sourceRecordType: String,
    val sourceRecordId: Long,
    val displayOrder: Int,
    val coverageStatus: String,
    val linkedQuestionIds: List<Long>,
)

data class InterviewSessionCoverageFacetSummaryDto(
    val section: String,
    val label: String?,
    val sourceRecordType: String,
    val sourceRecordId: Long,
    val defendedFacets: List<String>,
    val weakFacets: List<String>,
    val skippedFacets: List<String>,
    val unaskedFacets: List<String>,
)

data class InterviewSessionCoverageResponseDto(
    val sessionId: Long,
    val interviewMode: String,
    val overallCoveragePercent: Int,
    val defendedCoveragePercent: Int,
    val weakFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val skippedFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val facetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val evidenceItems: List<InterviewSessionCoverageEvidenceItemDto>,
)

data class InterviewSessionResumeMapQuestionDto(
    val sessionQuestionId: Long,
    val title: String,
    val sourceType: String,
    val orderIndex: Int,
    val status: String,
    val isFollowUp: Boolean,
)

data class InterviewSessionResumeMapEvidenceItemDto(
    val section: String,
    val label: String?,
    val snippet: String,
    val facet: String,
    val sourceRecordType: String,
    val sourceRecordId: Long,
    val displayOrder: Int,
    val coverageStatus: String,
    val primaryQuestionCount: Int,
    val followUpQuestionCount: Int,
    val relatedQuestions: List<InterviewSessionResumeMapQuestionDto>,
)

data class InterviewSessionResumeMapResponseDto(
    val sessionId: Long,
    val resumeVersionId: Long?,
    val weakFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val skippedFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val facetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val evidenceItems: List<InterviewSessionResumeMapEvidenceItemDto>,
)
