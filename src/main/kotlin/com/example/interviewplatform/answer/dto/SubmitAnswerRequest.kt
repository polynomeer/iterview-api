package com.example.interviewplatform.answer.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(description = "Answer submission payload for a question attempt")
data class SubmitAnswerRequest(
    @field:Schema(description = "Resume version used to frame the answer", nullable = true)
    val resumeVersionId: Long? = null,
    @field:NotBlank
    @field:Schema(description = "Submission mode such as text, skip, or unanswered", example = "text")
    val answerMode: String = "text",
    @field:NotBlank
    @field:Size(max = 20000)
    @field:Schema(description = "Raw answer content submitted by the user", example = "First, I would clarify the constraints...")
    val contentText: String,
)
