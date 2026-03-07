package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.dailycard.dto.OpenDailyCardResponseDto
import com.example.interviewplatform.dailycard.service.DailyCardService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/daily-cards")
class DailyCardController(
    private val dailyCardService: DailyCardService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/{dailyCardId}/open")
    fun openCard(@PathVariable dailyCardId: Long): OpenDailyCardResponseDto =
        dailyCardService.openCard(currentUserProvider.currentUserId(), dailyCardId)
}
