package com.example.interviewplatform.resume.dto

import java.math.BigDecimal

data class ResumeSkillSnapshotDto(
    val skillId: Long?,
    val skillName: String,
    val skillCategoryCode: String?,
    val skillCategoryName: String?,
    val sourceText: String?,
    val confidenceScore: BigDecimal?,
    val confirmed: Boolean,
)
