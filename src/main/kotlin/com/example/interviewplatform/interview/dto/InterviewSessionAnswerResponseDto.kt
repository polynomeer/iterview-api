package com.example.interviewplatform.interview.dto

import com.example.interviewplatform.answer.dto.SubmitAnswerResponseDto

data class InterviewSessionAnswerResponseDto(
    val sessionId: Long,
    val sessionQuestionId: Long,
    val status: String,
    val answer: SubmitAnswerResponseDto,
    val nextQuestion: InterviewSessionQuestionDto?,
    val summary: InterviewSessionSummaryDto,
)
