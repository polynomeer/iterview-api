package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface ResumeRepository : JpaRepository<ResumeEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<ResumeEntity>

    fun findByIdAndUserId(id: Long, userId: Long): ResumeEntity?

    @Modifying
    @Query("update ResumeEntity r set r.isPrimary = false, r.updatedAt = :updatedAt where r.userId = :userId and r.isPrimary = true")
    fun clearPrimaryForUser(userId: Long, updatedAt: Instant): Int
}
