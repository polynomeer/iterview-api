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
        return parseResponse(response)
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

    private fun parseResponse(responseBody: String): GeneratedInterviewFollowUp {
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
            generationRationale = payload.path("generationRationale").asText("").trim().ifBlank {
                "Generated follow-up based on the answer and active resume context."
            },
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
        )
    }

    private fun systemPrompt(): String = """
        You are generating a single interview follow-up question for a software engineer mock interview.
        Use the answer and the resume context to ask a concrete, defensible, technically relevant follow-up.
        Do not ask multiple questions.
        The follow-up should feel like a real interviewer noticed a gap, weak reasoning step, vague claim, missing metric, unexplained trade-off, or shallow technical explanation.
        Prefer one of these follow-up styles:
        1. STAR deepening: ask for the missing situation, task, action, result, metric, or decision point.
        2. Evidence challenge: ask the candidate to justify a claim from the answer or the resume with specific evidence.
        3. Technical drill-down: ask how a technology, architecture, failure mode, or implementation detail actually worked.
        4. Scenario extension: introduce a realistic constraint, outage, scale jump, latency target, or conflicting requirement and ask what they would do.
        Keep promptText concise and interview-ready.
        bodyText should add the exact lens the interviewer wants: metrics, trade-offs, rollback criteria, design assumptions, communication, ownership, or failure handling.
        Avoid generic "tell me more" style questions.
        Avoid repeating the parent question with only superficial wording changes.
        tags should be short topic labels.
        focusSkillNames should align to technical or behavioral skills likely being assessed.
        generationRationale should explain what gap or unresolved point triggered this follow-up.
        Return only schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: InterviewFollowUpGenerationInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
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
        if (input.parentTags.isNotEmpty()) {
            appendLine()
            appendLine("Parent tags: ${input.parentTags.joinToString(", ")}")
        }
        if (input.parentFocusSkillNames.isNotEmpty()) {
            appendLine()
            appendLine("Parent focus skills: ${input.parentFocusSkillNames.joinToString(", ")}")
        }
        appendLine()
        appendLine("Follow-up generation goal:")
        appendLine("Use the answer quality and resume evidence to ask the next most revealing single question, not just a keyword-adjacent question.")
        appendLine("If the answer was vague, ask for concrete evidence, metrics, STAR detail, or a decision process.")
        appendLine("If the answer was strong but incomplete, ask for technical depth, trade-offs, failure handling, or a realistic what-if constraint.")
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
            "generationRationale" to stringSchema(),
        ),
        "required" to listOf(
            "promptText",
            "bodyText",
            "tags",
            "focusSkillNames",
            "resumeContextSummary",
            "generationRationale",
        ),
    )

    private fun stringSchema(): Map<String, Any> = mapOf("type" to "string")

    private fun nullableStringSchema(): Map<String, Any> = mapOf("type" to listOf("string", "null"))

    private fun arrayOfStringsSchema(): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to stringSchema(),
    )
}
