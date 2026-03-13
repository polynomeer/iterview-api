package com.example.interviewplatform.interview.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "interview_session_questions")
class InterviewSessionQuestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "interview_session_id", nullable = false)
    val interviewSessionId: Long,
    @Column(name = "question_id")
    val questionId: Long? = null,
    @Column(name = "parent_session_question_id")
    val parentSessionQuestionId: Long? = null,
    @Column(name = "prompt_text")
    val promptText: String? = null,
    @Column(name = "body_text")
    val bodyText: String? = null,
    @Column(name = "question_source_type", nullable = false)
    val questionSourceType: String,
    @Column(name = "order_index", nullable = false)
    val orderIndex: Int,
    @Column(name = "is_follow_up", nullable = false)
    val isFollowUp: Boolean = false,
    @Column(name = "depth", nullable = false)
    val depth: Int = 0,
    @Column(name = "category_name")
    val categoryName: String? = null,
    @Column(name = "tags_json")
    val tagsJson: String? = null,
    @Column(name = "focus_skill_names_json")
    val focusSkillNamesJson: String? = null,
    @Column(name = "resume_context_summary")
    val resumeContextSummary: String? = null,
    @Column(name = "generation_rationale")
    val generationRationale: String? = null,
    @Column(name = "generation_status", nullable = false)
    val generationStatus: String,
    @Column(name = "llm_model")
    val llmModel: String? = null,
    @Column(name = "llm_prompt_version")
    val llmPromptVersion: String? = null,
    @Column(name = "answer_attempt_id")
    val answerAttemptId: Long? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
