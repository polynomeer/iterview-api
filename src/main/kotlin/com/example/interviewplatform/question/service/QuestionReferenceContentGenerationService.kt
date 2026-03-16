package com.example.interviewplatform.question.service

import com.example.interviewplatform.common.service.AppLocaleService
import org.springframework.stereotype.Service

@Service
class QuestionReferenceContentGenerationService(
    private val client: QuestionReferenceContentGenerationClient,
    private val appLocaleService: AppLocaleService,
) {
    fun generate(
        questionTitle: String,
        questionBody: String?,
        questionType: String,
        difficultyLevel: String,
        categoryName: String?,
        tags: List<String>,
    ): GeneratedQuestionReferenceContent {
        val input = QuestionReferenceContentGenerationInput(
            outputLanguage = appLocaleService.resolveLanguage(),
            questionTitle = questionTitle,
            questionBody = questionBody,
            questionType = questionType,
            difficultyLevel = difficultyLevel,
            categoryName = categoryName,
            tags = tags,
        )
        return runCatching {
            if (client.isEnabled()) client.generate(input) else fallback(input)
        }.getOrElse { fallback(input) }
    }

    private fun fallback(input: QuestionReferenceContentGenerationInput): GeneratedQuestionReferenceContent {
        val locale = input.outputLanguage
        val answerOutlineTitle = if (locale == "ko") "핵심 답변 구조" else "Answer structure"
        val answerFullTitle = if (locale == "ko") "심화 모범답변" else "Expanded model answer"
        val refresherTitle = if (locale == "ko") "핵심 개념 정리" else "Core concept refresher"
        val drillTitle = if (locale == "ko") "답변 연습 포인트" else "Practice drill"
        val answerOutline = if (locale == "ko") {
            buildString {
                appendLine("1. 질문 의도를 먼저 한 문장으로 정리합니다.")
                appendLine("2. 핵심 개념 또는 문제 상황을 정의합니다.")
                appendLine("3. 실제 선택한 접근과 그 이유를 설명합니다.")
                appendLine("4. 장단점, 트레이드오프, 예외 상황을 덧붙입니다.")
                appendLine("5. 가능하면 결과나 검증 방법으로 마무리합니다.")
                input.questionBody?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    append("질문 맥락 메모: ").append(it.take(220))
                }
            }
        } else {
            buildString {
                appendLine("1. Restate the intent of the question in one sentence.")
                appendLine("2. Define the core concept or problem context.")
                appendLine("3. Explain the approach you would take and why.")
                appendLine("4. Call out trade-offs, risks, and edge cases.")
                appendLine("5. Close with validation, outcome, or operational impact.")
                input.questionBody?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    append("Context note: ").append(it.take(220))
                }
            }
        }
        val answerFull = if (locale == "ko") {
            "${input.questionTitle}에 답할 때는 정의 -> 선택 기준 -> 실제 적용 방식 -> 트레이드오프 -> 검증 결과 순으로 답하는 것이 좋습니다. " +
                "가능하면 직접 겪은 사례나 시스템 제약을 함께 설명하고, 왜 다른 대안보다 현재 방식을 선택했는지까지 말해야 설득력이 높아집니다."
        } else {
            "For ${input.questionTitle}, answer in the order of definition, decision criteria, implementation approach, trade-offs, and validation. " +
                "Use a concrete example when possible and explain why you chose this approach over alternatives."
        }
        val refresherContent = if (locale == "ko") {
            "질문 유형(${input.questionType})과 난이도(${input.difficultyLevel})를 고려해, 핵심 용어 정의와 대표적인 설계/운영 트레이드오프를 먼저 정리하세요."
        } else {
            "Given the ${input.questionType} format and ${input.difficultyLevel} difficulty, review the core definitions first and then the main design or operational trade-offs."
        }
        val drillContent = if (locale == "ko") {
            "30초 요약, 2분 구조화 답변, 꼬리질문 대비 포인트를 각각 준비하세요. 숫자나 검증 기준이 있다면 반드시 포함하세요."
        } else {
            "Prepare a 30-second summary, a 2-minute structured answer, and likely follow-up probes. Include metrics or validation criteria whenever possible."
        }
        return GeneratedQuestionReferenceContent(
            referenceAnswers = listOf(
                GeneratedQuestionReferenceAnswer(
                    title = answerOutlineTitle,
                    answerText = answerOutline,
                    answerFormat = "outline",
                    displayOrder = 1,
                ),
                GeneratedQuestionReferenceAnswer(
                    title = answerFullTitle,
                    answerText = answerFull,
                    answerFormat = "full_answer",
                    displayOrder = 2,
                ),
            ),
            learningMaterials = listOf(
                GeneratedQuestionLearningMaterial(
                    title = refresherTitle,
                    materialType = "note",
                    description = if (locale == "ko") "답변 전에 확인할 핵심 개념 정리" else "Key concepts to review before answering",
                    contentText = refresherContent,
                    contentUrl = null,
                    difficultyLevel = input.difficultyLevel.lowercase(),
                    estimatedMinutes = 8,
                    relationshipType = "prerequisite",
                    displayOrder = 1,
                    relevanceScore = 0.95,
                ),
                GeneratedQuestionLearningMaterial(
                    title = drillTitle,
                    materialType = "practice_note",
                    description = if (locale == "ko") "실전 답변을 더 설득력 있게 만드는 연습 포인트" else "Practice points that make the answer more convincing",
                    contentText = drillContent,
                    contentUrl = null,
                    difficultyLevel = input.difficultyLevel.lowercase(),
                    estimatedMinutes = 6,
                    relationshipType = "practice",
                    displayOrder = 2,
                    relevanceScore = 0.88,
                ),
            ),
            llmModel = null,
            contentLocale = locale,
        )
    }
}
