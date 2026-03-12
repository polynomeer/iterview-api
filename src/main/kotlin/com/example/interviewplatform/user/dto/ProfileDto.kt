package com.example.interviewplatform.user.dto

data class ProfileDto(
    val nickname: String?,
    val jobRoleId: Long?,
    val yearsOfExperience: Int?,
    val profileImageUrl: String?,
    val profileImageFileName: String?,
    val profileImageContentType: String?,
    val profileImageUploadedAt: java.time.Instant?,
)
