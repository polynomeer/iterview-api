package com.example.interviewplatform.answer.controller

import com.example.interviewplatform.answer.dto.AnswerAttemptDetailResponseDto
import com.example.interviewplatform.answer.dto.AnswerAttemptListItemDto
import com.example.interviewplatform.answer.dto.SubmitAnswerRequest
import com.example.interviewplatform.answer.dto.SubmitAnswerResponseDto
import com.example.interviewplatform.common.service.CurrentUserProvider
import jakarta.validation.Valid
import com.example.interviewplatform.answer.service.AnswerService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api")
class AnswerController(
    private val answerService: AnswerService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/questions/{questionId}/answers")
    fun submitAnswer(
        @PathVariable questionId: Long,
        @Valid @RequestBody request: SubmitAnswerRequest,
    ): SubmitAnswerResponseDto = answerService.submitAnswer(currentUserProvider.currentUserId(), questionId, request)

    @GetMapping("/questions/{questionId}/answers")
    fun listAnswers(@PathVariable questionId: Long): List<AnswerAttemptListItemDto> =
        answerService.listQuestionAnswers(currentUserProvider.currentUserId(), questionId)

    @GetMapping("/answer-attempts/{answerAttemptId}")
    fun getAnswerAttempt(@PathVariable answerAttemptId: Long): AnswerAttemptDetailResponseDto =
        answerService.getAnswerAttempt(currentUserProvider.currentUserId(), answerAttemptId)
}
