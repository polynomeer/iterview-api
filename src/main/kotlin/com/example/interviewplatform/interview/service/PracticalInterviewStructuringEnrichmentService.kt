package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import org.springframework.stereotype.Service

@Service
class PracticalInterviewStructuringEnrichmentService(
    private val client: PracticalInterviewStructuringEnrichmentClient,
    private val appLocaleService: AppLocaleService,
) {
    fun enrich(
        record: InterviewRecordEntity,
        transcriptText: String,
        parsedTranscript: ParsedInterviewTranscript,
    ): ParsedInterviewTranscript {
        if (!client.isEnabled() || parsedTranscript.questions.isEmpty()) {
            return parsedTranscript
        }
        val deterministicSummary = buildDeterministicSummary(parsedTranscript.questions)
        val input = PracticalInterviewStructuringEnrichmentInput(
            outputLanguage = appLocaleService.resolveLanguage(),
            companyName = record.companyName,
            roleName = record.roleName,
            interviewType = record.interviewType,
            transcriptText = transcriptText,
            deterministicSummary = deterministicSummary,
            questions = parsedTranscript.questions.map { question ->
                PracticalInterviewStructuringQuestionInput(
                    orderIndex = question.orderIndex,
                    text = question.text,
                    questionType = question.questionType,
                    topicTags = question.topicTags,
                    intentTags = question.intentTags,
                    parentOrderIndex = question.parentOrderIndex,
                    answerText = question.answer?.text,
                    answerSummary = question.answer?.summary,
                    weaknessTags = question.answer?.weaknessTags.orEmpty(),
                    strengthTags = question.answer?.strengthTags.orEmpty(),
                )
            },
        )
        val enrichment = runCatching { client.enrich(input) }.getOrNull() ?: return parsedTranscript
        val questionOverridesByOrder = enrichment.questions.associateBy { it.orderIndex }
        return ParsedInterviewTranscript(
            segments = parsedTranscript.segments,
            questions = parsedTranscript.questions.map { question ->
                val override = questionOverridesByOrder[question.orderIndex]
                question.copy(
                    questionType = override?.questionType ?: question.questionType,
                    topicTags = override?.topicTags ?: question.topicTags,
                    intentTags = override?.intentTags ?: question.intentTags,
                    parentOrderIndex = override?.parentOrderIndex ?: question.parentOrderIndex,
                    answer = question.answer?.let { answer ->
                        answer.copy(
                            summary = override?.answerSummary ?: answer.summary,
                            weaknessTags = override?.weaknessTags ?: answer.weaknessTags,
                            strengthTags = override?.strengthTags ?: answer.strengthTags,
                            confidenceMarkers = override?.confidenceMarkers ?: answer.confidenceMarkers,
                            analysis = override?.analysis ?: answer.analysis,
                        )
                    },
                )
            },
            overallSummaryOverride = enrichment.overallSummary,
            interviewerProfileOverride = enrichment.interviewerProfile,
            structuringSource = if (hasMeaningfulOverrides(parsedTranscript, enrichment)) "ai_enriched" else parsedTranscript.structuringSource,
        )
    }

    private fun buildDeterministicSummary(questions: List<ParsedQuestion>): String =
        buildString {
            append("Imported ${questions.size} interview questions")
            val followUpCount = questions.count { it.parentOrderIndex != null }
            if (followUpCount > 0) {
                append(" with $followUpCount follow-up probes")
            }
            append(" across ${questions.flatMap { it.topicTags }.distinct().joinToString(", ").ifBlank { "general" }}.")
        }

    private fun hasMeaningfulOverrides(
        parsedTranscript: ParsedInterviewTranscript,
        enrichment: PracticalInterviewStructuringEnrichment,
    ): Boolean {
        if (!enrichment.overallSummary.isNullOrBlank() || enrichment.interviewerProfile != null) {
            return true
        }
        val parsedByOrder = parsedTranscript.questions.associateBy { it.orderIndex }
        return enrichment.questions.any { override ->
            val existing = parsedByOrder[override.orderIndex] ?: return@any true
            override.questionType?.let { it != existing.questionType } == true ||
                override.topicTags?.let { it != existing.topicTags } == true ||
                override.intentTags?.let { it != existing.intentTags } == true ||
                override.parentOrderIndex?.let { it != existing.parentOrderIndex } == true ||
                override.answerSummary?.let { it != existing.answer?.summary } == true ||
                override.weaknessTags?.let { it != existing.answer?.weaknessTags } == true ||
                override.strengthTags?.let { it != existing.answer?.strengthTags } == true ||
                override.confidenceMarkers?.let { it != existing.answer?.confidenceMarkers } == true ||
                override.analysis?.let { it != existing.answer?.analysis } == true
        }
    }
}
