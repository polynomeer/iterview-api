package com.example.interviewplatform.resume.mapper

import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.entity.ResumeEntity
import com.example.interviewplatform.resume.entity.ResumeVersionEntity

object ResumeMapper {
    fun toResumeDto(entity: ResumeEntity, versions: List<ResumeVersionEntity>): ResumeDto = ResumeDto(
        id = entity.id,
        title = entity.title,
        isPrimary = entity.isPrimary,
        versions = versions.map { toVersionDto(it) },
    )

    fun toVersionDto(entity: ResumeVersionEntity): ResumeVersionDto = ResumeVersionDto(
        id = entity.id,
        versionNo = entity.versionNo,
        fileUrl = entity.fileUrl,
        fileName = entity.fileName,
        fileType = entity.fileType,
        fileSizeBytes = entity.fileSizeBytes,
        summaryText = entity.summaryText,
        parsingStatus = entity.parsingStatus,
        parseStartedAt = entity.parseStartedAt,
        parseCompletedAt = entity.parseCompletedAt,
        parseErrorMessage = entity.parseErrorMessage,
        isActive = entity.isActive,
        uploadedAt = entity.uploadedAt,
    )
}
