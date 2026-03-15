package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiInterviewFollowUpGenerationClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.interview.llm.api-key:}")
    private val apiKey: String,
    @Value("\${app.interview.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.interview.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.interview.llm.prompt-version:interview-follow-up-v1}")
    private val promptVersion: String,
    @Value("\${app.interview.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : InterviewFollowUpGenerationClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun generate(input: InterviewFollowUpGenerationInput): GeneratedInterviewFollowUp {
        require(isEnabled()) { "OpenAI interview follow-up generation is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response, input)
    }

    private fun buildRequest(input: InterviewFollowUpGenerationInput): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf(
                "role" to "system",
                "content" to listOf(mapOf("type" to "input_text", "text" to systemPrompt())),
            ),
            mapOf(
                "role" to "user",
                "content" to listOf(mapOf("type" to "input_text", "text" to userPrompt(input))),
            ),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "interview_follow_up",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String, input: InterviewFollowUpGenerationInput): GeneratedInterviewFollowUp {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI interview follow-up response did not include output_text")
        val payload = objectMapper.readTree(outputText)
        val promptText = payload.path("promptText").asText("").trim()
        if (promptText.isBlank()) {
            throw IllegalStateException("OpenAI interview follow-up response returned a blank promptText")
        }
        return GeneratedInterviewFollowUp(
            promptText = promptText,
            bodyText = payload.path("bodyText").asText(null)?.trim()?.ifBlank { null },
            tags = payload.path("tags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
            focusSkillNames = payload.path("focusSkillNames").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
            resumeContextSummary = payload.path("resumeContextSummary").asText(null)?.trim()?.ifBlank { null },
            resumeEvidence = payload.path("resumeEvidence").mapNotNull(::parseResumeEvidenceItem),
            generationRationale = payload.path("generationRationale").asText("").trim().ifBlank {
                defaultGenerationRationale(input.outputLanguage)
            },
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
            contentLocale = input.outputLanguage,
        )
    }

    private fun systemPrompt(): String = """
        You are generating a single interview follow-up question for a software engineer mock interview.
        Use the answer and the resume context to ask a concrete, defensible, technically relevant follow-up.
        Do not ask multiple questions.
        The follow-up should feel like a real interviewer noticed a gap, weak reasoning step, vague claim, missing metric, unexplained trade-off, or shallow technical explanation.
        If the resume evidence candidates include facets such as problem, action, result, metric, or tradeoff, prefer a follow-up that attacks a different unresolved facet from the parent question instead of repeating the same high-level summary angle.
        If replay interview metadata is provided, preserve the imported interviewer style, pressure level, depth preference, and follow-up patterns instead of sounding like a generic coach.
        In replay mode, use the imported question and answer examples as style references, not as text to copy verbatim.
        Prefer one of these follow-up styles:
        1. STAR deepening: ask for the missing situation, task, action, result, metric, or decision point.
        2. Evidence challenge: ask the candidate to justify a claim from the answer or the resume with specific evidence.
        3. Technical drill-down: ask how a technology, architecture, failure mode, or implementation detail actually worked.
        4. Scenario extension: introduce a realistic constraint, outage, scale jump, latency target, or conflicting requirement and ask what they would do.
        Respect the preferred follow-up style hint when one is provided.
        - weak answers should usually bias toward evidence challenge or STAR deepening
        - medium answers should usually bias toward technical drill-down
        - strong answers should usually bias toward scenario extension or trade-off pressure-testing
        Match the question angle to the active evidence facet when possible:
        - problem: ask about original constraints, root cause, why it was difficult, or what made the issue important
        - action: ask about implementation details, execution order, ownership boundaries, or decision points
        - result: ask how the result was validated, what changed afterward, or whether the outcome held up in production
        - metric: ask for baselines, targets, instrumentation, thresholds, and metric reliability
        - tradeoff: ask what alternatives were rejected, what downside was accepted, and whether they would choose differently now
        Keep promptText concise and interview-ready.
        bodyText should add the exact lens the interviewer wants: metrics, trade-offs, rollback criteria, design assumptions, communication, ownership, or failure handling.
        Avoid generic "tell me more" style questions.
        Avoid repeating the parent question with only superficial wording changes.
        Generate promptText, bodyText, tags, focusSkillNames, resumeContextSummary, and generationRationale in the requested output language.
        Do not translate resumeEvidence.snippet. Keep resume evidence in its original source language.
        tags should be short topic labels.
        focusSkillNames should align to technical or behavioral skills likely being assessed.
        resumeEvidence should contain one or two short, specific resume snippets that justify why this follow-up remains grounded in the resume.
        Prefer evidence tied to a concrete project, experience, award, certification, or education record.
        generationRationale should explain what gap or unresolved point triggered this follow-up.
        Return only schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: InterviewFollowUpGenerationInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
        appendLine("Output language: ${languageName(input.outputLanguage)} (${input.outputLanguage})")
        appendLine("Answer quality signal: ${input.answerQualitySignal}")
        appendLine("Preferred follow-up style: ${input.preferredFollowUpStyle}")
        appendLine("Parent question:")
        appendLine(input.parentPromptText)
        input.parentBodyText?.let {
            appendLine()
            appendLine("Parent question details:")
            appendLine(it)
        }
        appendLine()
        appendLine("Candidate answer:")
        appendLine(input.answerText)
        input.resumeSummaryText?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Resume summary:")
            appendLine(it)
        }
        if (input.resumeSkillNames.isNotEmpty()) {
            appendLine()
            appendLine("Resume skills: ${input.resumeSkillNames.joinToString(", ")}")
        }
        if (input.resumeProjectSummaries.isNotEmpty()) {
            appendLine()
            appendLine("Resume projects:")
            input.resumeProjectSummaries.forEach { appendLine("- $it") }
        }
        if (input.resumeRiskSummaries.isNotEmpty()) {
            appendLine()
            appendLine("Resume risk areas:")
            input.resumeRiskSummaries.forEach { appendLine("- $it") }
        }
        if (input.resumeEvidenceCandidates.isNotEmpty()) {
            appendLine()
            appendLine("Resume evidence candidates:")
            input.resumeEvidenceCandidates.forEach { candidate ->
                appendLine("- [${candidate.sourceRecordType}:${candidate.sourceRecordId}] section=${candidate.section} facet=${candidate.facet} label=${candidate.label ?: "-"} snippet=${candidate.snippet}")
            }
        }
        if (input.parentResumeEvidenceCandidates.isNotEmpty()) {
            appendLine()
            appendLine("Parent question resume evidence:")
            input.parentResumeEvidenceCandidates.forEach { candidate ->
                appendLine("- [${candidate.sourceRecordType}:${candidate.sourceRecordId}] section=${candidate.section} facet=${candidate.facet} label=${candidate.label ?: "-"} snippet=${candidate.snippet}")
            }
        }
        if (input.preferredResumeEvidenceCandidates.isNotEmpty()) {
            appendLine()
            appendLine("Preferred follow-up evidence candidates:")
            input.preferredResumeEvidenceCandidates.forEach { candidate ->
                appendLine("- [${candidate.sourceRecordType}:${candidate.sourceRecordId}] section=${candidate.section} facet=${candidate.facet} label=${candidate.label ?: "-"} snippet=${candidate.snippet}")
            }
        }
        if (input.usedFacetsForPreferredRecord.isNotEmpty()) {
            appendLine()
            appendLine("Already covered facets for this record: ${input.usedFacetsForPreferredRecord.joinToString(", ")}")
        }
        if (input.parentTags.isNotEmpty()) {
            appendLine()
            appendLine("Parent tags: ${input.parentTags.joinToString(", ")}")
        }
        if (input.parentFocusSkillNames.isNotEmpty()) {
            appendLine()
            appendLine("Parent focus skills: ${input.parentFocusSkillNames.joinToString(", ")}")
        }
        input.replayMode?.let {
            appendLine()
            appendLine("Replay mode: $it")
        }
        input.importedRecordSummary?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Imported interview summary:")
            appendLine(it)
        }
        if (input.interviewerStyleTags.isNotEmpty() || input.interviewerToneProfile != null || input.interviewerPressureLevel != null || input.interviewerDepthPreference != null) {
            appendLine()
            appendLine("Imported interviewer profile:")
            input.interviewerToneProfile?.let { appendLine("- tone=$it") }
            input.interviewerPressureLevel?.let { appendLine("- pressure=$it") }
            input.interviewerDepthPreference?.let { appendLine("- depth=$it") }
            if (input.interviewerStyleTags.isNotEmpty()) appendLine("- styleTags=${input.interviewerStyleTags.joinToString(", ")}")
            if (input.interviewerFavoriteTopics.isNotEmpty()) appendLine("- favoriteTopics=${input.interviewerFavoriteTopics.joinToString(", ")}")
            if (input.interviewerFollowUpPatterns.isNotEmpty()) appendLine("- followUpPatterns=${input.interviewerFollowUpPatterns.joinToString(", ")}")
        }
        if (input.importedQuestionExamples.isNotEmpty()) {
            appendLine()
            appendLine("Imported interview examples:")
            input.importedQuestionExamples.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("Follow-up generation goal:")
        appendLine("Use the answer quality and resume evidence to ask the next most revealing single question, not just a keyword-adjacent question.")
        appendLine("If preferred follow-up evidence candidates are provided, choose one of them first unless the answer clearly requires a harder challenge on the same exact claim.")
        appendLine("Honor the preferred follow-up style when possible: weak=evidence challenge or STAR deepening, medium=technical drill-down, strong=scenario extension or trade-off pressure test.")
        appendLine("If the answer was vague, ask for concrete evidence, metrics, STAR detail, or a decision process.")
        appendLine("If the answer was strong but incomplete, ask for technical depth, trade-offs, failure handling, or a realistic what-if constraint.")
        appendLine("Map the drill-down to the chosen facet when possible: problem=context and constraints, action=implementation and choices, result=validation and impact, metric=measurement and thresholds, tradeoff=alternatives and accepted downside.")
        appendLine("Avoid reusing an already-covered facet when another unresolved facet from the same record is available.")
        appendLine("Prefer drilling into one concrete claim or sentence from the same project rather than asking for another broad project overview.")
        appendLine("If replay interviewer profile metadata is present, keep the follow-up style aligned with that imported interviewer instead of producing a neutral generic question.")
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "promptText" to stringSchema(),
            "bodyText" to nullableStringSchema(),
            "tags" to arrayOfStringsSchema(),
            "focusSkillNames" to arrayOfStringsSchema(),
            "resumeContextSummary" to nullableStringSchema(),
            "resumeEvidence" to resumeEvidenceArraySchema(),
            "generationRationale" to stringSchema(),
        ),
        "required" to listOf(
            "promptText",
            "bodyText",
            "tags",
            "focusSkillNames",
            "resumeContextSummary",
            "resumeEvidence",
            "generationRationale",
        ),
    )

    private fun stringSchema(): Map<String, Any> = mapOf("type" to "string")

    private fun nullableStringSchema(): Map<String, Any> = mapOf("type" to listOf("string", "null"))

    private fun arrayOfStringsSchema(): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to stringSchema(),
    )

    private fun resumeEvidenceArraySchema(): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "type" to stringSchema(),
                "section" to nullableStringSchema(),
                "label" to nullableStringSchema(),
                "snippet" to stringSchema(),
                "sourceRecordType" to nullableStringSchema(),
                "sourceRecordId" to mapOf("type" to listOf("integer", "null")),
                "confidence" to mapOf("type" to listOf("number", "null")),
                "startOffset" to mapOf("type" to listOf("integer", "null")),
                "endOffset" to mapOf("type" to listOf("integer", "null")),
            ),
            "required" to listOf(
                "type",
                "section",
                "label",
                "snippet",
                "sourceRecordType",
                "sourceRecordId",
                "confidence",
                "startOffset",
                "endOffset",
            ),
        ),
    )

    private fun parseResumeEvidenceItem(node: JsonNode): GeneratedInterviewResumeEvidence? {
        val snippet = node.path("snippet").asText("").trim()
        if (snippet.isBlank()) {
            return null
        }
        return GeneratedInterviewResumeEvidence(
            type = node.path("type").asText("resume_sentence").trim().ifBlank { "resume_sentence" },
            section = node.path("section").asText(null)?.trim()?.ifBlank { null },
            label = node.path("label").asText(null)?.trim()?.ifBlank { null },
            snippet = snippet,
            sourceRecordType = node.path("sourceRecordType").asText(null)?.trim()?.ifBlank { null },
            sourceRecordId = node.path("sourceRecordId").takeIf { !it.isMissingNode && !it.isNull }?.asLong(),
            confidence = node.path("confidence").takeIf { !it.isMissingNode && !it.isNull }?.asDouble(),
            startOffset = node.path("startOffset").takeIf { !it.isMissingNode && !it.isNull }?.asInt(),
            endOffset = node.path("endOffset").takeIf { !it.isMissingNode && !it.isNull }?.asInt(),
        )
    }

    private fun languageName(language: String): String = when (language.lowercase()) {
        "en" -> "English"
        else -> "Korean"
    }

    private fun defaultGenerationRationale(language: String): String =
        if (language.lowercase() == "en") {
            "Generated follow-up based on the answer and active resume context."
        } else {
            "답변과 현재 이력서 맥락을 바탕으로 꼬리질문을 생성했습니다."
        }
}
