package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiResumeStructuredExtractionClient(
    private val objectMapper: ObjectMapper,
    private val transport: ResumeLlmApiTransport,
    @Value("\${app.resume.llm.api-key:}")
    private val apiKey: String,
    @Value("\${app.resume.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.resume.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.resume.llm.prompt-version:resume-extract-v1}")
    private val promptVersion: String,
    @Value("\${app.resume.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : ResumeStructuredExtractionClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun extract(version: ResumeVersionEntity): ExtractedResumeSignals {
        require(isEnabled()) { "OpenAI extraction is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(version))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response)
    }

    private fun buildRequest(version: ResumeVersionEntity): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf(
                "role" to "system",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to buildSystemPrompt(),
                    ),
                ),
            ),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to buildUserPrompt(version),
                    ),
                ),
            ),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "resume_extraction",
                "strict" to true,
                "schema" to extractionSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String): ExtractedResumeSignals {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI extraction response did not include output_text")
        val extractedRoot = objectMapper.readTree(outputText)
        return ExtractedResumeSignals(
            skills = extractedRoot.path("skills").map { node ->
                ExtractedResumeSkill(
                    skillName = node.path("skillName").asText(),
                    sourceText = node.path("sourceText").asText(null),
                    confidenceScore = node.path("confidenceScore").takeIf(JsonNode::isNumber)?.asDouble(),
                )
            },
            experiences = extractedRoot.path("experiences").mapIndexed { index, node ->
                ExtractedResumeExperience(
                    projectName = node.path("projectName").asText(null),
                    summaryText = node.path("summaryText").asText(),
                    impactText = node.path("impactText").asText(null),
                    sourceText = node.path("sourceText").asText(),
                    riskLevel = node.path("riskLevel").asText("medium"),
                    displayOrder = index + 1,
                )
            },
            risks = extractedRoot.path("risks").map { node ->
                ExtractedResumeRisk(
                    riskType = node.path("riskType").asText(),
                    title = node.path("title").asText(),
                    description = node.path("description").asText(),
                    severity = node.path("severity").asText(),
                )
            },
            sourceType = "openai",
            extractionStatus = "completed",
            extractionErrorMessage = null,
            extractionConfidence = extractedRoot.path("overallConfidence").takeIf(JsonNode::isNumber)?.asDouble(),
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
            rawExtractionPayload = outputText,
        )
    }

    private fun buildSystemPrompt(): String = """
        You extract structured interview-preparation signals from a software engineer's resume.
        Return only schema-compliant JSON.
        Preserve claims from the resume text rather than inventing missing details.
        Normalize riskLevel to low, medium, or high.
        Normalize severity to LOW, MEDIUM, or HIGH.
    """.trimIndent()

    private fun buildUserPrompt(version: ResumeVersionEntity): String = buildString {
        appendLine("Prompt version: $promptVersion")
        version.summaryText?.takeIf { it.isNotBlank() }?.let {
            appendLine("Resume summary:")
            appendLine(it.trim())
            appendLine()
        }
        appendLine("Resume raw text:")
        appendLine(version.rawText.orEmpty().trim())
    }

    private fun extractionSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "skills" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "skillName" to mapOf("type" to "string"),
                        "sourceText" to mapOf("type" to listOf("string", "null")),
                        "confidenceScore" to mapOf("type" to listOf("number", "null")),
                    ),
                    "required" to listOf("skillName", "sourceText", "confidenceScore"),
                ),
            ),
            "experiences" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "projectName" to mapOf("type" to listOf("string", "null")),
                        "summaryText" to mapOf("type" to "string"),
                        "impactText" to mapOf("type" to listOf("string", "null")),
                        "sourceText" to mapOf("type" to "string"),
                        "riskLevel" to mapOf("type" to "string", "enum" to listOf("low", "medium", "high")),
                    ),
                    "required" to listOf("projectName", "summaryText", "impactText", "sourceText", "riskLevel"),
                ),
            ),
            "risks" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "riskType" to mapOf("type" to "string"),
                        "title" to mapOf("type" to "string"),
                        "description" to mapOf("type" to "string"),
                        "severity" to mapOf("type" to "string", "enum" to listOf("LOW", "MEDIUM", "HIGH")),
                    ),
                    "required" to listOf("riskType", "title", "description", "severity"),
                ),
            ),
            "overallConfidence" to mapOf("type" to listOf("number", "null")),
        ),
        "required" to listOf("skills", "experiences", "risks", "overallConfidence"),
    )
}
