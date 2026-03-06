package com.example.interviewplatform.question.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "question_tags")
class QuestionTagEntity(
    @EmbeddedId
    val id: QuestionTagId,
    val createdAt: Instant,
)
