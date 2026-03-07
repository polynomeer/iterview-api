package com.example.interviewplatform.question.controller

import com.example.interviewplatform.common.service.CurrentUserProvider
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.service.QuestionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/questions")
class QuestionController(
    private val questionService: QuestionService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping
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
    fun getQuestionDetail(@PathVariable questionId: Long): QuestionDetailResponse =
        questionService.getQuestionDetail(questionId, currentUserProvider.currentUserIdOrNull())
}
