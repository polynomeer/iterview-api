package com.example.interviewplatform.resume.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
import com.example.interviewplatform.resume.dto.CreateResumeRequest
import com.example.interviewplatform.resume.dto.CreateResumeVersionRequest
import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.dto.ResumeExperienceSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeRiskItemResponseDto
import com.example.interviewplatform.resume.dto.ResumeSkillSnapshotResponseDto
import com.example.interviewplatform.resume.dto.ResumeVersionDto
import com.example.interviewplatform.resume.entity.ResumeExperienceSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeRiskItemEntity
import com.example.interviewplatform.resume.entity.ResumeSkillSnapshotEntity
import com.example.interviewplatform.resume.entity.ResumeEntity
import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.example.interviewplatform.resume.mapper.ResumeIntelligenceMapper
import com.example.interviewplatform.resume.mapper.ResumeMapper
import com.example.interviewplatform.resume.repository.ResumeExperienceSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.skill.repository.SkillCategoryRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.example.interviewplatform.user.repository.UserRepository
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
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val resumeFileStorageService: ResumeFileStorageService,
    private val resumeDocumentParser: ResumeDocumentParser,
    private val resumeSignalExtractionService: ResumeSignalExtractionService,
    private val skillRepository: SkillRepository,
    private val skillCategoryRepository: SkillCategoryRepository,
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
            persistExtractedSignals(created, now)
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
            persistExtractedSignals(completedVersion, completedAt)
            ResumeMapper.toVersionDto(completedVersion)
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

    private fun persistExtractedSignals(version: ResumeVersionEntity, now: java.time.Instant) {
        val extracted = resumeSignalExtractionService.extract(version)
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
                projectName = experience.projectName,
                summaryText = experience.summaryText,
                impactText = experience.impactText,
                sourceText = experience.sourceText,
                riskLevel = experience.riskLevel,
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
        summaryText: String? = this.summaryText,
        parsingStatus: String = this.parsingStatus,
        parseCompletedAt: java.time.Instant? = this.parseCompletedAt,
        parseErrorMessage: String? = this.parseErrorMessage,
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
        isActive = isActive,
        uploadedAt = uploadedAt,
        createdAt = createdAt,
    )

    private companion object {
        const val PARSING_STATUS_PENDING = "pending"
        const val PARSING_STATUS_COMPLETED = "completed"
        const val PARSING_STATUS_FAILED = "failed"
    }
}

data class ResumeVersionFileDownload(
    val resource: Resource,
    val fileName: String,
    val contentType: String,
)
