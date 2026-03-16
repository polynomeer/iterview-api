package com.example.interviewplatform.question.mapper

import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.dto.QuestionCompanyDto
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionMetadataDto
import com.example.interviewplatform.question.dto.PracticalInterviewQuestionContextDto
import com.example.interviewplatform.question.dto.QuestionReferenceAnswerDto
import com.example.interviewplatform.question.dto.QuestionRoleDto
import com.example.interviewplatform.question.dto.QuestionTagDto
import com.example.interviewplatform.question.dto.UserProgressSummaryDto
import com.example.interviewplatform.question.entity.LearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.entity.QuestionLearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionReferenceAnswerEntity
import com.example.interviewplatform.question.entity.QuestionRoleEntity
import com.example.interviewplatform.question.entity.TagEntity
import com.example.interviewplatform.question.entity.UserQuestionLearningMaterialEntity
import com.example.interviewplatform.question.entity.UserQuestionReferenceAnswerEntity
import com.example.interviewplatform.question.entity.UserQuestionProgressEntity
import com.example.interviewplatform.user.entity.CompanyEntity
import com.example.interviewplatform.user.entity.JobRoleEntity

object QuestionMapper {
    fun toListItemDto(
        question: QuestionEntity,
        categoryName: String?,
        tags: List<QuestionTagDto>,
        companies: List<QuestionCompanyDto>,
        learningMaterials: List<LearningMaterialDto>,
    ): QuestionListItemDto = QuestionListItemDto(
        id = question.id,
        title = question.title,
        questionType = question.questionType,
        difficulty = question.difficultyLevel,
        qualityStatus = question.qualityStatus,
        categoryId = question.categoryId,
        categoryName = categoryName,
        expectedAnswerSeconds = question.expectedAnswerSeconds,
        tags = tags,
        companies = companies,
        learningMaterials = learningMaterials,
    )

    fun toDetailResponse(
        question: QuestionEntity,
        categoryName: String?,
        tags: List<QuestionTagDto>,
        companies: List<QuestionCompanyDto>,
        roles: List<QuestionRoleDto>,
        learningMaterials: List<LearningMaterialDto>,
        referenceAnswers: List<QuestionReferenceAnswerDto>,
        practicalInterviewContext: PracticalInterviewQuestionContextDto?,
        progress: UserQuestionProgressEntity?,
    ): QuestionDetailResponse = QuestionDetailResponse(
        question = QuestionMetadataDto(
            id = question.id,
            title = question.title,
            body = question.body,
            questionType = question.questionType,
            difficulty = question.difficultyLevel,
            sourceType = question.sourceType,
            qualityStatus = question.qualityStatus,
            visibility = question.visibility,
            categoryId = question.categoryId,
            categoryName = categoryName,
            expectedAnswerSeconds = question.expectedAnswerSeconds,
            createdAt = question.createdAt,
            updatedAt = question.updatedAt,
        ),
        tags = tags,
        companies = companies,
        roles = roles,
        learningMaterials = learningMaterials,
        referenceAnswers = referenceAnswers,
        practicalInterviewContext = practicalInterviewContext,
        userProgressSummary = progress?.let {
            UserProgressSummaryDto(
                currentStatus = it.currentStatus,
                latestScore = it.latestScore,
                bestScore = it.bestScore,
                totalAttemptCount = it.totalAttemptCount,
                lastAnsweredAt = it.lastAnsweredAt,
                nextReviewAt = it.nextReviewAt,
                masteryLevel = it.masteryLevel,
            )
        },
    )

    fun toTagDto(tag: TagEntity): QuestionTagDto = QuestionTagDto(
        id = tag.id,
        name = tag.name,
        tagType = tag.tagType,
    )

    fun toCompanyDto(edge: QuestionCompanyEntity, company: CompanyEntity): QuestionCompanyDto = QuestionCompanyDto(
        id = company.id,
        name = company.name,
        relevanceScore = edge.relevanceScore,
        isPastFrequent = edge.isPastFrequent,
        isTrendingRecent = edge.isTrendingRecent,
    )

