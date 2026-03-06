package com.example.interviewplatform.user.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ReplaceTargetCompaniesRequest(
    @field:Valid
    @field:Size(max = 20)
    val companies: List<TargetCompanyInput>,
)

data class TargetCompanyInput(
    @field:NotNull
    val companyId: Long,
    @field:NotNull
    @field:Min(1)
    val priorityOrder: Int,
)
