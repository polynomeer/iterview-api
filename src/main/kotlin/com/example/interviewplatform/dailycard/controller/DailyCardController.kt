package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.dailycard.dto.DailyCardDto
import com.example.interviewplatform.dailycard.service.DailyCardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/home")
class DailyCardController(
    private val dailyCardService: DailyCardService,
) {
    @GetMapping
    fun getHome(): List<DailyCardDto> = dailyCardService.getTodayCards(DEFAULT_USER_ID)

    private companion object {
        const val DEFAULT_USER_ID = 1L
    }
}
