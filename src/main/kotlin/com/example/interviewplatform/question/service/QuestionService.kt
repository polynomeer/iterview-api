package com.example.interviewplatform.question.service

import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.dto.QuestionCompanyDto
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionRoleDto
import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.dto.QuestionTagDto
import com.example.interviewplatform.question.mapper.QuestionMapper
import com.example.interviewplatform.question.repository.CategoryRepository
import com.example.interviewplatform.question.repository.LearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionCompanyRepository
import com.example.interviewplatform.question.repository.QuestionLearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.QuestionRoleRepository
import com.example.interviewplatform.question.repository.QuestionSearchRepository
import com.example.interviewplatform.question.repository.QuestionTagRepository
import com.example.interviewplatform.question.repository.TagRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.user.repository.CompanyRepository
import com.example.interviewplatform.user.repository.JobRoleRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val questionSearchRepository: QuestionSearchRepository,
    private val questionTagRepository: QuestionTagRepository,
    private val questionCompanyRepository: QuestionCompanyRepository,
    private val questionRoleRepository: QuestionRoleRepository,
    private val questionLearningMaterialRepository: QuestionLearningMaterialRepository,
    private val tagRepository: TagRepository,
    private val companyRepository: CompanyRepository,
    private val jobRoleRepository: JobRoleRepository,
    private val learningMaterialRepository: LearningMaterialRepository,
    private val categoryRepository: CategoryRepository,
    private val userQuestionProgressRepository: UserQuestionProgressRepository,
) {
    @Transactional(readOnly = true)
    fun listQuestions(filter: QuestionSearchFilter): List<QuestionListItemDto> {
        val questions = questionSearchRepository.search(filter)
        if (questions.isEmpty()) {
            return emptyList()
        }

        val context = loadContext(
            questionIds = questions.map { it.id },
            categoryIds = questions.map { it.categoryId }.distinct(),
        )
        return questions.map { question ->
            QuestionMapper.toListItemDto(
                question = question,
                categoryName = context.categoryNameById[question.categoryId],
                tags = context.tagsByQuestionId[question.id].orEmpty(),
                companies = context.companiesByQuestionId[question.id].orEmpty(),
                learningMaterials = context.learningMaterialsByQuestionId[question.id].orEmpty(),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getQuestionDetail(questionId: Long, userId: Long): QuestionDetailResponse {
        val question = questionRepository.findByIdAndIsActiveTrue(questionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: $questionId")
        val context = loadContext(questionIds = listOf(questionId), categoryIds = listOf(question.categoryId))
        val progress = userQuestionProgressRepository.findByUserIdAndQuestionId(userId, questionId)

        return QuestionMapper.toDetailResponse(
            question = question,
            categoryName = context.categoryNameById[question.categoryId],
            tags = context.tagsByQuestionId[question.id].orEmpty(),
            companies = context.companiesByQuestionId[question.id].orEmpty(),
            roles = context.rolesByQuestionId[question.id].orEmpty(),
            learningMaterials = context.learningMaterialsByQuestionId[question.id].orEmpty(),
            progress = progress,
        )
    }

    private fun loadContext(questionIds: List<Long>, categoryIds: List<Long>): QuestionQueryContext {
        val tagsByQuestionId = mapTags(questionIds)
        val companiesByQuestionId = mapCompanies(questionIds)
        val rolesByQuestionId = mapRoles(questionIds)
        val learningMaterialsByQuestionId = mapLearningMaterials(questionIds)

        val categoryNameById = categoryRepository.findAllById(categoryIds).associate { it.id to it.name }

        return QuestionQueryContext(
            tagsByQuestionId = tagsByQuestionId,
            companiesByQuestionId = companiesByQuestionId,
            rolesByQuestionId = rolesByQuestionId,
            learningMaterialsByQuestionId = learningMaterialsByQuestionId,
            categoryNameById = categoryNameById,
        )
    }

    private fun mapTags(questionIds: List<Long>): Map<Long, List<QuestionTagDto>> {
        val tagEdges = questionTagRepository.findByIdQuestionIdIn(questionIds)
        val tagsById = tagRepository.findAllById(tagEdges.map { it.id.tagId }.distinct()).associateBy { it.id }
        return tagEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.mapNotNull { edge ->
                tagsById[edge.id.tagId]?.let(QuestionMapper::toTagDto)
            }
        }
    }

    private fun mapCompanies(questionIds: List<Long>): Map<Long, List<QuestionCompanyDto>> {
        val companyEdges = questionCompanyRepository.findByIdQuestionIdIn(questionIds)
        val companyById = companyRepository.findAllById(companyEdges.map { it.id.companyId }.distinct()).associateBy { it.id }
        return companyEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.mapNotNull { edge ->
                companyById[edge.id.companyId]?.let { QuestionMapper.toCompanyDto(edge, it) }
            }
        }
    }

    private fun mapRoles(questionIds: List<Long>): Map<Long, List<QuestionRoleDto>> {
        val roleEdges = questionRoleRepository.findByIdQuestionIdIn(questionIds)
        val roleById = jobRoleRepository.findAllById(roleEdges.map { it.id.jobRoleId }.distinct()).associateBy { it.id }
        return roleEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.mapNotNull { edge ->
                roleById[edge.id.jobRoleId]?.let { QuestionMapper.toRoleDto(edge, it) }
            }
        }
    }

    private fun mapLearningMaterials(questionIds: List<Long>): Map<Long, List<LearningMaterialDto>> {
        val materialEdges = questionLearningMaterialRepository.findByIdQuestionIdIn(questionIds)
        val materialsById = learningMaterialRepository
            .findAllById(materialEdges.map { it.id.learningMaterialId }.distinct())
            .associateBy { it.id }

        return materialEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.mapNotNull { edge ->
                materialsById[edge.id.learningMaterialId]?.let(QuestionMapper::toLearningMaterialDto)
            }
        }
    }

    private data class QuestionQueryContext(
        val tagsByQuestionId: Map<Long, List<QuestionTagDto>>,
        val companiesByQuestionId: Map<Long, List<QuestionCompanyDto>>,
        val rolesByQuestionId: Map<Long, List<QuestionRoleDto>>,
        val learningMaterialsByQuestionId: Map<Long, List<LearningMaterialDto>>,
        val categoryNameById: Map<Long, String>,
    )
}
