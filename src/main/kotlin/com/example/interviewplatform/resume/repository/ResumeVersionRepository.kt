package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ResumeVersionRepository : JpaRepository<ResumeVersionEntity, Long> {
    fun findByResumeIdOrderByVersionNoAsc(resumeId: Long): List<ResumeVersionEntity>

    fun findByResumeIdInOrderByResumeIdAscVersionNoAsc(resumeIds: List<Long>): List<ResumeVersionEntity>

    fun findTopByResumeIdOrderByVersionNoDesc(resumeId: Long): ResumeVersionEntity?

    @Modifying
    @Query("update ResumeVersionEntity rv set rv.isActive = false where rv.resumeId = :resumeId and rv.isActive = true")
    fun deactivateActiveByResumeId(resumeId: Long): Int

    @Modifying
    @Query("update ResumeVersionEntity rv set rv.isActive = true where rv.id = :versionId")
    fun activateByVersionId(versionId: Long): Int

    @Query(
        """
        select r.userId
        from ResumeVersionEntity rv
        join ResumeEntity r on r.id = rv.resumeId
        where rv.id = :versionId
        """,
    )
    fun findResumeOwnerIdByVersionId(versionId: Long): Long?
}
