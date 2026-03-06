package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class QuestionCompanyId(
    @Column(name = "question_id")
    val questionId: Long = 0,
    @Column(name = "company_id")
    val companyId: Long = 0,
) : Serializable
