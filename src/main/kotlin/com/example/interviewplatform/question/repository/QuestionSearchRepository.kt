package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.entity.QuestionEntity

interface QuestionSearchRepository {
    fun search(filter: QuestionSearchFilter): List<QuestionEntity>
}
