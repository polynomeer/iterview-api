package com.example.interviewplatform.question.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionReferenceAnswerDto
import com.example.interviewplatform.question.dto.QuestionTreeResponseDto
import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.dto.RecommendedFollowUpDto
import com.example.interviewplatform.question.dto.ResumeBasedQuestionDto
import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.service.QuestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Questions")
@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
    @Operation(summary = "List active questions")
    fun listQuestions(
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) companyId: Long?,
        @RequestParam(required = false) roleId: Long?,
        @RequestParam(required = false) difficulty: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) search: String?,
    ): List<QuestionListItemDto> = questionService.listQuestions(
        QuestionSearchFilter(
            categoryId = categoryId,
            tag = tag,
            companyId = companyId,
            roleId = roleId,
            difficulty = difficulty,
            status = status,
            search = search,
        ),
    )

    @GetMapping("/{questionId}")
    @Operation(summary = "Get question detail")
    fun getQuestionDetail(@PathVariable questionId: Long): QuestionDetailResponse =
        questionService.getQuestionDetail(questionId, currentUserProvider.currentUserIdOrNull())

    @GetMapping("/{questionId}/reference-answers")
    @Operation(summary = "Get curated model answers for a question")
    fun getQuestionReferenceAnswers(@PathVariable questionId: Long): List<QuestionReferenceAnswerDto> =
        questionService.getQuestionReferenceAnswers(questionId, currentUserProvider.currentUserIdOrNull())

    @GetMapping("/{questionId}/learning-materials")
    @Operation(summary = "Get curated learning materials for a question")
    fun getQuestionLearningMaterials(@PathVariable questionId: Long): List<LearningMaterialDto> =
        questionService.getQuestionLearningMaterials(questionId, currentUserProvider.currentUserIdOrNull())

    @GetMapping("/{questionId}/tree")
    @Operation(summary = "Get question follow-up tree")
    fun getQuestionTree(@PathVariable questionId: Long): QuestionTreeResponseDto =
        questionService.getQuestionTree(questionId, currentUserProvider.currentUserIdOrNull())

    @GetMapping("/{questionId}/recommended-followups")
    @Operation(summary = "Get recommended follow-up questions")
    fun getRecommendedFollowUps(@PathVariable questionId: Long): List<RecommendedFollowUpDto> =
        questionService.getRecommendedFollowUps(questionId, currentUserProvider.currentUserIdOrNull())

    @GetMapping("/resume-based")
    @Operation(summary = "Get resume-based question recommendations")
    fun getResumeBasedQuestions(
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): List<ResumeBasedQuestionDto> = questionService.getResumeBasedQuestions(
        userId = currentUserProvider.currentUserId(),
        limit = limit,
    )
}
