package com.example.interviewplatform.auth.service

import com.example.interviewplatform.auth.dto.AuthMeResponse
import com.example.interviewplatform.auth.dto.AuthTokenResponse
import com.example.interviewplatform.auth.dto.AuthUserDto
import com.example.interviewplatform.auth.dto.LoginRequest
import com.example.interviewplatform.auth.dto.SignupRequest
import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.user.entity.UserEntity
import com.example.interviewplatform.user.enums.UserStatus
import com.example.interviewplatform.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val clockService: ClockService,
) {
    @Transactional
    fun signup(request: SignupRequest): AuthTokenResponse {
        val email = request.email.trim().lowercase()
        if (userRepository.findByEmail(email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already in use")
        }

        val now = clockService.now()
        val user = userRepository.save(
            UserEntity(
                email = email,
                passwordHash = passwordEncoder.encode(request.password),
                provider = PROVIDER_LOCAL,
                providerUserId = null,
                status = UserStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return toAuthResponse(user)
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthTokenResponse {
        val email = request.email.trim().lowercase()
        val user = userRepository.findByEmail(email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        val hash = user.passwordHash
        if (hash.isNullOrBlank() || !passwordEncoder.matches(request.password, hash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        if (user.status != UserStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User is not active")
        }

        return toAuthResponse(user)
    }

    @Transactional(readOnly = true)
    fun me(userId: Long): AuthMeResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found") }
        return AuthMeResponse(id = user.id, email = user.email)
    }

    private fun toAuthResponse(user: UserEntity): AuthTokenResponse = AuthTokenResponse(
        accessToken = tokenService.issueToken(user.id, user.email),
        user = AuthUserDto(
            id = user.id,
            email = user.email,
            status = user.status.name,
        ),
    )

    private companion object {
        const val PROVIDER_LOCAL = "local"
    }
}
