package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class QuestionRoleId(
    @Column(name = "question_id")
    val questionId: Long = 0,
    @Column(name = "job_role_id")
    val jobRoleId: Long = 0,
) : Serializable
