package com.example.interviewplatform.user.controller

import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.UpdateProfileRequest
import com.example.interviewplatform.user.service.UserProfileService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class ProfileController(
    private val userProfileService: UserProfileService,
) {
    @GetMapping
    fun getMe(): ProfileDto = userProfileService.getProfile(DEFAULT_USER_ID)

    @PatchMapping("/profile")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ProfileDto =
        userProfileService.updateProfile(DEFAULT_USER_ID, request)

    private companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
