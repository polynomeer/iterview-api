package com.example.interviewplatform.question.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.question.dto.CreateQuestionLearningMaterialRequest
import com.example.interviewplatform.question.dto.CreateQuestionReferenceAnswerRequest
import com.example.interviewplatform.question.dto.LearningMaterialDto
import com.example.interviewplatform.question.dto.QuestionCompanyDto
import com.example.interviewplatform.question.dto.QuestionDetailResponse
import com.example.interviewplatform.question.dto.QuestionListItemDto
import com.example.interviewplatform.question.dto.QuestionReferenceAnswerDto
import com.example.interviewplatform.question.dto.QuestionRoleDto
import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.dto.QuestionTagDto
import com.example.interviewplatform.question.dto.QuestionTreeNodeDto
import com.example.interviewplatform.question.dto.QuestionTreeResponseDto
import com.example.interviewplatform.question.dto.PracticalInterviewQuestionContextDto
import com.example.interviewplatform.question.dto.RecommendedFollowUpDto
import com.example.interviewplatform.question.dto.ResumeBasedQuestionDto
import com.example.interviewplatform.question.entity.LearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionLearningMaterialEntity
import com.example.interviewplatform.question.entity.QuestionLearningMaterialId
import com.example.interviewplatform.question.entity.QuestionReferenceAnswerEntity
import com.example.interviewplatform.question.entity.UserQuestionLearningMaterialEntity
import com.example.interviewplatform.question.entity.UserQuestionReferenceAnswerEntity
import com.example.interviewplatform.question.mapper.QuestionMapper
import com.example.interviewplatform.question.repository.QuestionRelationshipRepository
import com.example.interviewplatform.question.repository.QuestionReferenceAnswerRepository
import com.example.interviewplatform.question.repository.CategoryRepository
import com.example.interviewplatform.question.repository.LearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionCompanyRepository
import com.example.interviewplatform.question.repository.QuestionLearningMaterialRepository
import com.example.interviewplatform.question.repository.QuestionRepository
import com.example.interviewplatform.question.repository.QuestionRoleRepository
import com.example.interviewplatform.question.repository.QuestionSearchRepository
import com.example.interviewplatform.question.repository.QuestionSkillMappingRepository
import com.example.interviewplatform.question.repository.QuestionTagRepository
import com.example.interviewplatform.question.repository.TagRepository
import com.example.interviewplatform.question.repository.UserQuestionProgressRepository
import com.example.interviewplatform.question.repository.UserQuestionLearningMaterialRepository
import com.example.interviewplatform.question.repository.UserQuestionReferenceAnswerRepository
import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeRiskItemRepository
import com.example.interviewplatform.resume.repository.ResumeSkillSnapshotRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordAnswerRepository
import com.example.interviewplatform.interview.repository.InterviewRecordQuestionRepository
import com.example.interviewplatform.interview.repository.InterviewRecordRepository
import com.example.interviewplatform.skill.repository.SkillRepository
import com.example.interviewplatform.user.repository.CompanyRepository
import com.example.interviewplatform.user.repository.JobRoleRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant

