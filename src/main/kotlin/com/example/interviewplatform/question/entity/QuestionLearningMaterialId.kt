package com.example.interviewplatform.question.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class QuestionLearningMaterialId(
    @Column(name = "question_id")
    val questionId: Long = 0,
    @Column(name = "learning_material_id")
    val learningMaterialId: Long = 0,
) : Serializable
