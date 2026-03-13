package com.example.interviewplatform.resume.mapper

import com.example.interviewplatform.resume.dto.ResumeAchievementItemDto
import com.example.interviewplatform.resume.dto.ResumeAwardItemDto
import com.example.interviewplatform.resume.dto.ResumeCertificationItemDto
import com.example.interviewplatform.resume.dto.ResumeCompetencyItemDto
import com.example.interviewplatform.resume.dto.ResumeContactPointDto
import com.example.interviewplatform.resume.dto.ResumeEducationItemDto
import com.example.interviewplatform.resume.dto.ResumeExperienceSnapshotDto
import com.example.interviewplatform.resume.dto.ResumeProfileSnapshotDto
import com.example.interviewplatform.resume.dto.ResumeProjectSnapshotDto
import com.example.interviewplatform.resume.dto.ResumeProjectTagDto
import com.example.interviewplatform.resume.dto.ResumeRiskItemDto
import com.example.interviewplatform.resume.dto.ResumeSkillSnapshotDto
import com.example.interviewplatform.resume.entity.ResumeAchievementItemEntity
import com.example.interviewplatform.resume.entity.ResumeAwardItemEntity
import com.example.interviewplatform.resume.entity.ResumeCertificationItemEntity
import com.example.interviewplatform.resume.entity.ResumeCompetencyItemEntity
import com.example.interviewplatform.resume.entity.ResumeContactPointEntity
import com.example.interviewplatform.resume.entity.ResumeEducationItemEntity
import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProfileSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProjectTagEntity
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
        companyName = entity.companyName,
        roleName = entity.roleName,
        employmentType = entity.employmentType,
        startedOn = entity.startedOn,
        endedOn = entity.endedOn,
        current = entity.isCurrent,
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

    fun toProfileDto(entity: ResumeProfileSnapshotEntity): ResumeProfileSnapshotDto = ResumeProfileSnapshotDto(
        fullName = entity.fullName,
        headline = entity.headline,
        summaryText = entity.summaryText,
        locationText = entity.locationText,
        yearsOfExperienceText = entity.yearsOfExperienceText,
        sourceText = entity.sourceText,
    )

    fun toContactDto(entity: ResumeContactPointEntity): ResumeContactPointDto = ResumeContactPointDto(
        id = entity.id,
        contactType = entity.contactType,
        label = entity.label,
        valueText = entity.valueText,
        url = entity.url,
        displayOrder = entity.displayOrder,
        primary = entity.isPrimary,
    )

    fun toCompetencyDto(entity: ResumeCompetencyItemEntity): ResumeCompetencyItemDto = ResumeCompetencyItemDto(
        id = entity.id,
        title = entity.title,
        description = entity.description,
        sourceText = entity.sourceText,
        displayOrder = entity.displayOrder,
    )

    fun toProjectDto(entity: ResumeProjectSnapshotEntity, tags: List<ResumeProjectTagEntity>): ResumeProjectSnapshotDto = ResumeProjectSnapshotDto(
        id = entity.id,
        resumeExperienceSnapshotId = entity.resumeExperienceSnapshotId,
        title = entity.title,
        organizationName = entity.organizationName,
        roleName = entity.roleName,
        summaryText = entity.summaryText,
        contentText = entity.contentText,
        projectCategoryCode = entity.projectCategoryCode,
        projectCategoryName = entity.projectCategoryName,
        tags = tags.map(::toProjectTagDto),
        techStackText = entity.techStackText,
        startedOn = entity.startedOn,
        endedOn = entity.endedOn,
        displayOrder = entity.displayOrder,
        sourceText = entity.sourceText,
    )

    fun toProjectTagDto(entity: ResumeProjectTagEntity): ResumeProjectTagDto = ResumeProjectTagDto(
        id = entity.id,
        tagName = entity.tagName,
        tagType = entity.tagType,
        displayOrder = entity.displayOrder,
        sourceText = entity.sourceText,
    )

    fun toAchievementDto(entity: ResumeAchievementItemEntity): ResumeAchievementItemDto = ResumeAchievementItemDto(
        id = entity.id,
        resumeExperienceSnapshotId = entity.resumeExperienceSnapshotId,
        resumeProjectSnapshotId = entity.resumeProjectSnapshotId,
        title = entity.title,
        metricText = entity.metricText,
        impactSummary = entity.impactSummary,
        sourceText = entity.sourceText,
        severityHint = entity.severityHint,
        displayOrder = entity.displayOrder,
    )

    fun toEducationDto(entity: ResumeEducationItemEntity): ResumeEducationItemDto = ResumeEducationItemDto(
        id = entity.id,
        institutionName = entity.institutionName,
        degreeName = entity.degreeName,
        fieldOfStudy = entity.fieldOfStudy,
        startedOn = entity.startedOn,
        endedOn = entity.endedOn,
        description = entity.description,
        displayOrder = entity.displayOrder,
        sourceText = entity.sourceText,
    )

    fun toCertificationDto(entity: ResumeCertificationItemEntity): ResumeCertificationItemDto = ResumeCertificationItemDto(
        id = entity.id,
        name = entity.name,
        issuerName = entity.issuerName,
        credentialCode = entity.credentialCode,
        issuedOn = entity.issuedOn,
        expiresOn = entity.expiresOn,
        scoreText = entity.scoreText,
        displayOrder = entity.displayOrder,
        sourceText = entity.sourceText,
    )

    fun toAwardDto(entity: ResumeAwardItemEntity): ResumeAwardItemDto = ResumeAwardItemDto(
        id = entity.id,
        title = entity.title,
        issuerName = entity.issuerName,
        awardedOn = entity.awardedOn,
        description = entity.description,
        displayOrder = entity.displayOrder,
        sourceText = entity.sourceText,
    )
}
