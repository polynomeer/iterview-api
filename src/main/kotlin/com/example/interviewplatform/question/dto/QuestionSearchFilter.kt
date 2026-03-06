package com.example.interviewplatform.question.dto

data class QuestionSearchFilter(
    val categoryId: Long? = null,
    val tag: String? = null,
    val companyId: Long? = null,
    val roleId: Long? = null,
    val difficulty: String? = null,
    val status: String? = null,
    val search: String? = null,
)
