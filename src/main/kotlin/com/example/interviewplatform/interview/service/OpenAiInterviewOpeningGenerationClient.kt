package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiInterviewOpeningGenerationClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.interview.llm.api-key:}")
    private val apiKey: String,
    @Value("\${app.interview.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.interview.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.interview.llm.prompt-version:interview-opening-v1}")
    private val promptVersion: String,
    @Value("\${app.interview.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : InterviewOpeningGenerationClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun generate(input: InterviewOpeningGenerationInput): GeneratedInterviewOpening {
        require(isEnabled()) { "OpenAI interview opening generation is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response)
    }

    private fun buildRequest(input: InterviewOpeningGenerationInput): Map<String, Any> = mapOf(
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
                "name" to "interview_opening",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String): GeneratedInterviewOpening {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI interview opening response did not include output_text")
        val payload = objectMapper.readTree(outputText)
        val promptText = payload.path("promptText").asText("").trim()
        if (promptText.isBlank()) {
            throw IllegalStateException("OpenAI interview opening response returned a blank promptText")
        }
        return GeneratedInterviewOpening(
            promptText = promptText,
            bodyText = payload.path("bodyText").asText(null)?.trim()?.ifBlank { null },
            tags = payload.path("tags").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
            focusSkillNames = payload.path("focusSkillNames").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } }.distinct(),
            resumeContextSummary = payload.path("resumeContextSummary").asText(null)?.trim()?.ifBlank { null },
            generationRationale = payload.path("generationRationale").asText("").trim().ifBlank {
                "Generated opening question from the selected resume context."
            },
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
        )
    }

    private fun systemPrompt(): String = """
        You are generating the opening interview question for a software engineer mock interview.
        Ground the question in the candidate's resume evidence.
        Ask one strong interviewer-style question that opens the mock interview.
        Do not ask multiple questions.
        promptText should be concise and interview-ready.
        bodyText may add constraints or what the interviewer expects in the answer.
        tags should be short topic labels.
        focusSkillNames should align to technical or behavioral skills being assessed.
        Return only schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: InterviewOpeningGenerationInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
        input.resumeSummaryText?.takeIf { it.isNotBlank() }?.let {
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
