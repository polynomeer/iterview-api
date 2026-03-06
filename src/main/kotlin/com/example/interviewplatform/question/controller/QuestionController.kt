package com.example.interviewplatform.question.controller

import com.example.interviewplatform.question.dto.QuestionSummaryDto
import com.example.interviewplatform.question.service.QuestionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
) {
    @GetMapping
    fun listQuestions(): List<QuestionSummaryDto> = questionService.listActiveQuestions()
}
