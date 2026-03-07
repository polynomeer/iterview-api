package com.example.interviewplatform.dailycard.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.dailycard.dto.HomeResponseDto
import com.example.interviewplatform.dailycard.service.HomeService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/home")
class HomeController(
    private val homeService: HomeService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    fun getHome(): HomeResponseDto = homeService.getHome(currentUserProvider.currentUserId())
}