@Service
class QuestionService(
    private val questionRepository: QuestionRepository,
    private val questionSearchRepository: QuestionSearchRepository,
    private val questionRelationshipRepository: QuestionRelationshipRepository,
    private val questionReferenceAnswerRepository: QuestionReferenceAnswerRepository,
    private val questionSkillMappingRepository: QuestionSkillMappingRepository,
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
    private val userQuestionReferenceAnswerRepository: UserQuestionReferenceAnswerRepository,
    private val userQuestionLearningMaterialRepository: UserQuestionLearningMaterialRepository,
    private val interviewRecordQuestionRepository: InterviewRecordQuestionRepository,
    private val interviewRecordAnswerRepository: InterviewRecordAnswerRepository,
    private val interviewRecordRepository: InterviewRecordRepository,
    private val resumeRepository: ResumeRepository,
    private val resumeVersionRepository: ResumeVersionRepository,
    private val resumeSkillSnapshotRepository: ResumeSkillSnapshotRepository,
    private val resumeRiskItemRepository: ResumeRiskItemRepository,
    private val skillRepository: SkillRepository,
    private val appLocaleService: AppLocaleService,
    private val questionReferenceContentGenerationService: QuestionReferenceContentGenerationService,
    private val objectMapper: ObjectMapper,
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

    @Transactional
    fun getQuestionDetail(questionId: Long, userId: Long?): QuestionDetailResponse {
        val question = requireReadableQuestion(questionId, userId)
        ensureGeneratedReferenceContent(question)
        val context = loadContext(questionIds = listOf(questionId), categoryIds = listOf(question.categoryId))
        val progress = userId?.let { userQuestionProgressRepository.findByUserIdAndQuestionId(it, questionId) }
        val practicalInterviewContext = loadPracticalInterviewContext(question, userId)
        val referenceAnswers = mergeReferenceAnswers(
            curated = mergeQuestionReferenceAnswers(
                globalAnswers = context.referenceAnswersByQuestionId[question.id].orEmpty(),
                userAnswers = userId?.let { loadUserReferenceAnswers(questionId, it, context.referenceAnswersByQuestionId[question.id].orEmpty().size) }.orEmpty(),
            ),
            practicalInterviewContext = practicalInterviewContext,
        )
        val learningMaterials = mergeLearningMaterials(
            globalMaterials = context.learningMaterialsByQuestionId[question.id].orEmpty(),
            userMaterials = userId?.let { loadUserLearningMaterials(questionId, it, context.learningMaterialsByQuestionId[question.id].orEmpty()) }.orEmpty(),
        )

        return QuestionMapper.toDetailResponse(
            question = question,
            categoryName = context.categoryNameById[question.categoryId],
            tags = context.tagsByQuestionId[question.id].orEmpty(),
            companies = context.companiesByQuestionId[question.id].orEmpty(),
            roles = context.rolesByQuestionId[question.id].orEmpty(),
            learningMaterials = learningMaterials,
            referenceAnswers = referenceAnswers,
            practicalInterviewContext = practicalInterviewContext,
            progress = progress,
        )
    }

    @Transactional
    fun getQuestionReferenceAnswers(questionId: Long, userId: Long?): List<QuestionReferenceAnswerDto> {
        val question = requireReadableQuestion(questionId, userId)
        ensureGeneratedReferenceContent(question)
        val locale = appLocaleService.resolveLanguage()
        val globalAnswers = questionReferenceAnswerRepository.findByQuestionIdOrderByDisplayOrderAscIdAsc(questionId)
            .filter { it.contentLocale == null || it.contentLocale == locale }
            .map(QuestionMapper::toReferenceAnswerDto)
        val userAnswers = userId?.let { loadUserReferenceAnswers(questionId, it, globalAnswers.size) }.orEmpty()
        return mergeReferenceAnswers(
            curated = mergeQuestionReferenceAnswers(globalAnswers, userAnswers),
            practicalInterviewContext = loadPracticalInterviewContext(question, userId),
        )
    }

    @Transactional
    fun getQuestionLearningMaterials(questionId: Long, userId: Long?): List<LearningMaterialDto> {
        val question = requireReadableQuestion(questionId, userId)
        ensureGeneratedReferenceContent(question)
        val globalMaterials = mapLearningMaterials(listOf(questionId))[questionId].orEmpty()
        val userMaterials = userId?.let { loadUserLearningMaterials(questionId, it, globalMaterials) }.orEmpty()
        return mergeLearningMaterials(globalMaterials, userMaterials)
    }

    @Transactional
    fun createQuestionReferenceAnswer(
        questionId: Long,
        userId: Long,
        request: CreateQuestionReferenceAnswerRequest,
    ): QuestionReferenceAnswerDto {
        requireReadableQuestion(questionId, userId)
        val now = Instant.now()
        val entity = userQuestionReferenceAnswerRepository.save(
            UserQuestionReferenceAnswerEntity(
                questionId = questionId,
                userId = userId,
                title = request.title.trim(),
                answerText = request.answerText.trim(),
                answerFormat = request.answerFormat.ifBlank { "full_answer" },
                sourceType = USER_REFERENCE_SOURCE_TYPE,
                contentLocale = appLocaleService.resolveLanguage(),
                displayOrder = userQuestionReferenceAnswerRepository.countByQuestionIdAndUserId(questionId, userId).toInt() + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return QuestionMapper.toReferenceAnswerDto(entity)
    }

    @Transactional
    fun createQuestionLearningMaterial(
        questionId: Long,
        userId: Long,
        request: CreateQuestionLearningMaterialRequest,
    ): LearningMaterialDto {
        requireReadableQuestion(questionId, userId)
        if (request.contentText.isNullOrBlank() && request.contentUrl.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "contentText or contentUrl is required")
        }
        val now = Instant.now()
        val entity = userQuestionLearningMaterialRepository.save(
            UserQuestionLearningMaterialEntity(
                questionId = questionId,
                userId = userId,
                title = request.title.trim(),
                materialType = request.materialType.trim(),
                description = request.description?.trim()?.ifBlank { null },
                contentText = request.contentText?.trim()?.ifBlank { null },
                contentUrl = request.contentUrl?.trim()?.ifBlank { null },
                sourceName = request.sourceName?.trim()?.ifBlank { null },
                difficultyLevel = request.difficultyLevel?.trim()?.ifBlank { null },
                estimatedMinutes = request.estimatedMinutes,
                relationshipType = request.relationshipType?.trim()?.ifBlank { null },
                labelOverride = request.labelOverride?.trim()?.ifBlank { null },
                relevanceScore = request.relevanceScore,
                sourceType = USER_REFERENCE_SOURCE_TYPE,
                contentLocale = appLocaleService.resolveLanguage(),
                displayOrder = userQuestionLearningMaterialRepository.countByQuestionIdAndUserId(questionId, userId).toInt() + 1,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return QuestionMapper.toLearningMaterialDto(entity)
    }

    @Transactional(readOnly = true)
    fun getQuestionTree(questionId: Long, userId: Long?): QuestionTreeResponseDto {
        val rootQuestion = requireReadableQuestion(questionId, userId)
        val graph = loadQuestionTreeGraph(questionId)
        val questionsById = questionRepository.findAllById(graph.questionIds).filter { it.isActive }.associateBy { it.id }
        val progressByQuestionId = if (userId == null || graph.questionIds.isEmpty()) {
            emptyMap()
        } else {
            userQuestionProgressRepository.findByUserIdAndQuestionIdIn(userId, graph.questionIds).associateBy { it.questionId }
        }

        return QuestionTreeResponseDto(
            root = buildTreeNode(
                questionId = rootQuestion.id,
                depth = 0,
                childEdgesByParentId = graph.childEdgesByParentId,
                questionsById = questionsById,
                progressByQuestionId = progressByQuestionId,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getRecommendedFollowUps(questionId: Long, userId: Long?): List<RecommendedFollowUpDto> {
        val question = requireReadableQuestion(questionId, userId)
        val edges = questionRelationshipRepository.findByParentQuestionIdOrderByDisplayOrderAscIdAsc(question.id)
        if (edges.isEmpty()) {
            return emptyList()
        }

        val childQuestionsById = questionRepository.findAllById(edges.map { it.childQuestionId }).filter { it.isActive }.associateBy { it.id }
        val progressByQuestionId = if (userId == null) {
            emptyMap()
        } else {
            userQuestionProgressRepository.findByUserIdAndQuestionIdIn(userId, childQuestionsById.keys.toList()).associateBy { it.questionId }
        }

        return edges.mapNotNull { edge ->
            val child = childQuestionsById[edge.childQuestionId] ?: return@mapNotNull null
            RecommendedFollowUpDto(
                questionId = child.id,
                title = child.title,
                difficulty = child.difficultyLevel,
                relationshipType = edge.relationshipType,
                depth = edge.depth,
                nodeStatus = progressStatus(progressByQuestionId[child.id]),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getResumeBasedQuestions(userId: Long, limit: Int): List<ResumeBasedQuestionDto> {
        val resume = resumeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId).firstOrNull()
            ?: return emptyList()
        val versions = resumeVersionRepository.findByResumeIdOrderByVersionNoAsc(resume.id)
        val version = versions.findLast { it.isActive } ?: versions.lastOrNull() ?: return emptyList()

        val resumeSkills = resumeSkillSnapshotRepository.findByResumeVersionIdOrderByIdAsc(version.id)
        if (resumeSkills.isEmpty()) {
            return emptyList()
        }
        val resumeSkillIds = resumeSkills.mapNotNull { it.skillId }.distinct()
        if (resumeSkillIds.isEmpty()) {
            return emptyList()
        }

        val matchedSkillNamesById = skillRepository.findAllById(resumeSkillIds).associate { it.id to it.name }
        val mappings = questionSkillMappingRepository.findBySkillIdIn(resumeSkillIds)
        if (mappings.isEmpty()) {
            return emptyList()
        }

        val riskQuestionIds = resumeRiskItemRepository.findByResumeVersionIdOrderBySeverityDescIdAsc(version.id)
            .mapNotNull { it.linkedQuestionId }
            .toSet()

        val ranked = mappings.groupBy { it.questionId }.map { (mappedQuestionId, questionMappings) ->
            val matchedSkills = questionMappings.mapNotNull { matchedSkillNamesById[it.skillId] }.distinct()
            val totalWeight = questionMappings.fold(BigDecimal.ZERO) { acc, mapping -> acc + mapping.weight }
            RankedResumeQuestion(
                questionId = mappedQuestionId,
                score = totalWeight.toDouble() + if (mappedQuestionId in riskQuestionIds) 10.0 else 0.0,
                matchedSkills = matchedSkills,
            )
        }.sortedWith(compareByDescending<RankedResumeQuestion> { it.score }.thenBy { it.questionId })

        val limited = ranked.take(limit.coerceIn(1, 50))
        val questionsById = questionRepository.findAllById(limited.map { it.questionId })
            .filter { it.isActive }
            .associateBy { it.id }

        return limited.mapNotNull { rankedQuestion ->
            val question = questionsById[rankedQuestion.questionId] ?: return@mapNotNull null
            ResumeBasedQuestionDto(
                questionId = question.id,
                title = question.title,
                difficulty = question.difficultyLevel,
                matchScore = rankedQuestion.score,
                matchedSkills = rankedQuestion.matchedSkills,
            )
        }
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
            referenceAnswersByQuestionId = mapReferenceAnswers(questionIds),
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
        val locale = appLocaleService.resolveLanguage()
        val materialEdges = questionLearningMaterialRepository.findByIdQuestionIdIn(questionIds)
        val materialsById = learningMaterialRepository
            .findAllById(materialEdges.map { it.id.learningMaterialId }.distinct())
            .associateBy { it.id }

        return materialEdges.groupBy { it.id.questionId }.mapValues { (_, edges) ->
            edges.sortedWith(
                compareBy<com.example.interviewplatform.question.entity.QuestionLearningMaterialEntity> { it.displayOrder ?: Int.MAX_VALUE }
                    .thenByDescending { it.relevanceScore }
                    .thenBy { it.id.learningMaterialId },
            ).mapNotNull { edge ->
                materialsById[edge.id.learningMaterialId]
                    ?.takeIf { it.contentLocale == null || it.contentLocale == locale }
                    ?.let { QuestionMapper.toLearningMaterialDto(it, edge) }
            }
        }
    }

    private fun mapReferenceAnswers(questionIds: List<Long>): Map<Long, List<QuestionReferenceAnswerDto>> {
        val locale = appLocaleService.resolveLanguage()
        return questionReferenceAnswerRepository.findByQuestionIdInOrderByQuestionIdAscDisplayOrderAscIdAsc(questionIds)
            .filter { it.contentLocale == null || it.contentLocale == locale }
            .groupBy { it.questionId }
            .mapValues { (_, answers) -> answers.map(QuestionMapper::toReferenceAnswerDto) }
    }

    private fun ensureGeneratedReferenceContent(question: com.example.interviewplatform.question.entity.QuestionEntity) {
        val locale = appLocaleService.resolveLanguage()
        val hasReferenceAnswers = questionReferenceAnswerRepository.findByQuestionIdOrderByDisplayOrderAscIdAsc(question.id)
            .any { it.contentLocale == null || it.contentLocale == locale }
        val materialEdges = questionLearningMaterialRepository.findByIdQuestionIdIn(listOf(question.id))
        val materialsById = learningMaterialRepository.findAllById(materialEdges.map { it.id.learningMaterialId }.distinct())
            .associateBy { it.id }
        val hasLearningMaterials = materialEdges.any { edge ->
            materialsById[edge.id.learningMaterialId]?.let { it.contentLocale == null || it.contentLocale == locale } == true
        }
        if (hasReferenceAnswers && hasLearningMaterials) {
            return
        }

        val tags = mapTags(listOf(question.id))[question.id].orEmpty().map { it.name }
        val categoryName = categoryRepository.findById(question.categoryId).orElse(null)?.name
        val generated = questionReferenceContentGenerationService.generate(
            questionTitle = question.title,
            questionBody = question.body,
            questionType = question.questionType,
            difficultyLevel = question.difficultyLevel,
            categoryName = categoryName,
            tags = tags,
        )
        val now = Instant.now()

        if (!hasReferenceAnswers) {
            generated.referenceAnswers.forEach { answer ->
                questionReferenceAnswerRepository.save(
                    QuestionReferenceAnswerEntity(
                        questionId = question.id,
                        title = answer.title,
                        answerText = answer.answerText,
                        answerFormat = answer.answerFormat,
                        sourceType = AI_REFERENCE_SOURCE_TYPE,
                        contentLocale = generated.contentLocale,
                        isOfficial = false,
                        displayOrder = answer.displayOrder,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }

        if (!hasLearningMaterials) {
            generated.learningMaterials.forEach { material ->
                val savedMaterial = learningMaterialRepository.save(
                    LearningMaterialEntity(
                        title = material.title,
                        materialType = material.materialType,
                        contentText = material.contentText,
                        contentUrl = material.contentUrl,
                        sourceName = AI_SOURCE_NAME,
                        contentLocale = generated.contentLocale,
                        description = material.description,
                        difficultyLevel = material.difficultyLevel,
                        estimatedMinutes = material.estimatedMinutes,
                        isOfficial = false,
                        displayOrderHint = material.displayOrder,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                questionLearningMaterialRepository.save(
                    QuestionLearningMaterialEntity(
                        id = QuestionLearningMaterialId(question.id, savedMaterial.id),
                        relevanceScore = BigDecimal.valueOf(material.relevanceScore ?: DEFAULT_GENERATED_MATERIAL_SCORE),
                        displayOrder = material.displayOrder,
                        relationshipType = material.relationshipType,
                        labelOverride = null,
                        createdAt = now,
                    ),
                )
            }
        }
    }

    private fun loadUserReferenceAnswers(
        questionId: Long,
        userId: Long,
        globalCount: Int,
    ): List<QuestionReferenceAnswerDto> = userQuestionReferenceAnswerRepository
        .findByQuestionIdAndUserIdOrderByDisplayOrderAscIdAsc(questionId, userId)
        .map(QuestionMapper::toReferenceAnswerDto)
        .mapIndexed { index, dto -> dto.copy(displayOrder = globalCount + index + 1) }

    private fun loadUserLearningMaterials(
        questionId: Long,
        userId: Long,
        globalMaterials: List<LearningMaterialDto>,
    ): List<LearningMaterialDto> {
        val globalMax = globalMaterials.mapNotNull { it.displayOrder }.maxOrNull() ?: globalMaterials.size
        return userQuestionLearningMaterialRepository.findByQuestionIdAndUserIdOrderByDisplayOrderAscIdAsc(questionId, userId)
            .map(QuestionMapper::toLearningMaterialDto)
            .mapIndexed { index, dto -> dto.copy(displayOrder = globalMax + index + 1) }
    }

    private fun mergeQuestionReferenceAnswers(
        globalAnswers: List<QuestionReferenceAnswerDto>,
        userAnswers: List<QuestionReferenceAnswerDto>,
    ): List<QuestionReferenceAnswerDto> = globalAnswers + userAnswers

    private fun mergeLearningMaterials(
        globalMaterials: List<LearningMaterialDto>,
        userMaterials: List<LearningMaterialDto>,
    ): List<LearningMaterialDto> = globalMaterials + userMaterials

    private fun requireReadableQuestion(questionId: Long, userId: Long?): com.example.interviewplatform.question.entity.QuestionEntity {
        val question = questionRepository.findByIdAndIsActiveTrue(questionId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: $questionId")
        if (question.visibility.lowercase() != QUESTION_VISIBILITY_PUBLIC && question.authorUserId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: $questionId")
        }
        return question
    }

    private fun loadPracticalInterviewContext(
        question: com.example.interviewplatform.question.entity.QuestionEntity,
        userId: Long?,
    ): PracticalInterviewQuestionContextDto? {
        if (question.sourceType != QUESTION_SOURCE_TYPE_REAL_INTERVIEW_IMPORT) {
            return null
        }
        val linkedQuestion = interviewRecordQuestionRepository.findByLinkedQuestionId(question.id) ?: return null
        val record = interviewRecordRepository.findById(linkedQuestion.interviewRecordId).orElse(null) ?: return null
        if (record.userId != userId) {
            return null
        }
        val answer = interviewRecordAnswerRepository.findByInterviewRecordQuestionIdIn(listOf(linkedQuestion.id)).firstOrNull()
        return PracticalInterviewQuestionContextDto(
            sourceInterviewRecordId = record.id,
            sourceInterviewQuestionId = linkedQuestion.id,
            companyName = record.companyName,
            roleName = record.roleName,
            interviewDate = record.interviewDate,
            interviewType = record.interviewType,
            questionType = linkedQuestion.questionType,
            topicTags = decodeStringList(linkedQuestion.topicTagsJson),
            intentTags = decodeStringList(linkedQuestion.intentTagsJson),
            interviewerProfileId = record.interviewerProfileId,
            importedAnswerSummary = answer?.summary,
            importedAnswerText = answer?.text,
            isFollowUp = linkedQuestion.parentQuestionId != null,
        )
    }

    private fun mergeReferenceAnswers(
        curated: List<QuestionReferenceAnswerDto>,
        practicalInterviewContext: PracticalInterviewQuestionContextDto?,
    ): List<QuestionReferenceAnswerDto> {
        val imported = practicalInterviewContext?.importedAnswerSummary?.takeIf { it.isNotBlank() }?.let {
            listOf(
                QuestionReferenceAnswerDto(
                    id = -practicalInterviewContext.sourceInterviewQuestionId,
                    title = "Imported real interview answer summary",
                    answerText = practicalInterviewContext.importedAnswerText?.takeIf { text -> text.isNotBlank() } ?: it,
                    answerFormat = if (practicalInterviewContext.importedAnswerText.isNullOrBlank()) "summary" else "transcript_excerpt",
                    sourceType = QUESTION_SOURCE_TYPE_REAL_INTERVIEW_IMPORT,
                    sourceLabel = "Real interview",
                    contentLocale = null,
                    isUserGenerated = false,
                    targetRoleId = null,
                    companyId = null,
                    isOfficial = false,
                    displayOrder = curated.size + 1,
                ),
            )
        }.orEmpty()
        return curated + imported
    }

    private fun decodeStringList(raw: String): List<String> =
        runCatching { objectMapper.readValue(raw, object : TypeReference<List<String>>() {}) }
            .getOrDefault(emptyList())

    private companion object {
        const val QUESTION_SOURCE_TYPE_REAL_INTERVIEW_IMPORT = "real_interview_import"
        const val QUESTION_VISIBILITY_PUBLIC = "public"
        const val AI_REFERENCE_SOURCE_TYPE = "ai_generated"
        const val USER_REFERENCE_SOURCE_TYPE = "user_added"
        const val AI_SOURCE_NAME = "OpenAI"
        const val DEFAULT_GENERATED_MATERIAL_SCORE = 0.85
    }

    private data class QuestionQueryContext(
        val tagsByQuestionId: Map<Long, List<QuestionTagDto>>,
        val companiesByQuestionId: Map<Long, List<QuestionCompanyDto>>,
        val rolesByQuestionId: Map<Long, List<QuestionRoleDto>>,
        val learningMaterialsByQuestionId: Map<Long, List<LearningMaterialDto>>,
        val referenceAnswersByQuestionId: Map<Long, List<QuestionReferenceAnswerDto>>,
        val categoryNameById: Map<Long, String>,
    )

    private fun loadQuestionTreeGraph(rootQuestionId: Long): TreeGraph {
        val discoveredQuestionIds = linkedSetOf(rootQuestionId)
        val childEdgesByParentId = linkedMapOf<Long, List<com.example.interviewplatform.question.entity.QuestionRelationshipEntity>>()
        var frontier = listOf(rootQuestionId)

        while (frontier.isNotEmpty()) {
            val edges = questionRelationshipRepository.findByParentQuestionIdInOrderByParentQuestionIdAscDisplayOrderAscIdAsc(frontier)
            if (edges.isEmpty()) {
                break
            }

            edges.groupBy { it.parentQuestionId }.forEach { (parentId, groupedEdges) ->
                childEdgesByParentId[parentId] = groupedEdges
            }
            frontier = edges.map { it.childQuestionId }.filter { discoveredQuestionIds.add(it) }
        }

        return TreeGraph(
            questionIds = discoveredQuestionIds.toList(),
            childEdgesByParentId = childEdgesByParentId,
        )
    }

    private fun buildTreeNode(
        questionId: Long,
        depth: Int,
        childEdgesByParentId: Map<Long, List<com.example.interviewplatform.question.entity.QuestionRelationshipEntity>>,
        questionsById: Map<Long, com.example.interviewplatform.question.entity.QuestionEntity>,
        progressByQuestionId: Map<Long, com.example.interviewplatform.question.entity.UserQuestionProgressEntity>,
    ): QuestionTreeNodeDto {
        val question = questionsById[questionId]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found: $questionId")
        val children = childEdgesByParentId[questionId].orEmpty().mapNotNull { edge ->
            questionsById[edge.childQuestionId]?.let {
                buildTreeNode(
                    questionId = it.id,
                    depth = edge.depth,
                    childEdgesByParentId = childEdgesByParentId,
                    questionsById = questionsById,
                    progressByQuestionId = progressByQuestionId,
                )
            }
        }
        return QuestionTreeNodeDto(
            questionId = question.id,
            title = question.title,
            difficulty = question.difficultyLevel,
            nodeStatus = progressStatus(progressByQuestionId[question.id]),
            depth = depth,
            children = children,
        )
    }

    private fun progressStatus(progress: com.example.interviewplatform.question.entity.UserQuestionProgressEntity?): String = when {
        progress == null || progress.totalAttemptCount == 0 -> "unanswered"
        progress.currentStatus == "archived" || (progress.bestScore?.toDouble() ?: 0.0) >= 85.0 -> "strong"
        progress.currentStatus == "retry_pending" || (progress.latestScore?.toDouble() ?: 0.0) < 60.0 -> "weak"
        else -> "answered"
    }

    private data class TreeGraph(
        val questionIds: List<Long>,
        val childEdgesByParentId: Map<Long, List<com.example.interviewplatform.question.entity.QuestionRelationshipEntity>>,
    )

    private data class RankedResumeQuestion(
        val questionId: Long,
        val score: Double,
        val matchedSkills: List<String>,
    )
}
