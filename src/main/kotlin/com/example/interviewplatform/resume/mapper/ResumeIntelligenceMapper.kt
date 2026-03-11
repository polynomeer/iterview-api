package com.example.interviewplatform.resume.mapper

import com.example.interviewplatform.resume.dto.ResumeExperienceSnapshotDto
import com.example.interviewplatform.resume.dto.ResumeRiskItemDto
import com.example.interviewplatform.resume.dto.ResumeSkillSnapshotDto
import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeRiskItemEntity
import com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity
import com.example.interviewplatform.skill.entity.SkillCategoryEntity

object ResumeIntelligenceMapper {
    fun toSkillDto(
        entity: ResumeSkillSnapshotEntity,
        category: SkillCategoryEntity?,
    ): ResumeSkillSnapshotDto = ResumeSkillSnapshotDto(
        skillId = entity.skillId,
        skillName = entity.skillName,
        skillCategoryCode = category?.code,
        skillCategoryName = category?.name,
        sourceText = entity.sourceText,
        confidenceScore = entity.confidenceScore,
        confirmed = entity.isConfirmed,
    )

    fun toExperienceDto(entity: ResumeExperienceSnapshotEntity): ResumeExperienceSnapshotDto = ResumeExperienceSnapshotDto(
        id = entity.id,
        projectName = entity.projectName,
        summaryText = entity.summaryText,
        impactText = entity.impactText,
        sourceText = entity.sourceText,
        riskLevel = entity.riskLevel,
        displayOrder = entity.displayOrder,
        confirmed = entity.isConfirmed,
    )

    fun toRiskDto(entity: ResumeRiskItemEntity): ResumeRiskItemDto = ResumeRiskItemDto(
        id = entity.id,
        linkedQuestionId = entity.linkedQuestionId,
        riskType = entity.riskType,
        title = entity.title,
        description = entity.description,
        severity = entity.severity,
    )
}
