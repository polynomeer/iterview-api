package com.example.interviewplatform.jobposting.mapper

import com.example.interviewplatform.jobposting.dto.JobPostingDto
import com.example.interviewplatform.jobposting.entity.JobPostingEntity

object JobPostingMapper {
    fun toDto(
        entity: JobPostingEntity,
        parsedRequirements: List<String>,
        parsedNiceToHave: List<String>,
        parsedKeywords: List<String>,
        parsedResponsibilities: List<String>,
    ): JobPostingDto = JobPostingDto(
        id = entity.id,
        inputType = entity.inputType,
        sourceUrl = entity.sourceUrl,
        rawText = entity.rawText,
        companyName = entity.companyName,
        roleName = entity.roleName,
        parsedRequirements = parsedRequirements,
        parsedNiceToHave = parsedNiceToHave,
        parsedKeywords = parsedKeywords,
        parsedResponsibilities = parsedResponsibilities,
        parsedSummary = entity.parsedSummary,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )
}
