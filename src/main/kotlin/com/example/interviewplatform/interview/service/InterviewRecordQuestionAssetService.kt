package com.example.interviewplatform.interview.service

import com.example.interviewplatform.interview.entity.InterviewRecordAnswerEntity
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import com.example.interviewplatform.interview.entity.InterviewRecordQuestionEntity
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.repository.CategoryRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class InterviewRecordQuestionAssetService(
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val questionRepository: QuestionRepository,
    private val categoryRepository: CategoryRepository,
) {
    @Transactional
    fun ensureLinkedQuestionAssets(
        record: InterviewRecordEntity,
        questions: List<InterviewRecordQuestionEntity>,
        answersByQuestionId: Map<Long, InterviewRecordAnswerEntity>,
        now: Instant,
    ): List<InterviewRecordQuestionEntity> {
        if (questions.isEmpty()) {
            return emptyList()
        }
        val categoryIdsByName = categoryRepository.findAll().associate { it.name to it.id }
        return questions.map { question ->
            if (question.linkedQuestionId != null) {
                question
            } else {
                val linkedQuestion = questionRepository.save(
                    QuestionEntity(
                        authorUserId = record.userId,
                        categoryId = resolveCategoryId(question.questionType, categoryIdsByName),
                        title = question.text,
                        body = buildQuestionBody(record, question, answersByQuestionId[question.id]),
                        questionType = normalizeQuestionType(question.questionType),
                        difficultyLevel = QUESTION_DIFFICULTY_MEDIUM,
                        sourceType = QUESTION_SOURCE_TYPE_REAL_INTERVIEW_IMPORT,
                        qualityStatus = QUESTION_QUALITY_STATUS_APPROVED,
                        visibility = QUESTION_VISIBILITY_PRIVATE,
                        expectedAnswerSeconds = DEFAULT_EXPECTED_ANSWER_SECONDS,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                interviewRecordQuestionRepository.save(
                    InterviewRecordQuestionEntity(
                        id = question.id,
                        interviewRecordId = question.interviewRecordId,
                        segmentStartId = question.segmentStartId,
                        segmentEndId = question.segmentEndId,
                        text = question.text,
                        normalizedText = question.normalizedText,
                        questionType = question.questionType,
                        topicTagsJson = question.topicTagsJson,
                        intentTagsJson = question.intentTagsJson,
                        derivedFromResumeSection = question.derivedFromResumeSection,
                        derivedFromResumeRecordType = question.derivedFromResumeRecordType,
                        derivedFromResumeRecordId = question.derivedFromResumeRecordId,
                        derivedFromJobPostingSection = question.derivedFromJobPostingSection,
                        linkedQuestionId = linkedQuestion.id,
                        parentQuestionId = question.parentQuestionId,
                        structuringSource = question.structuringSource,
                        orderIndex = question.orderIndex,
                        createdAt = question.createdAt,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun buildQuestionBody(
        record: InterviewRecordEntity,
        question: InterviewRecordQuestionEntity,
        answer: InterviewRecordAnswerEntity?,
    ): String = buildString {
        append("Imported from a recorded practical interview")
        record.companyName?.let { append(" at $it") }
        record.roleName?.let { append(" for role $it") }
        append(".")
        append("\nQuestion type: ${question.questionType}.")
        answer?.summary?.takeIf { it.isNotBlank() }?.let {
            append("\nImported answer summary: $it")
        }
        answer?.text?.takeIf { it.isNotBlank() }?.let {
            append("\nImported answer transcript: $it")
        }
    }

    private fun resolveCategoryId(questionType: String, categoryIdsByName: Map<String, Long>): Long =
        when {
            questionType.contains("system", ignoreCase = true) -> categoryIdsByName.getValue(CATEGORY_ARCHITECTURE)
            questionType.contains("architecture", ignoreCase = true) -> categoryIdsByName.getValue(CATEGORY_ARCHITECTURE)
            questionType.contains("database", ignoreCase = true) -> categoryIdsByName.getValue(CATEGORY_DATABASE)
            questionType.contains("sql", ignoreCase = true) -> categoryIdsByName.getValue(CATEGORY_DATABASE)
            questionType.contains("test", ignoreCase = true) -> categoryIdsByName.getValue(CATEGORY_TESTING)
            else -> categoryIdsByName.getValue(CATEGORY_BACKEND_ENGINEERING)
        }

    private fun normalizeQuestionType(questionType: String): String =
        if (questionType.contains("behavior", ignoreCase = true)) QUESTION_TYPE_BEHAVIORAL else QUESTION_TYPE_TECHNICAL

    private companion object {
        const val CATEGORY_BACKEND_ENGINEERING = "Backend Engineering"
        const val CATEGORY_ARCHITECTURE = "Architecture"
        const val CATEGORY_DATABASE = "Database"
        const val CATEGORY_TESTING = "Testing"
        const val QUESTION_TYPE_BEHAVIORAL = "behavioral"
        const val QUESTION_TYPE_TECHNICAL = "technical"
        const val QUESTION_DIFFICULTY_MEDIUM = "MEDIUM"
        const val QUESTION_SOURCE_TYPE_REAL_INTERVIEW_IMPORT = "real_interview_import"
        const val QUESTION_QUALITY_STATUS_APPROVED = "approved"
        const val QUESTION_VISIBILITY_PRIVATE = "private"
        const val DEFAULT_EXPECTED_ANSWER_SECONDS = 180
    }
}
