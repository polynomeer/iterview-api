package com.example.interviewplatform.dailycard.dto

import com.example.interviewplatform.question.dto.LearningMaterialDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Home payload with the primary daily card, retry queue preview, and learning materials")
data class HomeResponseDto(
    @field:Schema(description = "Primary daily question card when one is available")
    val todayQuestion: HomeQuestionDto?,
    @field:Schema(description = "Retry questions that should be surfaced separately from the main card")
    val retryQuestions: List<HomeRetryQuestionDto>,
    @field:Schema(description = "Preview of current skill radar categories")
    val skillRadarPreview: List<HomeSkillRadarPreviewDto> = emptyList(),
    @field:Schema(description = "Weak skill highlights based on current benchmark gaps")
    val weakSkillHighlights: List<HomeWeakSkillDto> = emptyList(),
    @field:Schema(description = "Resume risk questions that likely need defense practice")
    val resumeRiskPreview: List<HomeResumeRiskQuestionDto> = emptyList(),
    @field:Schema(description = "Learning materials linked to surfaced questions")
    val learningMaterials: List<LearningMaterialDto>,
    @field:Schema(description = "High-level counts for the home screen")
    val summaryStats: HomeSummaryStatsDto,
)
