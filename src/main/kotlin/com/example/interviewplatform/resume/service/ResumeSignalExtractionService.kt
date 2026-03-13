package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity

interface ResumeSignalExtractionService {
    fun extract(version: ResumeVersionEntity): ExtractedResumeSignals
}

data class ExtractedResumeSignals(
    val profile: ExtractedResumeProfile?,
    val contacts: List<ExtractedResumeContactPoint>,
    val competencies: List<ExtractedResumeCompetency>,
    val skills: List<ExtractedResumeSkill>,
    val experiences: List<ExtractedResumeExperience>,
    val projects: List<ExtractedResumeProject>,
    val achievements: List<ExtractedResumeAchievement>,
    val educationItems: List<ExtractedResumeEducation>,
    val certificationItems: List<ExtractedResumeCertification>,
    val awardItems: List<ExtractedResumeAward>,
    val risks: List<ExtractedResumeRisk>,
    val sourceType: String,
    val extractionStatus: String,
    val extractionErrorMessage: String?,
    val extractionConfidence: Double?,
    val llmModel: String?,
    val llmPromptVersion: String?,
    val rawExtractionPayload: String?,
)

data class ExtractedResumeSkill(
    val skillName: String,
    val sourceText: String?,
    val confidenceScore: Double?,
)

data class ExtractedResumeProfile(
    val fullName: String?,
    val headline: String?,
    val summaryText: String?,
    val locationText: String?,
    val yearsOfExperienceText: String?,
    val sourceText: String?,
)

data class ExtractedResumeContactPoint(
    val contactType: String,
    val label: String?,
    val valueText: String?,
    val url: String?,
    val displayOrder: Int,
    val isPrimary: Boolean,
)

data class ExtractedResumeCompetency(
    val title: String,
    val description: String,
    val sourceText: String?,
    val displayOrder: Int,
)

data class ExtractedResumeExperience(
    val projectName: String?,
    val companyName: String?,
    val roleName: String?,
    val employmentType: String?,
    val startedOn: java.time.LocalDate?,
    val endedOn: java.time.LocalDate?,
    val isCurrent: Boolean,
    val summaryText: String,
    val impactText: String?,
    val sourceText: String,
    val riskLevel: String,
    val displayOrder: Int,
)

data class ExtractedResumeProject(
    val title: String,
    val organizationName: String?,
    val roleName: String?,
    val summaryText: String,
    val contentText: String?,
    val projectCategoryCode: String?,
    val projectCategoryName: String?,
    val tags: List<ExtractedResumeProjectTag>,
    val techStackText: String?,
    val startedOn: java.time.LocalDate?,
    val endedOn: java.time.LocalDate?,
    val displayOrder: Int,
    val sourceText: String?,
    val experienceDisplayOrder: Int?,
)

data class ExtractedResumeProjectTag(
    val tagName: String,
    val tagType: String?,
    val displayOrder: Int,
    val sourceText: String?,
)

data class ExtractedResumeAchievement(
    val title: String,
    val metricText: String?,
    val impactSummary: String,
    val sourceText: String?,
    val severityHint: String?,
    val displayOrder: Int,
    val experienceDisplayOrder: Int?,
    val projectDisplayOrder: Int?,
)

data class ExtractedResumeEducation(
    val institutionName: String,
    val degreeName: String?,
    val fieldOfStudy: String?,
    val startedOn: java.time.LocalDate?,
    val endedOn: java.time.LocalDate?,
    val description: String?,
    val displayOrder: Int,
    val sourceText: String?,
)

data class ExtractedResumeCertification(
    val name: String,
    val issuerName: String?,
    val credentialCode: String?,
    val issuedOn: java.time.LocalDate?,
    val expiresOn: java.time.LocalDate?,
    val scoreText: String?,
    val displayOrder: Int,
    val sourceText: String?,
)

data class ExtractedResumeAward(
    val title: String,
    val issuerName: String?,
    val awardedOn: java.time.LocalDate?,
    val description: String?,
    val displayOrder: Int,
    val sourceText: String?,
)

data class ExtractedResumeRisk(
    val riskType: String,
    val title: String,
    val description: String,
    val severity: String,
)
