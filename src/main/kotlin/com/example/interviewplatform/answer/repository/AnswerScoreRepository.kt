package com.example.interviewplatform.answer.repository

import com.example.interviewplatform.answer.entity.AnswerScoreEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AnswerScoreRepository : JpaRepository<AnswerScoreEntity, Long>
