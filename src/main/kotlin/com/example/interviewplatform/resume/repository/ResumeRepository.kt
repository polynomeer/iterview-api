package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeRepository : JpaRepository<ResumeEntity, Long> {
    fun findByUserId(userId: Long): List<ResumeEntity>
}