    fun toRoleDto(edge: QuestionRoleEntity, role: JobRoleEntity): QuestionRoleDto = QuestionRoleDto(
        id = role.id,
        name = role.name,
        relevanceScore = edge.relevanceScore,
    )

    fun toLearningMaterialDto(
        entity: LearningMaterialEntity,
        edge: QuestionLearningMaterialEntity? = null,
    ): LearningMaterialDto {
        val sourceType = if (entity.sourceName == "OpenAI") "ai_generated" else "editorial"
        return LearningMaterialDto(
        id = entity.id,
        title = edge?.labelOverride ?: entity.title,
        materialType = entity.materialType,
        sourceType = sourceType,
        sourceLabel = learningMaterialSourceLabel(sourceType = sourceType, sourceName = entity.sourceName, isUserGenerated = false),
        description = entity.description,
        contentText = entity.contentText,
        contentUrl = entity.contentUrl,
        sourceName = entity.sourceName,
        contentLocale = entity.contentLocale,
        isUserGenerated = false,
        difficultyLevel = entity.difficultyLevel,
        estimatedMinutes = entity.estimatedMinutes,
        isOfficial = entity.isOfficial,
        displayOrder = edge?.displayOrder ?: entity.displayOrderHint,
        relationshipType = edge?.relationshipType,
        labelOverride = edge?.labelOverride,
        relevanceScore = edge?.relevanceScore?.toDouble(),
    )
    }

    fun toReferenceAnswerDto(entity: QuestionReferenceAnswerEntity): QuestionReferenceAnswerDto = QuestionReferenceAnswerDto(
        id = entity.id,
        title = entity.title,
        answerText = entity.answerText,
        answerFormat = entity.answerFormat,
        sourceType = entity.sourceType,
        sourceLabel = referenceAnswerSourceLabel(entity.sourceType, isUserGenerated = false),
        contentLocale = entity.contentLocale,
        isUserGenerated = false,
        targetRoleId = entity.targetRoleId,
        companyId = entity.companyId,
        isOfficial = entity.isOfficial,
        displayOrder = entity.displayOrder,
    )

    fun toLearningMaterialDto(entity: UserQuestionLearningMaterialEntity): LearningMaterialDto = LearningMaterialDto(
        id = entity.id,
        title = entity.labelOverride ?: entity.title,
        materialType = entity.materialType,
        sourceType = entity.sourceType,
        sourceLabel = learningMaterialSourceLabel(entity.sourceType, entity.sourceName, isUserGenerated = true),
        description = entity.description,
        contentText = entity.contentText,
        contentUrl = entity.contentUrl,
        sourceName = entity.sourceName,
        contentLocale = entity.contentLocale,
        isUserGenerated = true,
        difficultyLevel = entity.difficultyLevel,
        estimatedMinutes = entity.estimatedMinutes,
        isOfficial = false,
        displayOrder = entity.displayOrder,
        relationshipType = entity.relationshipType,
        labelOverride = entity.labelOverride,
        relevanceScore = entity.relevanceScore?.toDouble(),
    )

    fun toReferenceAnswerDto(entity: UserQuestionReferenceAnswerEntity): QuestionReferenceAnswerDto = QuestionReferenceAnswerDto(
        id = entity.id,
        title = entity.title,
        answerText = entity.answerText,
        answerFormat = entity.answerFormat,
        sourceType = entity.sourceType,
        sourceLabel = referenceAnswerSourceLabel(entity.sourceType, isUserGenerated = true),
        contentLocale = entity.contentLocale,
        isUserGenerated = true,
        targetRoleId = null,
        companyId = null,
        isOfficial = false,
        displayOrder = entity.displayOrder,
    )

    private fun referenceAnswerSourceLabel(sourceType: String, isUserGenerated: Boolean): String = when {
        isUserGenerated -> "Your note"
        sourceType == "ai_generated" -> "AI generated"
        sourceType == "real_interview_import" -> "Real interview"
        else -> "Editorial"
    }

    private fun learningMaterialSourceLabel(sourceType: String, sourceName: String?, isUserGenerated: Boolean): String = when {
        isUserGenerated -> "Your material"
        sourceType == "ai_generated" -> "AI generated"
        !sourceName.isNullOrBlank() -> sourceName
        else -> "Editorial"
    }
}
