package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiPracticalInterviewStructuringEnrichmentClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.interview.llm.api-key:}")
    private val apiKey: String,
    @Value("\${app.interview.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.interview.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.interview.practical-enrichment.prompt-version:practical-interview-structuring-v1}")
    private val promptVersion: String,
    @Value("\${app.interview.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : PracticalInterviewStructuringEnrichmentClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun enrich(input: PracticalInterviewStructuringEnrichmentInput): PracticalInterviewStructuringEnrichment {
        require(isEnabled()) { "OpenAI practical interview structuring enrichment is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response)
    }

    private fun buildRequest(input: PracticalInterviewStructuringEnrichmentInput): Map<String, Any> = mapOf(
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
                "name" to "practical_interview_structuring_enrichment",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String): PracticalInterviewStructuringEnrichment {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI practical interview structuring response did not include output_text")
        val payload = objectMapper.readTree(outputText)
        return PracticalInterviewStructuringEnrichment(
            overallSummary = payload.path("overallSummary").asText(null)?.trim()?.ifBlank { null },
            questions = payload.path("questions").map { node ->
                PracticalInterviewStructuringQuestionEnrichment(
                    orderIndex = node.path("orderIndex").asInt(),
                    questionType = node.path("questionType").asText(null)?.trim()?.ifBlank { null },
                    topicTags = node.path("topicTags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct().ifEmpty { null },
                    intentTags = node.path("intentTags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct().ifEmpty { null },
                    parentOrderIndex = node.path("parentOrderIndex").takeIf(JsonNode::isInt)?.asInt(),
                    answerSummary = node.path("answerSummary").asText(null)?.trim()?.ifBlank { null },
                    weaknessTags = node.path("weaknessTags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct().ifEmpty { null },
                    strengthTags = node.path("strengthTags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct().ifEmpty { null },
                    confidenceMarkers = node.path("confidenceMarkers").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct().ifEmpty { null },
                    analysis = node.path("analysis").takeIf(JsonNode::isObject)?.let(::decodeAnalysis),
                )
            },
            interviewerProfile = payload.path("interviewerProfile").takeIf(JsonNode::isObject)?.let { node ->
                PracticalInterviewInterviewerProfileOverride(
                    styleTags = node.path("styleTags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
                    toneProfile = node.path("toneProfile").asText(null)?.trim()?.ifBlank { null },
                    pressureLevel = node.path("pressureLevel").asText(null)?.trim()?.ifBlank { null },
                    depthPreference = node.path("depthPreference").asText(null)?.trim()?.ifBlank { null },
                    followUpPatterns = node.path("followUpPatterns").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
                    favoriteTopics = node.path("favoriteTopics").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
                    openingPattern = node.path("openingPattern").asText(null)?.trim()?.ifBlank { null },
                    closingPattern = node.path("closingPattern").asText(null)?.trim()?.ifBlank { null },
                )
            },
        )
    }

    private fun systemPrompt(): String = """
        You are refining structured data extracted from a real software engineering interview transcript.
        A deterministic parser has already split the transcript into interviewer questions and candidate answers.
        Your job is to improve the structure, not to invent unrelated content.
        You may refine:
        - overall interview summary
        - question type
        - topic tags
        - intent tags
        - parent follow-up linkage by order index
        - concise answer summary
        - weakness tags
        - strength tags
        - confidence markers
        - interviewer profile style/tone/pressure/depth/follow-up patterns/favorite topics

        Rules:
        - Keep changes grounded in the transcript.
        - Do not fabricate resume evidence or job posting evidence.
        - Do not rewrite the question text or answer text themselves.
        - Use stable machine-readable tags in English snake_case or kebab-case where appropriate.
        - questionType must stay within realistic backend-supported categories such as intro, motivation, project, technical_depth, system_design, general.
        - parentOrderIndex should point only to earlier questions and only when the current question is clearly a follow-up or drill-down.
        - answerSummary should be concise and useful for later review.
        - weaknessTags should capture gaps such as missing_metrics, missing_tradeoff, missing_star_shape, vague_reasoning, low_specificity.
        - strengthTags should capture strengths such as quantified, detailed, structured, tradeoff_aware, ownership_clear.
        - confidenceMarkers should capture signals such as quantified, uncertain, concrete_scope, production_validation.
        - interviewerProfile should describe the interviewer from the transcript, not a generic coach.
        - Return only schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: PracticalInterviewStructuringEnrichmentInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
        appendLine("Output language: ${input.outputLanguage}")
        input.companyName?.let { appendLine("Company: $it") }
        input.roleName?.let { appendLine("Role: $it") }
        input.interviewType?.let { appendLine("Interview type: $it") }
        input.deterministicSummary?.let {
            appendLine()
            appendLine("Deterministic summary:")
            appendLine(it)
        }
        appendLine()
        appendLine("Transcript:")
        appendLine(input.transcriptText)
        appendLine()
        appendLine("Deterministic structured questions:")
        input.questions.forEach { question ->
            appendLine("- order=${question.orderIndex} type=${question.questionType} parent=${question.parentOrderIndex ?: "-"}")
            appendLine("  Q: ${question.text}")
            appendLine("  topicTags=${question.topicTags.joinToString(", ")} intentTags=${question.intentTags.joinToString(", ")}")
            question.answerText?.let { appendLine("  A: $it") }
            question.answerSummary?.let { appendLine("  answerSummary=$it") }
            if (question.weaknessTags.isNotEmpty()) appendLine("  weaknessTags=${question.weaknessTags.joinToString(", ")}")
            if (question.strengthTags.isNotEmpty()) appendLine("  strengthTags=${question.strengthTags.joinToString(", ")}")
        }
        appendLine()
        appendLine("Refine the structure conservatively. Prefer correcting obvious mistakes and improving summaries/tags over large rewrites.")
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "overallSummary" to nullableStringSchema(),
            "questions" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "orderIndex" to mapOf("type" to "integer"),
                        "questionType" to nullableStringSchema(),
                        "topicTags" to arrayOfStringsSchema(),
                        "intentTags" to arrayOfStringsSchema(),
                        "parentOrderIndex" to mapOf("type" to listOf("integer", "null")),
                        "answerSummary" to nullableStringSchema(),
                        "weaknessTags" to arrayOfStringsSchema(),
                        "strengthTags" to arrayOfStringsSchema(),
                        "confidenceMarkers" to arrayOfStringsSchema(),
                        "analysis" to mapOf("type" to listOf("object", "null"), "additionalProperties" to true),
                    ),
                    "required" to listOf(
                        "orderIndex",
                        "questionType",
                        "topicTags",
                        "intentTags",
                        "parentOrderIndex",
                        "answerSummary",
                        "weaknessTags",
                        "strengthTags",
                        "confidenceMarkers",
                        "analysis",
                    ),
                ),
            ),
            "interviewerProfile" to mapOf(
                "type" to listOf("object", "null"),
                "additionalProperties" to false,
                "properties" to mapOf(
                    "styleTags" to arrayOfStringsSchema(),
                    "toneProfile" to nullableStringSchema(),
                    "pressureLevel" to nullableStringSchema(),
                    "depthPreference" to nullableStringSchema(),
                    "followUpPatterns" to arrayOfStringsSchema(),
                    "favoriteTopics" to arrayOfStringsSchema(),
                    "openingPattern" to nullableStringSchema(),
                    "closingPattern" to nullableStringSchema(),
                ),
                "required" to listOf(
                    "styleTags",
                    "toneProfile",
                    "pressureLevel",
                    "depthPreference",
                    "followUpPatterns",
                    "favoriteTopics",
                    "openingPattern",
                    "closingPattern",
                ),
            ),
        ),
        "required" to listOf("overallSummary", "questions", "interviewerProfile"),
    )

    private fun nullableStringSchema(): Map<String, Any> = mapOf("type" to listOf("string", "null"))

    private fun arrayOfStringsSchema(): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to mapOf("type" to "string"),
    )

    private fun decodeAnalysis(node: JsonNode): Map<String, Any> {
        val raw = objectMapper.convertValue(node, Map::class.java)
        return raw.entries.associate { (key, value) -> key.toString() to value as Any }
    }
}
