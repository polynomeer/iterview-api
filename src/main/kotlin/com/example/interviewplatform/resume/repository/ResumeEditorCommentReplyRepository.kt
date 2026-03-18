package com.example.interviewplatform.resume.repository

import com.example.interviewplatform.resume.entity.ResumeEditorCommentReplyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResumeEditorCommentReplyRepository : JpaRepository<ResumeEditorCommentReplyEntity, Long> {
    fun findByResumeEditorCommentThreadIdInOrderByCreatedAtAsc(threadIds: List<Long>): List<ResumeEditorCommentReplyEntity>
}
