package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
import com.example.interviewplatform.resume.dto.CreateResumeRequest
import com.example.interviewplatform.resume.dto.CreateResumeVersionRequest
import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeAchievementItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeAwardItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeCertificationItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeCompetencyItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeContactPointResponseDto
import com.example.interviewplatform.resume.dto.ResumeEducationItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeExperienceSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeProfileSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeProjectSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeRiskItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeSkillSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeVersionExtractionDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.entity.ResumeAchievementItemEntity
import com.example.interviewplatform.resume.entity.ResumeAwardItemEntity
import com.example.interviewplatform.resume.entity.ResumeCertificationItemEntity
import com.example.interviewplatform.resume.entity.ResumeCompetencyItemEntity
import com.example.interviewplatform.resume.entity.ResumeContactPointEntity
import com.example.interviewplatform.resume.entity.ResumeEducationItemEntity
import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeEntity
import com.example.interviewplatform.resume.entity.ResumeProfileSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeProjectSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeRiskItemEntity
import com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.example.interviewplatform.resume.mapper.ResumeIntelligenceMapper
import com.example.interviewplatform.resume.mapper.ResumeMapper
import com.example.interviewplatform.resume.repository.ResumeAchievementItemRepository
import com.example.interviewplatform.resume.repository.ResumeAwardItemRepository
import com.example.interviewplatform.resume.repository.ResumeCertificationItemRepository
import com.example.interviewplatform.resume.repository.ResumeCompetencyItemRepository
import com.example.interviewplatform.resume.repository.ResumeContactPointRepository
import com.example.interviewplatform.resume.repository.ResumeEducationItemRepository
import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProfileSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeProjectSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.skill.repository.SkillCategoryRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.example.interviewplatform.user.repository.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@Service
class ResumeService(
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeExperienceSnapshotRepository: ResumeExperienceSnapshotRepository,
    private val resumeProfileSnapshotRepository: ResumeProfileSnapshotRepository,
    private val resumeContactPointRepository: ResumeContactPointRepository,
    private val resumeCompetencyItemRepository: ResumeCompetencyItemRepository,
    private val resumeProjectSnapshotRepository: ResumeProjectSnapshotRepository,
    private val resumeAchievementItemRepository: ResumeAchievementItemRepository,
    private val resumeEducationItemRepository: ResumeEducationItemRepository,
    private val resumeCertificationItemRepository: ResumeCertificationItemRepository,
    private val resumeAwardItemRepository: ResumeAwardItemRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val resumeFileStorageService: ResumeFileStorageService,
    private val resumeDocumentParser: ResumeDocumentParser,
    private val resumeSignalExtractionService: ResumeSignalExtractionService,
    private val skillRepository: SkillRepository,
    private val skillCategoryRepository: SkillCategoryRepository,
    private val userRepository: UserRepository,
    private val clockService: ClockService,
    private val entityManager: EntityManager,
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

    @Transactional(readOnly = true)
    fun getLatestResume(userId: Long): ResumeDto {
        requireUser(userId)
        val resume = resumeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found for user: $userId")
        val versions = resumeVersionRepository.findByResumeIdOrderByVersionNoAsc(resume.id)
        return ResumeMapper.toResumeDto(resume, versions)
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
        val fileName = request.fileName?.trim()?.takeIf { it.isNotEmpty() } ?: inferFileName(request.fileUrl)
        val fileType = request.fileType?.trim()?.takeIf { it.isNotEmpty() } ?: inferFileType(fileName, request.fileUrl)
        val parsingStatus = resolveParsingStatus(request)

        val created = resumeVersionRepository.save(
            ResumeVersionEntity(
                resumeId = resume.id,
                versionNo = nextVersionNo,
                fileUrl = request.fileUrl,
                fileName = fileName,
                fileType = fileType,
                storageKey = null,
                fileSizeBytes = null,
                checksumSha256 = null,
                rawText = request.rawText,
                parsedJson = request.parsedJson,
                summaryText = request.summaryText,
                parsingStatus = parsingStatus,
                parseStartedAt = if (parsingStatus == PARSING_STATUS_COMPLETED) now else null,
                parseCompletedAt = if (parsingStatus == PARSING_STATUS_COMPLETED) now else null,
                parseErrorMessage = null,
                isActive = false,
                uploadedAt = now,
                createdAt = now,
            ),
        )
        if (parsingStatus == PARSING_STATUS_COMPLETED) {
            return ResumeMapper.toVersionDto(extractAndPersistSignals(created))
        }
        return ResumeMapper.toVersionDto(created)
    }

    @Transactional
    fun uploadResumeVersion(userId: Long, resumeId: Long, file: MultipartFile, summaryText: String?): ResumeVersionDto {
        validatePdf(file)
        val resume = resumeRepository.findByIdAndUserId(resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume not found: $resumeId")

        val latestVersion = resumeVersionRepository.findTopByResumeIdOrderByVersionNoDesc(resume.id)
        val nextVersionNo = (latestVersion?.versionNo ?: 0) + 1
        val now = clockService.now()
        val storedFile = resumeFileStorageService.store(userId, resume.id, nextVersionNo, file, now)

        val initialVersion = resumeVersionRepository.save(
            ResumeVersionEntity(
                resumeId = resume.id,
                versionNo = nextVersionNo,
                fileUrl = null,
                fileName = storedFile.fileName,
                fileType = "application/pdf",
                storageKey = storedFile.storageKey,
                fileSizeBytes = storedFile.fileSizeBytes,
                checksumSha256 = storedFile.checksumSha256,
                rawText = null,
                parsedJson = null,
                summaryText = summaryText?.trim()?.takeIf { it.isNotEmpty() },
                parsingStatus = PARSING_STATUS_PENDING,
                parseStartedAt = now,
                parseCompletedAt = null,
                parseErrorMessage = null,
                isActive = false,
                uploadedAt = now,
                createdAt = now,
            ),
        )

        val versionWithFileUrl = saveResumeVersion(
            initialVersion.copyLike(
                fileUrl = buildResumeVersionFileUrl(initialVersion.id),
            ),
        )

        return try {
            val parsed = resumeDocumentParser.parse(storedFile.absolutePath)
            val completedAt = clockService.now()
            val completedVersion = saveResumeVersion(
                versionWithFileUrl.copyLike(
                    rawText = parsed.rawText,
                    summaryText = summaryText?.trim()?.takeIf { it.isNotEmpty() } ?: parsed.summaryText,
                    parsingStatus = PARSING_STATUS_COMPLETED,
                    parseCompletedAt = completedAt,
                    parseErrorMessage = null,
                ),
            )
            ResumeMapper.toVersionDto(extractAndPersistSignals(completedVersion))
        } catch (ex: Exception) {
            val failedAt = clockService.now()
            val failedVersion = saveResumeVersion(
                versionWithFileUrl.copyLike(
                    parsingStatus = PARSING_STATUS_FAILED,
                    parseCompletedAt = failedAt,
                    parseErrorMessage = ex.message?.take(1000) ?: "Resume parsing failed",
                ),
            )
            ResumeMapper.toVersionDto(failedVersion)
        }
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

    @Transactional(readOnly = true)
    fun listResumeVersionSkills(userId: Long, versionId: Long): ResumeSkillSnapshotResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        val items = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(versionId)
        val skillsById = skillRepository.findAllById(items.mapNotNull { it.skillId }.distinct()).associateBy { it.id }
        val categoryById = skillCategoryRepository.findAllById(skillsById.values.map { it.skillCategoryId }.distinct()).associateBy { it.id }
        return ResumeSkillSnapshotResponseDto(
            resumeVersionId = version.id,
            items = items.map { item ->
                val skill = item.skillId?.let(skillsById::get)
                val category = skill?.let { categoryById[it.skillCategoryId] }
                ResumeIntelligenceMapper.toSkillDto(item, category)
            },
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun getResumeVersion(userId: Long, versionId: Long): ResumeVersionDto =
        ResumeMapper.toVersionDto(requireOwnedVersion(userId, versionId))

    @Transactional(readOnly = true)
    fun getResumeVersionExtraction(userId: Long, versionId: Long): ResumeVersionExtractionDto {
        val version = requireOwnedVersion(userId, versionId)
        return version.toExtractionDto()
    }

    @Transactional(readOnly = true)
    fun getResumeVersionProfile(userId: Long, versionId: Long): ResumeProfileSnapshotResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeProfileSnapshotResponseDto(
            resumeVersionId = version.id,
            item = resumeProfileSnapshotRepository.findByResumeVersionId(versionId)?.let(ResumeIntelligenceMapper::toProfileDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionContacts(userId: Long, versionId: Long): ResumeContactPointResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeContactPointResponseDto(
            resumeVersionId = version.id,
            items = resumeContactPointRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toContactDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionCompetencies(userId: Long, versionId: Long): ResumeCompetencyItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeCompetencyItemResponseDto(
            resumeVersionId = version.id,
            items = resumeCompetencyItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toCompetencyDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionExperiences(userId: Long, versionId: Long): ResumeExperienceSnapshotResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeExperienceSnapshotResponseDto(
            resumeVersionId = version.id,
            items = resumeExperienceSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toExperienceDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionProjects(userId: Long, versionId: Long): ResumeProjectSnapshotResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeProjectSnapshotResponseDto(
            resumeVersionId = version.id,
            items = resumeProjectSnapshotRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toProjectDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionAchievements(userId: Long, versionId: Long): ResumeAchievementItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeAchievementItemResponseDto(
            resumeVersionId = version.id,
            items = resumeAchievementItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toAchievementDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionEducation(userId: Long, versionId: Long): ResumeEducationItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeEducationItemResponseDto(
            resumeVersionId = version.id,
            items = resumeEducationItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toEducationDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionCertifications(userId: Long, versionId: Long): ResumeCertificationItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeCertificationItemResponseDto(
            resumeVersionId = version.id,
            items = resumeCertificationItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toCertificationDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionAwards(userId: Long, versionId: Long): ResumeAwardItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeAwardItemResponseDto(
            resumeVersionId = version.id,
            items = resumeAwardItemRepository.findByResumeVersionIdOrderByDisplayOrderAscIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toAwardDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun listResumeVersionRisks(userId: Long, versionId: Long): ResumeRiskItemResponseDto {
        val version = requireOwnedVersion(userId, versionId)
        return ResumeRiskItemResponseDto(
            resumeVersionId = version.id,
            items = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(versionId)
                .map(ResumeIntelligenceMapper::toRiskDto),
            generatedAt = version.uploadedAt,
        )
    }

    @Transactional(readOnly = true)
    fun downloadResumeVersionFile(userId: Long, versionId: Long): ResumeVersionFileDownload {
        val version = requireOwnedVersion(userId, versionId)
        val storageKey = version.storageKey
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found: $versionId")
        val resource = resumeFileStorageService.load(storageKey)
        if (!resource.exists()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found: $versionId")
        }
        return ResumeVersionFileDownload(
            resource = resource,
            fileName = version.fileName ?: "resume-$versionId.pdf",
            contentType = version.fileType ?: "application/pdf",
        )
    }

    @Transactional
    fun reExtractResumeVersion(userId: Long, versionId: Long): ResumeVersionExtractionDto {
        val version = requireOwnedVersion(userId, versionId)
        if (version.parsingStatus != PARSING_STATUS_COMPLETED || version.rawText.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume raw text is not ready for structured extraction")
        }
        return extractAndPersistSignals(version).toExtractionDto()
    }

    private fun requireUser(userId: Long) {
        if (!userRepository.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: $userId")
        }
    }

    private fun requireOwnedVersion(userId: Long, versionId: Long): ResumeVersionEntity {
        val version = resumeVersionRepository.findById(versionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId") }
        resumeRepository.findByIdAndUserId(version.resumeId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Resume version not found: $versionId")
        return version
    }

    private fun saveResumeVersion(entity: ResumeVersionEntity): ResumeVersionEntity = resumeVersionRepository.save(entity)

    private fun extractAndPersistSignals(version: ResumeVersionEntity): ResumeVersionEntity {
        val startedAt = clockService.now()
        val extractionPending = saveResumeVersion(
            version.copyLike(
                llmExtractionStatus = LLM_EXTRACTION_STATUS_PENDING,
                llmExtractionStartedAt = startedAt,
                llmExtractionCompletedAt = null,
                llmExtractionErrorMessage = null,
            ),
        )
        val extracted = try {
            resumeSignalExtractionService.extract(extractionPending)
        } catch (ex: Exception) {
            val failedAt = clockService.now()
            return saveResumeVersion(
                extractionPending.copyLike(
                    llmExtractionStatus = LLM_EXTRACTION_STATUS_FAILED,
                    llmExtractionCompletedAt = failedAt,
                    llmExtractionErrorMessage = ex.message?.take(1000) ?: "Structured extraction failed",
                ),
            )
        }
        val now = clockService.now()
        resumeRiskItemRepository.deleteByResumeVersionId(version.id)
        resumeAchievementItemRepository.deleteByResumeVersionId(version.id)
        resumeProjectSnapshotRepository.deleteByResumeVersionId(version.id)
        resumeAwardItemRepository.deleteByResumeVersionId(version.id)
        resumeCertificationItemRepository.deleteByResumeVersionId(version.id)
        resumeEducationItemRepository.deleteByResumeVersionId(version.id)
        resumeCompetencyItemRepository.deleteByResumeVersionId(version.id)
        resumeContactPointRepository.deleteByResumeVersionId(version.id)
        resumeProfileSnapshotRepository.deleteByResumeVersionId(version.id)
        resumeExperienceSnapshotRepository.deleteByResumeVersionId(version.id)
        resumeSkillSnapshotRepository.deleteByResumeVersionId(version.id)
        entityManager.flush()
        extracted.profile?.let { profile ->
            resumeProfileSnapshotRepository.save(
                ResumeProfileSnapshotEntity(
                    resumeVersionId = version.id,
                    fullName = profile.fullName.truncateTo(255),
                    headline = profile.headline.truncateTo(255),
                    summaryText = profile.summaryText,
                    locationText = profile.locationText.truncateTo(255),
                    yearsOfExperienceText = profile.yearsOfExperienceText.truncateTo(128),
                    sourceText = profile.sourceText,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        if (extracted.contacts.isNotEmpty()) {
            resumeContactPointRepository.saveAll(
                extracted.contacts.map { contact ->
                    ResumeContactPointEntity(
                        resumeVersionId = version.id,
                        contactType = contact.contactType.truncateTo(64).orEmpty(),
                        label = contact.label.truncateTo(255),
                        valueText = contact.valueText.truncateTo(512),
                        url = contact.url,
                        displayOrder = contact.displayOrder,
                        isPrimary = contact.isPrimary,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
        if (extracted.competencies.isNotEmpty()) {
            resumeCompetencyItemRepository.saveAll(
                extracted.competencies.map { competency ->
                    ResumeCompetencyItemEntity(
                        resumeVersionId = version.id,
                        title = competency.title.truncateTo(255).orEmpty(),
                        description = competency.description,
                        sourceText = competency.sourceText,
                        displayOrder = competency.displayOrder,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
        val skillsByName = skillRepository.findByNameIn(extracted.skills.map { it.skillName }.distinct()).associateBy { it.name }

        val skillEntities = extracted.skills.map { skill ->
            ResumeSkillSnapshotEntity(
                resumeVersionId = version.id,
                skillId = skillsByName[skill.skillName]?.id,
                skillName = skill.skillName,
                sourceText = skill.sourceText,
                confidenceScore = skill.confidenceScore?.let { BigDecimal.valueOf(it) },
                isConfirmed = false,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (skillEntities.isNotEmpty()) {
            resumeSkillSnapshotRepository.saveAll(skillEntities)
        }

        val experienceEntities = extracted.experiences.map { experience ->
            ResumeExperienceSnapshotEntity(
                resumeVersionId = version.id,
                companyName = experience.companyName.truncateTo(255),
                roleName = experience.roleName.truncateTo(255),
                employmentType = experience.employmentType.truncateTo(64),
                startedOn = experience.startedOn,
                endedOn = experience.endedOn,
                isCurrent = experience.isCurrent,
                projectName = experience.projectName.truncateTo(255),
                summaryText = experience.summaryText,
                impactText = experience.impactText,
                sourceText = experience.sourceText,
                riskLevel = experience.riskLevel.truncateTo(32).orEmpty(),
                displayOrder = experience.displayOrder,
                isConfirmed = false,
                createdAt = now,
                updatedAt = now,
            )
        }
        val savedExperiences = if (experienceEntities.isEmpty()) {
            emptyList()
        } else {
            resumeExperienceSnapshotRepository.saveAll(experienceEntities)
        }
        val experienceByDisplayOrder = savedExperiences.associateBy { it.displayOrder }

        val savedProjects = if (extracted.projects.isEmpty()) {
            emptyList()
        } else {
            resumeProjectSnapshotRepository.saveAll(
                extracted.projects.map { project ->
                    ResumeProjectSnapshotEntity(
                        resumeVersionId = version.id,
                        resumeExperienceSnapshotId = project.experienceDisplayOrder?.let(experienceByDisplayOrder::get)?.id,
                        title = project.title.truncateTo(255).orEmpty(),
                        organizationName = project.organizationName.truncateTo(255),
                        roleName = project.roleName.truncateTo(255),
                        summaryText = project.summaryText,
                        techStackText = project.techStackText,
                        startedOn = project.startedOn,
                        endedOn = project.endedOn,
                        displayOrder = project.displayOrder,
                        sourceText = project.sourceText,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
        val projectByDisplayOrder = savedProjects.associateBy { it.displayOrder }

        if (extracted.achievements.isNotEmpty()) {
            resumeAchievementItemRepository.saveAll(
                extracted.achievements.map { achievement ->
                    ResumeAchievementItemEntity(
                        resumeVersionId = version.id,
                        resumeExperienceSnapshotId = achievement.experienceDisplayOrder?.let(experienceByDisplayOrder::get)?.id,
                        resumeProjectSnapshotId = achievement.projectDisplayOrder?.let(projectByDisplayOrder::get)?.id,
                        title = achievement.title.truncateTo(255).orEmpty(),
                        metricText = achievement.metricText.truncateTo(255),
                        impactSummary = achievement.impactSummary,
                        sourceText = achievement.sourceText,
                        severityHint = achievement.severityHint.truncateTo(32),
                        displayOrder = achievement.displayOrder,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        if (extracted.educationItems.isNotEmpty()) {
            resumeEducationItemRepository.saveAll(
                extracted.educationItems.map { item ->
                    ResumeEducationItemEntity(
                        resumeVersionId = version.id,
                        institutionName = item.institutionName.truncateTo(255).orEmpty(),
                        degreeName = item.degreeName.truncateTo(255),
                        fieldOfStudy = item.fieldOfStudy.truncateTo(255),
                        startedOn = item.startedOn,
                        endedOn = item.endedOn,
                        description = item.description,
                        displayOrder = item.displayOrder,
                        sourceText = item.sourceText,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        if (extracted.certificationItems.isNotEmpty()) {
            resumeCertificationItemRepository.saveAll(
                extracted.certificationItems.map { item ->
                    ResumeCertificationItemEntity(
                        resumeVersionId = version.id,
                        name = item.name.truncateTo(255).orEmpty(),
                        issuerName = item.issuerName.truncateTo(255),
                        credentialCode = item.credentialCode.truncateTo(255),
                        issuedOn = item.issuedOn,
                        expiresOn = item.expiresOn,
                        scoreText = item.scoreText.truncateTo(255),
                        displayOrder = item.displayOrder,
                        sourceText = item.sourceText,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        if (extracted.awardItems.isNotEmpty()) {
            resumeAwardItemRepository.saveAll(
                extracted.awardItems.map { item ->
                    ResumeAwardItemEntity(
                        resumeVersionId = version.id,
                        title = item.title.truncateTo(255).orEmpty(),
                        issuerName = item.issuerName.truncateTo(255),
                        awardedOn = item.awardedOn,
                        description = item.description,
                        displayOrder = item.displayOrder,
                        sourceText = item.sourceText,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        val riskEntities = extracted.risks.mapIndexed { index, risk ->
            ResumeRiskItemEntity(
                resumeVersionId = version.id,
                resumeExperienceSnapshotId = savedExperiences.getOrNull(index)?.id,
                linkedQuestionId = null,
                riskType = risk.riskType,
                title = risk.title,
                description = risk.description,
                severity = risk.severity,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (riskEntities.isNotEmpty()) {
            resumeRiskItemRepository.saveAll(riskEntities)
        }
        return saveResumeVersion(
            extractionPending.copyLike(
                parsedJson = extracted.rawExtractionPayload ?: extractionPending.parsedJson,
                llmExtractionStatus = extracted.extractionStatus,
                llmExtractionCompletedAt = now,
                llmExtractionErrorMessage = extracted.extractionErrorMessage,
                llmModel = extracted.llmModel,
                llmPromptVersion = extracted.llmPromptVersion,
                llmExtractionConfidence = extracted.extractionConfidence?.let(BigDecimal::valueOf),
            ),
        )
    }

    private fun resolveParsingStatus(request: CreateResumeVersionRequest): String = when {
        !request.parsedJson.isNullOrBlank() || !request.rawText.isNullOrBlank() || !request.summaryText.isNullOrBlank() ->
            PARSING_STATUS_COMPLETED
        else -> PARSING_STATUS_PENDING
    }

    private fun inferFileName(fileUrl: String?): String? = fileUrl
        ?.substringAfterLast('/', "")
        ?.substringBefore('?')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun inferFileType(fileName: String?, fileUrl: String?): String? {
        val source = fileName ?: fileUrl ?: return null
        val extension = source.substringAfterLast('.', "").lowercase()
        return when (extension.takeIf { it.isNotEmpty() }) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> extension.takeIf { it.isNotEmpty() }
        }
    }

    private fun validatePdf(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume PDF is required")
        }
        val fileName = file.originalFilename.orEmpty().lowercase()
        val contentType = file.contentType.orEmpty().lowercase()
        val isPdf = fileName.endsWith(".pdf") || contentType == "application/pdf"
        if (!isPdf) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF resume uploads are supported")
        }
    }

    private fun buildResumeVersionFileUrl(versionId: Long): String = "/api/resume-versions/$versionId/file"

    private fun ResumeVersionEntity.copyLike(
        fileUrl: String? = this.fileUrl,
        rawText: String? = this.rawText,
        parsedJson: String? = this.parsedJson,
        summaryText: String? = this.summaryText,
        parsingStatus: String = this.parsingStatus,
        parseCompletedAt: java.time.Instant? = this.parseCompletedAt,
        parseErrorMessage: String? = this.parseErrorMessage,
        llmExtractionStatus: String? = this.llmExtractionStatus,
        llmExtractionStartedAt: java.time.Instant? = this.llmExtractionStartedAt,
        llmExtractionCompletedAt: java.time.Instant? = this.llmExtractionCompletedAt,
        llmExtractionErrorMessage: String? = this.llmExtractionErrorMessage,
        llmModel: String? = this.llmModel,
        llmPromptVersion: String? = this.llmPromptVersion,
        llmExtractionConfidence: BigDecimal? = this.llmExtractionConfidence,
    ): ResumeVersionEntity = ResumeVersionEntity(
        id = id,
        resumeId = resumeId,
        versionNo = versionNo,
        fileUrl = fileUrl,
        fileName = fileName,
        fileType = fileType,
        storageKey = storageKey,
        fileSizeBytes = fileSizeBytes,
        checksumSha256 = checksumSha256,
        rawText = rawText,
        parsedJson = parsedJson,
        summaryText = summaryText,
        parsingStatus = parsingStatus,
        parseStartedAt = parseStartedAt,
        parseCompletedAt = parseCompletedAt,
        parseErrorMessage = parseErrorMessage,
        llmExtractionStatus = llmExtractionStatus,
        llmExtractionStartedAt = llmExtractionStartedAt,
        llmExtractionCompletedAt = llmExtractionCompletedAt,
        llmExtractionErrorMessage = llmExtractionErrorMessage,
        llmModel = llmModel,
        llmPromptVersion = llmPromptVersion,
        llmExtractionConfidence = llmExtractionConfidence,
        isActive = isActive,
        uploadedAt = uploadedAt,
        createdAt = createdAt,
    )

    private fun ResumeVersionEntity.toExtractionDto(): ResumeVersionExtractionDto = ResumeVersionExtractionDto(
        resumeVersionId = id,
        rawParsingStatus = parsingStatus,
        llmExtractionStatus = llmExtractionStatus,
        llmModel = llmModel,
        llmPromptVersion = llmPromptVersion,
        startedAt = llmExtractionStartedAt,
        completedAt = llmExtractionCompletedAt,
        errorMessage = llmExtractionErrorMessage,
    )

    private companion object {
        const val PARSING_STATUS_PENDING = "pending"
        const val PARSING_STATUS_COMPLETED = "completed"
        const val PARSING_STATUS_FAILED = "failed"
        const val LLM_EXTRACTION_STATUS_PENDING = "pending"
        const val LLM_EXTRACTION_STATUS_FAILED = "failed"
    }
}

data class ResumeVersionFileDownload(
    val resource: Resource,
    val fileName: String,
    val contentType: String,
)

private fun String?.truncateTo(maxLength: Int): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        if (value.length <= maxLength) value else value.take(maxLength)
    }
