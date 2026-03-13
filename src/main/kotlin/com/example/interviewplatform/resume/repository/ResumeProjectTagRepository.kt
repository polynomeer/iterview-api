package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeProjectTagEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeProjectTagRepository : JpaRepository<ResumeProjectTagEntity, Long> {
    fun findByResumeProjectSnapshotIdInOrderByResumeProjectSnapshotIdAscDisplayOrderAscIdAsc(
        resumeProjectSnapshotIds: Collection<Long>,
    ): List<ResumeProjectTagEntity>

    fun deleteByResumeProjectSnapshotIdIn(resumeProjectSnapshotIds: Collection<Long>)
}
