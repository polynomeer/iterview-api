package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.dailycard.dto.HomeResponseDto
import com.example.interviewplatform.dailycard.service.HomeService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Home")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/home")
class HomeController(
    private val homeService: HomeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "Get home payload with today question and retry questions")
    fun getHome(): HomeResponseDto = homeService.getHome(currentUserProvider.currentUserId())
}
