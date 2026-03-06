package com.example.interviewplatform.question.mapper

import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.dto.QuestionCompanyDto
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionMetadataDto
import com.example.interviewplatform.question.dto.QuestionRoleDto
import com.example.interviewplatform.question.dto.QuestionTagDto
import com.example.interviewplatform.question.dto.UserProgressSummaryDto
import com.example.interviewplatform.question.entity.LearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionCompanyEntity
import com.example.interviewplatform.question.entity.QuestionEntity
import com.example.interviewplatform.question.entity.QuestionRoleEntity
import com.example.interviewplatform.question.entity.TagEntity
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

    fun toLearningMaterialDto(entity: LearningMaterialEntity): LearningMaterialDto = LearningMaterialDto(
        id = entity.id,
        title = entity.title,
        materialType = entity.materialType,
        contentUrl = entity.contentUrl,
        sourceName = entity.sourceName,
    )
}
