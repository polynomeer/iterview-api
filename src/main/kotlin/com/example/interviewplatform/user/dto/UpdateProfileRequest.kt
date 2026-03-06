package com.example.interviewplatform.user.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 50)
    val nickname: String?,
    val jobRoleId: Long?,
    @field:Min(0)
    val yearsOfExperience: Int?,
)
