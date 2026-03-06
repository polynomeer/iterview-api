package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.CategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<CategoryEntity, Long>
