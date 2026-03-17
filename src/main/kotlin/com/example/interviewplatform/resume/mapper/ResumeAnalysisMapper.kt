package com.example.interviewplatform.resume.mapper

import com.example.interviewplatform.resume.dto.ResumeAnalysisDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisListItemDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisSuggestionDto
import com.example.interviewplatform.resume.entity.ResumeAnalysisEntity
import com.example.interviewplatform.resume.entity.ResumeAnalysisSuggestionEntity

object ResumeAnalysisMapper {
    fun toListItemDto(entity: ResumeAnalysisEntity): ResumeAnalysisListItemDto = ResumeAnalysisListItemDto(
        id = entity.id,
        resumeVersionId = entity.resumeVersionId,
        jobPostingId = entity.jobPostingId,
        status = entity.status,
        overallScore = entity.overallScore,
        matchSummary = entity.matchSummary,
        suggestedHeadline = entity.suggestedHeadline,
        recommendedFormatType = entity.recommendedFormatType,
        createdAt = entity.createdAt,
    )

    fun toDetailDto(
        entity: ResumeAnalysisEntity,
        strongMatches: List<String>,
        missingKeywords: List<String>,
        weakSignals: List<String>,
        recommendedFocusAreas: List<String>,
        suggestions: List<ResumeAnalysisSuggestionDto>,
    ): ResumeAnalysisDto = ResumeAnalysisDto(
        id = entity.id,
        resumeVersionId = entity.resumeVersionId,
        jobPostingId = entity.jobPostingId,
        status = entity.status,
        overallScore = entity.overallScore,
        matchSummary = entity.matchSummary,
        strongMatches = strongMatches,
        missingKeywords = missingKeywords,
        weakSignals = weakSignals,
        recommendedFocusAreas = recommendedFocusAreas,
        suggestedHeadline = entity.suggestedHeadline,
        suggestedSummary = entity.suggestedSummary,
        recommendedFormatType = entity.recommendedFormatType,
        suggestions = suggestions,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun toSuggestionDto(entity: ResumeAnalysisSuggestionEntity): ResumeAnalysisSuggestionDto = ResumeAnalysisSuggestionDto(
        id = entity.id,
        sectionKey = entity.sectionKey,
        originalText = entity.originalText,
        suggestedText = entity.suggestedText,
        reason = entity.reason,
        suggestionType = entity.suggestionType,
        accepted = entity.accepted,
        displayOrder = entity.displayOrder,
        createdAt = entity.createdAt,
    )
}
