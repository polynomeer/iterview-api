package com.example.interviewplatform.user.dto

data class MeResponse(
    val profile: ProfileDto,
    val settings: SettingsDto,
    val activeResumeVersionSummary: ActiveResumeVersionSummaryDto?,
    val targetCompanies: List<TargetCompanyDto>,
)
