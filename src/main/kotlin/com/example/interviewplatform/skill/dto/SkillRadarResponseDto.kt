package com.example.interviewplatform.skill.dto

import java.time.Instant

data class SkillRadarResponseDto(
    val categories: List<SkillRadarCategoryDto>,
    val updatedAt: Instant,
)
