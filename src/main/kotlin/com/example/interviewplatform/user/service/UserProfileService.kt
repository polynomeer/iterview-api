package com.example.interviewplatform.user.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.user.dto.ActiveResumeVersionSummaryDto
import com.example.interviewplatform.user.dto.MeResponse
import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.ReplaceTargetCompaniesRequest
import com.example.interviewplatform.user.dto.SettingsDto
import com.example.interviewplatform.user.dto.TargetCompaniesResponse
import com.example.interviewplatform.user.dto.TargetCompanyDto
import com.example.interviewplatform.user.dto.UpdateProfileRequest
import com.example.interviewplatform.user.dto.UpdateSettingsRequest
import com.example.interviewplatform.user.entity.UserProfileEntity
import com.example.interviewplatform.user.entity.UserSettingsEntity
import com.example.interviewplatform.user.entity.UserTargetCompanyEntity
import com.example.interviewplatform.user.entity.UserTargetCompanyId
import com.example.interviewplatform.user.mapper.UserProfileMapper
import com.example.interviewplatform.user.repository.CompanyRepository
import com.example.interviewplatform.user.repository.JobRoleRepository
import com.example.interviewplatform.user.repository.UserProfileRepository
import com.example.interviewplatform.user.repository.UserRepository
import com.example.interviewplatform.user.repository.UserSettingsRepository
import com.example.interviewplatform.user.repository.UserTargetCompanyRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserProfileService(
    private val userRepository: UserRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val userTargetCompanyRepository: UserTargetCompanyRepository,
    private val companyRepository: CompanyRepository,
    private val jobRoleRepository: JobRoleRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val clockService: ClockService,
) {
    @Transactional(readOnly = true)
    fun getMe(userId: Long): MeResponse {
        requireUser(userId)
        val profile = userProfileRepository.findById(userId).orElseGet { defaultProfile(userId) }
        val settings = userSettingsRepository.findById(userId).orElseGet { defaultSettings(userId) }

        return MeResponse(
            profile = UserProfileMapper.toProfileDto(profile),
            settings = UserProfileMapper.toSettingsDto(settings),
            activeResumeVersionSummary = findActiveResumeVersionSummary(userId),
            targetCompanies = listTargetCompanies(userId),
        )
    }

    @Transactional
    fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileDto {
        requireUser(userId)
        if (request.jobRoleId != null && !jobRoleRepository.existsById(request.jobRoleId)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid jobRoleId: ${request.jobRoleId}")
        }

        val existing = userProfileRepository.findById(userId).orElseGet { defaultProfile(userId) }
        val now = clockService.now()
        val updated = UserProfileEntity(
            userId = userId,
            nickname = request.nickname ?: existing.nickname,
            jobRoleId = request.jobRoleId ?: existing.jobRoleId,
            yearsOfExperience = request.yearsOfExperience ?: existing.yearsOfExperience,
            avgScore = existing.avgScore,
            archivedQuestionCount = existing.archivedQuestionCount,
            answerVisibilityDefault = existing.answerVisibilityDefault,
            createdAt = existing.createdAt,
            updatedAt = now,
        )
        return UserProfileMapper.toProfileDto(userProfileRepository.save(updated))
    }

    @Transactional
    fun updateSettings(userId: Long, request: UpdateSettingsRequest): SettingsDto {
        requireUser(userId)

        val existing = userSettingsRepository.findById(userId).orElseGet { defaultSettings(userId) }
        val targetScore = request.targetScoreThreshold ?: existing.targetScoreThreshold
        val passScore = request.passScoreThreshold ?: existing.passScoreThreshold
        if (passScore > targetScore) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "passScoreThreshold cannot exceed targetScoreThreshold")
        }

        val now = clockService.now()
        val updated = UserSettingsEntity(
            userId = userId,
            targetScoreThreshold = targetScore,
            passScoreThreshold = passScore,
            retryEnabled = request.retryEnabled ?: existing.retryEnabled,
            dailyQuestionCount = request.dailyQuestionCount ?: existing.dailyQuestionCount,
            createdAt = existing.createdAt,
            updatedAt = now,
        )
        return UserProfileMapper.toSettingsDto(userSettingsRepository.save(updated))
    }

    @Transactional
    fun replaceTargetCompanies(userId: Long, request: ReplaceTargetCompaniesRequest): TargetCompaniesResponse {
        requireUser(userId)
        val inputCompanies = request.companies
        val uniqueCompanyIds = inputCompanies.map { it.companyId }.toSet()
        if (uniqueCompanyIds.size != inputCompanies.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate companyId entries are not allowed")
        }

        val existingCompanies = companyRepository.findAllById(uniqueCompanyIds).associateBy { it.id }
        if (existingCompanies.size != uniqueCompanyIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more companyId values are invalid")
        }

        userTargetCompanyRepository.deleteByIdUserId(userId)
        val now = clockService.now()
        val newRows = inputCompanies.map {
            UserTargetCompanyEntity(
                id = UserTargetCompanyId(userId = userId, companyId = it.companyId),
                priorityOrder = it.priorityOrder,
                createdAt = now,
            )
        }
        userTargetCompanyRepository.saveAll(newRows)
        return TargetCompaniesResponse(listTargetCompanies(userId))
    }

    private fun requireUser(userId: Long) {
        if (!userRepository.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $userId")
        }
    }

    private fun defaultProfile(userId: Long): UserProfileEntity {
        val now = clockService.now()
        return UserProfileEntity(
            userId = userId,
            nickname = null,
            jobRoleId = null,
            yearsOfExperience = null,
            avgScore = null,
            archivedQuestionCount = 0,
            answerVisibilityDefault = "private",
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun defaultSettings(userId: Long): UserSettingsEntity {
        val now = clockService.now()
        return UserSettingsEntity(
            userId = userId,
            targetScoreThreshold = 80,
            passScoreThreshold = 60,
            retryEnabled = true,
            dailyQuestionCount = 1,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun listTargetCompanies(userId: Long): List<TargetCompanyDto> {
        val targets = userTargetCompanyRepository.findByIdUserIdOrderByPriorityOrderAsc(userId)
        if (targets.isEmpty()) {
            return emptyList()
        }

        val companyMap = companyRepository.findAllById(targets.map { it.id.companyId }).associateBy { it.id }
        return targets.mapNotNull { target ->
            val company = companyMap[target.id.companyId] ?: return@mapNotNull null
            TargetCompanyDto(
                companyId = company.id,
                companyName = company.name,
                priorityOrder = target.priorityOrder,
            )
        }
    }

    private fun findActiveResumeVersionSummary(userId: Long): ActiveResumeVersionSummaryDto? {
        val resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId)
        if (resumes.isEmpty()) {
            return null
        }

        val resumeMap = resumes.associateBy { it.id }
        val versions = resumeVersionRepository.findByResumeIdInOrderByResumeIdAscVersionNoAsc(resumeMap.keys.toList())
        val activeVersion = versions.lastOrNull { it.isActive } ?: return null
        val resume = resumeMap[activeVersion.resumeId] ?: return null

        return ActiveResumeVersionSummaryDto(
            resumeId = resume.id,
            resumeTitle = resume.title,
            versionId = activeVersion.id,
            versionNo = activeVersion.versionNo,
            uploadedAt = activeVersion.uploadedAt,
        )
    }
}
