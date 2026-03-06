package com.example.interviewplatform.user.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.user.dto.MeResponse
import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.ReplaceTargetCompaniesRequest
import com.example.interviewplatform.user.dto.SettingsDto
import com.example.interviewplatform.user.dto.TargetCompaniesResponse
import com.example.interviewplatform.user.dto.UpdateProfileRequest
import com.example.interviewplatform.user.dto.UpdateSettingsRequest
import com.example.interviewplatform.user.service.UserProfileService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class ProfileController(
    private val userProfileService: UserProfileService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun getMe(): MeResponse = userProfileService.getMe(currentUserProvider.currentUserId())

    @PatchMapping("/profile")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ProfileDto =
        userProfileService.updateProfile(currentUserProvider.currentUserId(), request)

    @PatchMapping("/settings")
    fun updateSettings(@Valid @RequestBody request: UpdateSettingsRequest): SettingsDto =
        userProfileService.updateSettings(currentUserProvider.currentUserId(), request)

    @PutMapping("/target-companies")
    fun replaceTargetCompanies(@Valid @RequestBody request: ReplaceTargetCompaniesRequest): TargetCompaniesResponse =
        userProfileService.replaceTargetCompanies(currentUserProvider.currentUserId(), request)
}
