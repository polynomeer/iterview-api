package com.example.interviewplatform.user.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Current user profile aggregate returned by /api/me")
data class MeResponse(
    @field:Schema(description = "Editable user profile fields")
    val profile: ProfileDto,
    @field:Schema(description = "Practice and scoring settings")
    val settings: SettingsDto,
    @field:Schema(description = "Currently active resume version summary when one exists")
    val activeResumeVersionSummary: ActiveResumeVersionSummaryDto?,
    @field:Schema(description = "Target companies ordered by user priority")
    val targetCompanies: List<TargetCompanyDto>,
)
