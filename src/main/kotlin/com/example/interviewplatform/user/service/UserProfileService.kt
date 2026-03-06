package com.example.interviewplatform.user.service

import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.UpdateProfileRequest
import com.example.interviewplatform.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserProfileService(
    private val userRepository: UserRepository,
) {
    fun getProfile(userId: Long): ProfileDto {
        val user = userRepository.findById(userId).orElseThrow()
        return ProfileDto(user.id, nickname = null, jobRoleId = null, yearsOfExperience = null)
    }

    fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileDto {
        userRepository.findById(userId).orElseThrow()
        return ProfileDto(userId, request.nickname, request.jobRoleId, request.yearsOfExperience)
    }
}
