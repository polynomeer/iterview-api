package com.example.interviewplatform.resume.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.resume.dto.ActivateResumeVersionResponse
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
import com.example.interviewplatform.resume.service.ResumeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Resume")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/resume-versions")
class ResumeVersionController(
    private val resumeService: ResumeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/{versionId}")
    @Operation(summary = "Get resume version detail")
    fun getResumeVersion(@PathVariable versionId: Long): ResumeVersionDto =
        resumeService.getResumeVersion(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/extraction")
    @Operation(summary = "Get resume version extraction status")
    fun getResumeVersionExtraction(@PathVariable versionId: Long): ResumeVersionExtractionDto =
        resumeService.getResumeVersionExtraction(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/profile")
    @Operation(summary = "Get extracted profile for resume version")
    fun getResumeVersionProfile(@PathVariable versionId: Long): ResumeProfileSnapshotResponseDto =
        resumeService.getResumeVersionProfile(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/contacts")
    @Operation(summary = "Get extracted contacts for resume version")
    fun getResumeVersionContacts(@PathVariable versionId: Long): ResumeContactPointResponseDto =
        resumeService.listResumeVersionContacts(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/competencies")
    @Operation(summary = "Get extracted competencies for resume version")
    fun getResumeVersionCompetencies(@PathVariable versionId: Long): ResumeCompetencyItemResponseDto =
        resumeService.listResumeVersionCompetencies(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/skills")
    @Operation(summary = "Get extracted skills for resume version")
    fun getResumeVersionSkills(@PathVariable versionId: Long): ResumeSkillSnapshotResponseDto =
        resumeService.listResumeVersionSkills(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/experiences")
    @Operation(summary = "Get extracted experiences for resume version")
    fun getResumeVersionExperiences(@PathVariable versionId: Long): ResumeExperienceSnapshotResponseDto =
        resumeService.listResumeVersionExperiences(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/projects")
    @Operation(summary = "Get extracted projects for resume version")
    fun getResumeVersionProjects(@PathVariable versionId: Long): ResumeProjectSnapshotResponseDto =
        resumeService.listResumeVersionProjects(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/achievements")
    @Operation(summary = "Get extracted achievements for resume version")
    fun getResumeVersionAchievements(@PathVariable versionId: Long): ResumeAchievementItemResponseDto =
        resumeService.listResumeVersionAchievements(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/education")
    @Operation(summary = "Get extracted education items for resume version")
    fun getResumeVersionEducation(@PathVariable versionId: Long): ResumeEducationItemResponseDto =
        resumeService.listResumeVersionEducation(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/certifications")
    @Operation(summary = "Get extracted certifications for resume version")
    fun getResumeVersionCertifications(@PathVariable versionId: Long): ResumeCertificationItemResponseDto =
        resumeService.listResumeVersionCertifications(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/awards")
    @Operation(summary = "Get extracted awards for resume version")
    fun getResumeVersionAwards(@PathVariable versionId: Long): ResumeAwardItemResponseDto =
        resumeService.listResumeVersionAwards(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/risks")
    @Operation(summary = "Get extracted risk items for resume version")
    fun getResumeVersionRisks(@PathVariable versionId: Long): ResumeRiskItemResponseDto =
        resumeService.listResumeVersionRisks(currentUserProvider.currentUserId(), versionId)

    @GetMapping("/{versionId}/file")
    @Operation(summary = "Download resume version file")
    fun downloadResumeVersionFile(@PathVariable versionId: Long): ResponseEntity<Resource> {
        val file = resumeService.downloadResumeVersionFile(currentUserProvider.currentUserId(), versionId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${file.fileName}\"")
            .header(HttpHeaders.CONTENT_TYPE, file.contentType)
            .body(file.resource)
    }

    @PostMapping("/{versionId}/re-extract")
    @Operation(summary = "Re-run structured extraction for a resume version")
    fun reExtract(@PathVariable versionId: Long): ResumeVersionExtractionDto =
        resumeService.reExtractResumeVersion(currentUserProvider.currentUserId(), versionId)

    @PostMapping("/{versionId}/activate")
    @Operation(summary = "Activate resume version")
    fun activate(@PathVariable versionId: Long): ActivateResumeVersionResponse =
        resumeService.activateResumeVersion(currentUserProvider.currentUserId(), versionId)
}
