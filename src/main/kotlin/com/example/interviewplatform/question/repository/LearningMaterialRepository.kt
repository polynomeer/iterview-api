package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.LearningMaterialEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LearningMaterialRepository : JpaRepository<LearningMaterialEntity, Long>
