package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.dailycard.dto.OpenDailyCardResponseDto
import com.example.interviewplatform.dailycard.service.DailyCardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Home")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/daily-cards")
class DailyCardController(
    private val dailyCardService: DailyCardService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/{dailyCardId}/open")
    @Operation(summary = "Open daily card")
    fun openCard(@PathVariable dailyCardId: Long): OpenDailyCardResponseDto =
        dailyCardService.openCard(currentUserProvider.currentUserId(), dailyCardId)
}
