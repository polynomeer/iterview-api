package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeProjectTagEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ResumeProjectTagRepository : JpaRepository<ResumeProjectTagEntity, Long> {
    fun findByResumeProjectSnapshotIdInOrderByResumeProjectSnapshotIdAscDisplayOrderAscIdAsc(
        resumeProjectSnapshotIds: Collection<Long>,
    ): List<ResumeProjectTagEntity>

    @Query(
        """
        select rpt
        from ResumeProjectTagEntity rpt
        join ResumeProjectSnapshotEntity rps on rps.id = rpt.resumeProjectSnapshotId
        where rps.resumeVersionId = :resumeVersionId
        order by rps.displayOrder asc, rpt.displayOrder asc, rpt.id asc
        """,
    )
    fun findByResumeVersionIdOrderByProjectDisplayOrderAscTagDisplayOrderAsc(resumeVersionId: Long): List<ResumeProjectTagEntity>

    fun deleteByResumeProjectSnapshotIdIn(resumeProjectSnapshotIds: Collection<Long>)
}
