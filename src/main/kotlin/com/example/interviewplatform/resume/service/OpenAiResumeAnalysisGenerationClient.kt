package com.example.interviewplatform.resume.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiResumeAnalysisGenerationClient(
    private val objectMapper: ObjectMapper,
    private val transport: ResumeLlmApiTransport,
    @Value("\${app.resume.analysis.api-key:\${app.resume.llm.api-key:\${app.interview.llm.api-key:}}}")
    private val apiKey: String,
    @Value("\${app.resume.analysis.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.resume.analysis.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.resume.analysis.prompt-version:resume-analysis-v1}")
    private val promptVersion: String,
    @Value("\${app.resume.analysis.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : ResumeAnalysisGenerationClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun generate(input: ResumeAnalysisGenerationInput): ResumeAnalysisGenerationResult {
        require(isEnabled()) { "OpenAI resume analysis generation is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response, input)
    }

    private fun buildRequest(input: ResumeAnalysisGenerationInput): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf(
                "role" to "system",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to """
                            You produce resume-tailoring output for a job-specific resume analysis.
                            Strengthen wording without inventing unsupported experience.
                            Keep advice ATS-friendly, concrete, and defensible in interviews.
                            Return only schema-compliant JSON.
                        """.trimIndent(),
                    ),
                ),
            ),
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to objectMapper.writeValueAsString(input),
                    ),
                ),
            ),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "resume_analysis_generation",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String, input: ResumeAnalysisGenerationInput): ResumeAnalysisGenerationResult {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI resume analysis response did not include output_text")
        val generated = objectMapper.readTree(outputText)
        val sections = generated.path("tailoredDocument").path("sections").map { section ->
            TailoredResumeSection(
                sectionKey = section.path("sectionKey").asText(),
                title = section.path("title").asText(),
                lines = section.path("lines").map(JsonNode::asText),
            )
        }
        val sectionOrder = generated.path("tailoredDocument").path("sectionOrder").map(JsonNode::asText)
            .ifEmpty { input.preferredSectionOrder }
        val notes = generated.path("analysisNotes").map(JsonNode::asText)
        return ResumeAnalysisGenerationResult(
            matchSummary = generated.path("matchSummary").asText(),
            suggestedHeadline = generated.path("suggestedHeadline").asText(null),
            suggestedSummary = generated.path("suggestedSummary").asText(null),
            recommendedFormatType = generated.path("recommendedFormatType").asText(input.recommendedFormatType),
            analysisNotes = notes,
            diffSummary = generated.path("diffSummary").asText(null),
            suggestions = generated.path("suggestions").map { suggestion ->
                ResumeAnalysisSuggestionSeed(
                    sectionKey = suggestion.path("sectionKey").asText(),
                    originalText = suggestion.path("originalText").asText(null),
                    suggestedText = suggestion.path("suggestedText").asText(),
                    reason = suggestion.path("reason").asText(),
                    suggestionType = suggestion.path("suggestionType").asText(),
                )
            },
            tailoredDocument = TailoredResumeDocument(
                title = generated.path("tailoredDocument").path("title").asText(),
                targetCompany = generated.path("tailoredDocument").path("targetCompany").asText(input.companyName),
                targetRole = generated.path("tailoredDocument").path("targetRole").asText(input.roleName),
                formatType = generated.path("tailoredDocument").path("formatType").asText(input.recommendedFormatType),
                sectionOrder = sectionOrder,
                summary = generated.path("tailoredDocument").path("summary").asText(null),
                diffSummary = generated.path("tailoredDocument").path("diffSummary").asText(null),
                analysisNotes = notes,
                sections = sections,
                plainText = generated.path("tailoredDocument").path("plainText").asText(),
            ),
            generationSource = "openai",
            llmModel = root.path("model").asText(model),
        )
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "matchSummary" to mapOf("type" to "string"),
            "suggestedHeadline" to nullableString(),
            "suggestedSummary" to nullableString(),
            "recommendedFormatType" to mapOf("type" to "string"),
            "analysisNotes" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "diffSummary" to nullableString(),
            "suggestions" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "sectionKey" to mapOf("type" to "string"),
                        "originalText" to nullableString(),
                        "suggestedText" to mapOf("type" to "string"),
                        "reason" to mapOf("type" to "string"),
                        "suggestionType" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("sectionKey", "originalText", "suggestedText", "reason", "suggestionType"),
                ),
            ),
            "tailoredDocument" to mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "properties" to mapOf(
                    "title" to mapOf("type" to "string"),
                    "targetCompany" to nullableString(),
                    "targetRole" to nullableString(),
                    "formatType" to mapOf("type" to "string"),
                    "sectionOrder" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "summary" to nullableString(),
                    "diffSummary" to nullableString(),
                    "plainText" to mapOf("type" to "string"),
                    "sections" to mapOf(
                        "type" to "array",
                        "items" to mapOf(
                            "type" to "object",
                            "additionalProperties" to false,
                            "properties" to mapOf(
                                "sectionKey" to mapOf("type" to "string"),
                                "title" to mapOf("type" to "string"),
                                "lines" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                            ),
                            "required" to listOf("sectionKey", "title", "lines"),
                        ),
                    ),
                ),
                "required" to listOf("title", "targetCompany", "targetRole", "formatType", "sectionOrder", "summary", "diffSummary", "plainText", "sections"),
            ),
        ),
        "required" to listOf(
            "matchSummary",
            "suggestedHeadline",
            "suggestedSummary",
            "recommendedFormatType",
            "analysisNotes",
            "diffSummary",
            "suggestions",
            "tailoredDocument",
        ),
    )

    private fun nullableString(): Map<String, Any> = mapOf("type" to listOf("string", "null"))
}
