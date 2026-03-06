package com.example.interviewplatform.answer.controller

import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.service.AnswerService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/questions/{questionId}/answers")
class AnswerController(
    private val answerService: AnswerService,
) {
    @PostMapping
    fun submitAnswer(
        @PathVariable questionId: Long,
        @RequestBody request: SubmitAnswerRequest,
    ): ScoreSummaryDto = answerService.evaluateAnswer(request.contentText)
}
