package com.example.interviewplatform.answer.service

import com.example.interviewplatform.answer.dto.AnswerFeedbackItemDto
import com.example.interviewplatform.answer.dto.ScoreSummaryDto
import com.example.interviewplatform.answer.entity.AnswerAnalysisEntity
import com.example.interviewplatform.answer.entity.AnswerAttemptEntity
import com.example.interviewplatform.common.service.AppLocaleService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class AnswerAnalysisService(
    private val appLocaleService: AppLocaleService,
    private val objectMapper: ObjectMapper,
    private val answerDeepFeedbackGenerationClient: AnswerDeepFeedbackGenerationClient,
) {
    fun analyze(
        attempt: AnswerAttemptEntity,
        questionTitle: String,
        questionBody: String?,
        score: ScoreSummaryDto,
        feedback: List<AnswerFeedbackItemDto>,
        now: Instant,
    ): AnswerAnalysisEntity {
        val normalizedLength = attempt.contentText.trim().length.coerceAtMost(1200).toDouble() / 1200.0
        val hasNumericEvidence = attempt.contentText.any { it.isDigit() }
        val mentionsTradeoff = TRADEOFF_HINTS.any { attempt.contentText.contains(it, ignoreCase = true) }
        val mentionsExample = EXAMPLE_HINTS.any { attempt.contentText.contains(it, ignoreCase = true) } || hasNumericEvidence

        val depthScore = weighted(score.technicalAccuracyScore * 0.6 + normalizedLength * 40.0)
        val clarityScore = weighted(score.structureScore.toDouble())
        val accuracyScore = weighted(score.technicalAccuracyScore.toDouble())
        val exampleScore = weighted(if (mentionsExample) score.specificityScore + 10.0 else score.specificityScore.toDouble() - 5.0)
        val tradeoffScore = weighted(if (mentionsTradeoff) score.companyFitScore + 12.0 else score.companyFitScore.toDouble() - 8.0)
        val confidenceScore = weighted(
            when (attempt.answerMode) {
                "skip", "unanswered" -> 10.0
                else -> score.totalScore * 0.7 + normalizedLength * 20.0
            },
        )

        val strengthSummary = when {
            score.totalScore >= 85 -> "Strong answer with clear structure and defensible technical choices."
            score.totalScore >= 65 -> "Solid baseline answer that covers the prompt and shows partial depth."
            else -> feedback.firstOrNull()?.body ?: "The answer shows initial understanding but needs stronger structure and evidence."
        }

        val weaknessSummary = when {
            score.totalScore < 60 -> "Add more concrete examples, tradeoffs, and a clearer explanation of why your approach works."
            tradeoffScore.toInt() < 60 -> "The answer needs a stronger explanation of tradeoffs, constraints, and failure handling."
            else -> "Push deeper on examples and measurable outcomes to make the answer more interview-ready."
        }

        val recommendedNextStep = when {
            attempt.answerMode == "skip" || attempt.answerMode == "unanswered" ->
                "Write a full first-pass answer before optimizing for depth."
            score.totalScore < 60 ->
                "Re-answer using a concrete production example with metrics, constraints, and tradeoffs."
            else ->
                "Practice one likely follow-up question and make the tradeoff discussion more explicit."
        }
        val generated = generateDeepFeedback(
            questionTitle = questionTitle,
            questionBody = questionBody,
            attempt = attempt,
            score = score,
            feedback = feedback,
        )

        return AnswerAnalysisEntity(
            answerAttemptId = attempt.id,
            overallScore = weighted(score.totalScore.toDouble()),
            depthScore = depthScore,
            clarityScore = clarityScore,
            accuracyScore = accuracyScore,
            exampleScore = exampleScore,
            tradeoffScore = tradeoffScore,
            confidenceScore = confidenceScore,
            strengthSummary = strengthSummary,
            weaknessSummary = weaknessSummary,
            recommendedNextStep = recommendedNextStep,
            detailedFeedback = generated.detailedFeedback,
            modelAnswerText = generated.modelAnswerText,
            strengthPointsJson = encodeList(generated.strengthPoints),
            improvementPointsJson = encodeList(generated.improvementPoints),
            missedPointsJson = encodeList(generated.missedPoints),
            llmModel = generated.llmModel,
            contentLocale = generated.contentLocale,
            createdAt = now,
        )
    }

    private fun generateDeepFeedback(
        questionTitle: String,
        questionBody: String?,
        attempt: AnswerAttemptEntity,
        score: ScoreSummaryDto,
        feedback: List<AnswerFeedbackItemDto>,
    ): GeneratedAnswerDeepFeedback {
        val input = AnswerDeepFeedbackGenerationInput(
            outputLanguage = appLocaleService.resolveLanguage(),
            questionTitle = questionTitle,
            questionBody = questionBody,
            answerText = attempt.contentText,
            answerMode = attempt.answerMode,
            totalScore = score.totalScore,
            structureScore = score.structureScore,
            specificityScore = score.specificityScore,
            technicalAccuracyScore = score.technicalAccuracyScore,
            feedbackTitles = feedback.map { it.title },
            feedbackBodies = feedback.map { it.body },
        )
        return runCatching {
            if (answerDeepFeedbackGenerationClient.isEnabled()) answerDeepFeedbackGenerationClient.generate(input) else fallback(input)
        }.getOrElse { fallback(input) }
    }

    private fun fallback(input: AnswerDeepFeedbackGenerationInput): GeneratedAnswerDeepFeedback {
        val locale = input.outputLanguage
        val detailedFeedback = if (locale == "ko") {
            buildString {
                append("이 답변은 ")
                append(
                    when {
                        input.totalScore >= 80 -> "질문의 핵심을 비교적 잘 짚고 있고 기본 구조도 안정적입니다. "
                        input.totalScore >= 60 -> "핵심 방향은 맞지만 설득력을 높이려면 구조와 근거를 더 분명히 해야 합니다. "
                        else -> "기본 방향은 보이지만 면접 답변으로는 아직 근거, 구조, 깊이가 많이 부족합니다. "
                    },
                )
                append(
                    when {
                        input.specificityScore < 60 -> "특히 구체적 사례, 수치, 실제 의사결정 근거가 부족해서 추상적으로 들릴 수 있습니다. "
                        input.structureScore < 60 -> "답변 흐름이 약해서 면접관이 핵심 메시지를 따라가기 어렵습니다. "
                        else -> "다만 트레이드오프와 검증 방식까지 더 또렷하게 말하면 훨씬 강한 답변이 됩니다. "
                    },
                )
                append("정의 또는 상황 설명 -> 선택 기준 -> 실제 적용 -> 결과/검증 순으로 재구성하는 것이 좋습니다.")
            }
        } else {
            buildString {
                append(
                    when {
                        input.totalScore >= 80 -> "The answer covers the core of the question reasonably well and already has a usable structure. "
                        input.totalScore >= 60 -> "The direction is mostly right, but the answer still needs clearer structure and stronger proof. "
                        else -> "The answer shows a rough direction, but it is still too thin in evidence, structure, and depth for an interview setting. "
                    },
                )
                append(
                    when {
                        input.specificityScore < 60 -> "It especially needs more concrete examples, metrics, and decision evidence. "
                        input.structureScore < 60 -> "The flow is too loose, so the interviewer may struggle to follow the main point. "
                        else -> "It would become much stronger if it made trade-offs and validation methods more explicit. "
                    },
                )
                append("Rebuild it in the order of context, decision criteria, implementation, and validation.")
            }
        }
        val strengthPoints = if (locale == "ko") {
            listOfNotNull(
                if (input.totalScore >= 60) "질문을 완전히 벗어나지 않고 핵심 주제를 따라가고 있습니다." else null,
                if (input.structureScore >= 60) "답변 흐름에 최소한의 시작점과 결론이 보입니다." else null,
                if (input.technicalAccuracyScore >= 60) "기술 용어와 접근 방향이 크게 어긋나지 않습니다." else null,
            ).ifEmpty { listOf("핵심 개념을 전혀 모르는 상태는 아니며 보완 여지가 있는 초안 수준입니다.") }
        } else {
            listOfNotNull(
                if (input.totalScore >= 60) "The answer stays broadly on the core topic instead of drifting away." else null,
                if (input.structureScore >= 60) "There is at least a minimal flow from setup to explanation." else null,
                if (input.technicalAccuracyScore >= 60) "The technical direction is not fundamentally off." else null,
            ).ifEmpty { listOf("The answer reads like a rough first draft rather than a completely uninformed response.") }
        }
        val improvementPoints = if (locale == "ko") {
            listOf(
                "구체적인 상황, 제약, 선택 기준을 먼저 분명히 말하세요.",
                "가능하면 수치, 관측 지표, 전후 비교를 넣어 답변의 신뢰도를 높이세요.",
                "다른 대안 대신 현재 접근을 택한 이유와 트레이드오프를 명시하세요.",
            )
        } else {
            listOf(
                "State the concrete context, constraints, and decision criteria earlier.",
                "Add metrics, validation signals, or before/after comparisons when possible.",
                "Explain why this approach was chosen over alternatives and what trade-off it carries.",
            )
        }
        val missedPoints = if (locale == "ko") {
            listOf(
                "검증 방식이나 결과 측정 기준",
                "대안 비교와 트레이드오프 설명",
                "실제 사례 또는 운영 경험 기반 근거",
            )
        } else {
            listOf(
                "Validation method or success metric",
                "Alternative comparison and trade-off explanation",
                "Concrete operational example or production evidence",
            )
        }
        val modelAnswerText = if (locale == "ko") {
            buildString {
                append("이 질문에는 먼저 핵심 상황이나 문제를 한두 문장으로 정의하고, 어떤 기준으로 접근 방식을 선택했는지 설명하는 답변이 좋습니다. ")
                append("그 다음 실제 적용 방식, 고려한 트레이드오프, 그리고 결과를 어떻게 검증했는지 순서대로 말해야 합니다. ")
                append("예를 들면 문제의 원인을 파악한 뒤 성능, 안정성, 운영 복잡도 같은 기준으로 선택지를 비교했고, 최종안 적용 후 어떤 지표가 개선됐는지까지 연결해 마무리하는 형태가 가장 설득력 있습니다.")
            }
        } else {
            buildString {
                append("A strong answer should first define the concrete problem or context, then explain the criteria used to choose an approach. ")
                append("After that, walk through the implementation path, the trade-offs you accepted, and how you validated the result. ")
                append("The most convincing version closes with measurable impact or a clear operational outcome.")
            }
        }
        return GeneratedAnswerDeepFeedback(
            detailedFeedback = detailedFeedback,
            strengthPoints = strengthPoints,
            improvementPoints = improvementPoints,
            missedPoints = missedPoints,
            modelAnswerText = modelAnswerText,
            llmModel = null,
            contentLocale = locale,
        )
    }

    private fun encodeList(values: List<String>): String = objectMapper.writeValueAsString(values)

    private fun weighted(value: Double): BigDecimal = value.coerceIn(0.0, 100.0).toBigDecimal().setScale(2, RoundingMode.HALF_UP)

    private companion object {
        val TRADEOFF_HINTS = listOf("tradeoff", "however", "instead", "versus", "vs", "cost")
        val EXAMPLE_HINTS = listOf("for example", "for instance", "because", "measured", "latency", "throughput")
    }
}
