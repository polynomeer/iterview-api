package com.example.interviewplatform.jobposting.repository

import com.example.interviewplatform.jobposting.entity.JobPostingEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JobPostingRepository : JpaRepository<JobPostingEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<JobPostingEntity>

    fun findByIdAndUserId(id: Long, userId: Long): JobPostingEntity?
}
