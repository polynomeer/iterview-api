package com.example.interviewplatform.resume.mapper

import com.example.interviewplatform.resume.dto.ResumeAnalysisDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisExportDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisListItemDto
import com.example.interviewplatform.resume.dto.ResumeAnalysisSuggestionDto
import com.example.interviewplatform.resume.dto.ResumeTailoredDocumentDto
import com.example.interviewplatform.resume.entity.ResumeAnalysisEntity
import com.example.interviewplatform.resume.entity.ResumeAnalysisExportEntity
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
        generationSource = entity.generationSource,
        llmModel = entity.llmModel,
        createdAt = entity.createdAt,
    )

    fun toDetailDto(
        entity: ResumeAnalysisEntity,
        strongMatches: List<String>,
        missingKeywords: List<String>,
        weakSignals: List<String>,
        recommendedFocusAreas: List<String>,
        analysisNotes: List<String>,
        tailoredDocument: ResumeTailoredDocumentDto?,
        suggestions: List<ResumeAnalysisSuggestionDto>,
        exports: List<ResumeAnalysisExportDto>,
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
        generationSource = entity.generationSource,
        llmModel = entity.llmModel,
        analysisNotes = analysisNotes,
        tailoredDocument = tailoredDocument,
        suggestions = suggestions,
        exports = exports,
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

    fun toExportDto(entity: ResumeAnalysisExportEntity, versionId: Long, analysisId: Long): ResumeAnalysisExportDto = ResumeAnalysisExportDto(
        id = entity.id,
        resumeAnalysisId = entity.resumeAnalysisId,
        exportType = entity.exportType,
        formatType = entity.formatType,
        fileName = entity.fileName,
        fileUrl = "/api/resume-versions/$versionId/analyses/$analysisId/exports/${entity.id}/file",
        fileSizeBytes = entity.fileSizeBytes,
        checksumSha256 = entity.checksumSha256,
        pageCount = entity.pageCount,
        createdAt = entity.createdAt,
    )
}
