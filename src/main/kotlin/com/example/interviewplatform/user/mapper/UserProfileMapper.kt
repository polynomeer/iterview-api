package com.example.interviewplatform.user.mapper

import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.SettingsDto
import com.example.interviewplatform.user.entity.UserProfileEntity
import com.example.interviewplatform.user.entity.UserSettingsEntity

object UserProfileMapper {
    fun toProfileDto(entity: UserProfileEntity): ProfileDto = ProfileDto(
        nickname = entity.nickname,
        jobRoleId = entity.jobRoleId,
        yearsOfExperience = entity.yearsOfExperience,
    )

    fun toSettingsDto(entity: UserSettingsEntity): SettingsDto = SettingsDto(
        targetScoreThreshold = entity.targetScoreThreshold,
        passScoreThreshold = entity.passScoreThreshold,
        retryEnabled = entity.retryEnabled,
        dailyQuestionCount = entity.dailyQuestionCount,
    )
}
