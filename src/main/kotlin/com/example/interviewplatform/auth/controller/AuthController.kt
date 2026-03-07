package com.example.interviewplatform.auth.controller

import com.example.interviewplatform.auth.dto.AuthMeResponse
import com.example.interviewplatform.auth.dto.AuthTokenResponse
import com.example.interviewplatform.auth.dto.LoginRequest
import com.example.interviewplatform.auth.dto.SignupRequest
import com.example.interviewplatform.auth.service.AuthService
import com.example.interviewplatform.common.service.CurrentUserProvider
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): AuthTokenResponse = authService.signup(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthTokenResponse = authService.login(request)

    @GetMapping("/me")
    fun me(): AuthMeResponse = authService.me(currentUserProvider.currentUserId())
}
