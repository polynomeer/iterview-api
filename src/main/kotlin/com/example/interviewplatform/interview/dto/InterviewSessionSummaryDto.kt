package com.example.interviewplatform.interview.dto

data class InterviewSessionSummaryDto(
    val totalQuestions: Int,
    val answeredQuestions: Int,
    val skippedQuestions: Int,
    val remainingQuestions: Int,
    val averageScore: Double?,
    val weakFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val skippedFacetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
    val facetSummaries: List<InterviewSessionCoverageFacetSummaryDto>,
)
