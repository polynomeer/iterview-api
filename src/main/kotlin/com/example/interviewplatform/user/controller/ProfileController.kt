package com.example.interviewplatform.user.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.user.dto.MeResponse
import com.example.interviewplatform.user.dto.ProfileDto
import com.example.interviewplatform.user.dto.ProfileImageUploadResponseDto
import com.example.interviewplatform.user.dto.ReplaceTargetCompaniesRequest
import com.example.interviewplatform.user.dto.SettingsDto
import com.example.interviewplatform.user.dto.TargetCompaniesResponse
import com.example.interviewplatform.user.dto.UpdateProfileRequest
import com.example.interviewplatform.user.dto.UpdateSettingsRequest
import com.example.interviewplatform.user.service.UserProfileService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile

@Tag(name = "Profile")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/me")
class ProfileController(
    private val userProfileService: UserProfileService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Get current user profile and settings")
    fun getMe(): MeResponse = userProfileService.getMe(currentUserProvider.currentUserId())

    @PatchMapping("/profile")
    @Operation(summary = "Update current user profile")
    fun updateProfile(@Valid @RequestBody request: UpdateProfileRequest): ProfileDto =
        userProfileService.updateProfile(currentUserProvider.currentUserId(), request)

    @PostMapping("/profile-image", consumes = ["multipart/form-data"])
    @Operation(summary = "Upload current user profile image")
    fun uploadProfileImage(@RequestParam("file") file: MultipartFile): ProfileImageUploadResponseDto =
        userProfileService.uploadProfileImage(currentUserProvider.currentUserId(), file)

    @PatchMapping("/settings")
    @Operation(summary = "Update current user settings")
    fun updateSettings(@Valid @RequestBody request: UpdateSettingsRequest): SettingsDto =
        userProfileService.updateSettings(currentUserProvider.currentUserId(), request)

    @PutMapping("/target-companies")
    @Operation(summary = "Replace target companies")
    fun replaceTargetCompanies(@Valid @RequestBody request: ReplaceTargetCompaniesRequest): TargetCompaniesResponse =
        userProfileService.replaceTargetCompanies(currentUserProvider.currentUserId(), request)
}
