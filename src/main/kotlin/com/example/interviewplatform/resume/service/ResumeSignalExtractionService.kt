package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity

interface ResumeSignalExtractionService {
    fun extract(version: ResumeVersionEntity): ExtractedResumeSignals
}

data class ExtractedResumeSignals(
    val skills: List<ExtractedResumeSkill>,
    val experiences: List<ExtractedResumeExperience>,
    val risks: List<ExtractedResumeRisk>,
)

data class ExtractedResumeSkill(
    val skillName: String,
    val sourceText: String?,
    val confidenceScore: Double?,
)

data class ExtractedResumeExperience(
    val projectName: String?,
    val summaryText: String,
    val impactText: String?,
    val sourceText: String,
    val riskLevel: String,
    val displayOrder: Int,
)

data class ExtractedResumeRisk(
    val riskType: String,
    val title: String,
    val description: String,
    val severity: String,
)
