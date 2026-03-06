package com.example.interviewplatform.user.dto

data class ProfileDto(
    val userId: Long,
    val nickname: String?,
    val jobRoleId: Long?,
    val yearsOfExperience: Int?,
)
