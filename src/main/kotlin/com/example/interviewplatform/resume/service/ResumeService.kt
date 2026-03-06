package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
import com.example.interviewplatform.resume.dto.CreateResumeRequest
import com.example.interviewplatform.resume.dto.CreateResumeVersionRequest
import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.entity.ResumeEntity
import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.example.interviewplatform.resume.mapper.ResumeMapper
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ResumeService(
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val userRepository: UserRepository,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun listUserResumes(userId: Long): List<ResumeDto> {
        requireUser(userId)
        val resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId)
        if (resumes.isEmpty()) {
            return emptyList()
        }

        val versionsByResumeId = resumeVersionRepository
            .findByResumeIdInOrderByResumeIdAscVersionNoAsc(resumes.map { it.id })
            .groupBy { it.resumeId }

        return resumes.map { resume -> ResumeMapper.toResumeDto(resume, versionsByResumeId[resume.id].orEmpty()) }
    }

    @Transactional
    fun createResume(userId: Long, request: CreateResumeRequest): ResumeDto {
        requireUser(userId)
        val now = clockService.now()
        if (request.isPrimary) {
            resumeRepository.clearPrimaryForUser(userId, now)
        }

        val saved = resumeRepository.save(
            ResumeEntity(
                userId = userId,
                title = request.title.trim(),
                isPrimary = request.isPrimary,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return ResumeMapper.toResumeDto(saved, emptyList())
    }

    @Transactional
    fun createResumeVersion(userId: Long, resumeId: Long, request: CreateResumeVersionRequest): ResumeVersionDto {
        val resume = resumeRepository.findByIdAndUserId(resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found: $resumeId")

        val latestVersion = resumeVersionRepository.findTopByResumeIdOrderByVersionNoDesc(resume.id)
        val nextVersionNo = (latestVersion?.versionNo ?: 0) + 1
        val now = clockService.now()

        val created = resumeVersionRepository.save(
            ResumeVersionEntity(
                resumeId = resume.id,
                versionNo = nextVersionNo,
                fileUrl = request.fileUrl,
                rawText = request.rawText,
                parsedJson = request.parsedJson,
                summaryText = request.summaryText,
                isActive = false,
                uploadedAt = now,
                createdAt = now,
            ),
        )
        return ResumeMapper.toVersionDto(created)
    }

    @Transactional
    fun activateResumeVersion(userId: Long, versionId: Long): ActivateResumeVersionResponse {
        val targetVersion = resumeVersionRepository.findById(versionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId") }

        resumeRepository.findByIdAndUserId(targetVersion.resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId")

        resumeVersionRepository.deactivateActiveByResumeId(targetVersion.resumeId)
        resumeVersionRepository.activateByVersionId(versionId)
        val now = clockService.now()

        val activated = resumeVersionRepository.findById(versionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId") }

        return ActivateResumeVersionResponse(
            resumeId = activated.resumeId,
            versionId = activated.id,
            versionNo = activated.versionNo,
            activatedAt = now,
        )
    }

    private fun requireUser(userId: Long) {
        if (!userRepository.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $userId")
        }
    }
}
