package com.example.interviewplatform.skill.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.skill.dto.SkillGapItemDto
import com.example.interviewplatform.skill.dto.SkillProgressItemDto
import com.example.interviewplatform.skill.dto.SkillRadarResponseDto
import com.example.interviewplatform.skill.service.SkillRadarService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Skills")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/skills")
class SkillController(
    private val skillRadarService: SkillRadarService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/radar")
    @Operation(summary = "Get skill radar")
    fun getRadar(): SkillRadarResponseDto = skillRadarService.getRadar(currentUserProvider.currentUserId())

    @GetMapping("/gaps")
    @Operation(summary = "Get skill gaps")
    fun getGaps(): List<SkillGapItemDto> = skillRadarService.getGaps(currentUserProvider.currentUserId())

    @GetMapping("/progress")
    @Operation(summary = "Get skill progress snapshot")
    fun getProgress(): List<SkillProgressItemDto> = skillRadarService.getProgress(currentUserProvider.currentUserId())
}
