package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.entity.TagEntity
import org.springframework.data.jpa.repository.JpaRepository

interface TagRepository : JpaRepository<TagEntity, Long>
