package com.example.interviewplatform.user.mapper

import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.entity.UserEntity

object UserProfileMapper {
    fun toDto(user: UserEntity): ProfileDto = ProfileDto(
        userId = user.id,
        nickname = null,
        jobRoleId = null,
        yearsOfExperience = null,
    )
}
