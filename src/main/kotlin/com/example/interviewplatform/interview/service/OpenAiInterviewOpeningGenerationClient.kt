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
            resumeEvidence = payload.path("resumeEvidence").mapNotNull(::parseResumeEvidenceItem),
            generationRationale = payload.path("generationRationale").asText("").trim().ifBlank {
                "Generated opening question from the selected resume context."
            },
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
        )
    }

    private fun systemPrompt(): String = """
        You are generating the opening interview question for a software engineer mock interview.
        Ground the question in the candidate's resume evidence, not generic keyword matching.
        Ask one strong interviewer-style question that opens the mock interview.
        Do not ask multiple questions.
        Prefer a question that feels like a real interviewer read the resume line by line and wants to probe one specific claim, decision, trade-off, incident, or result.
        Choose one of these realistic opener styles:
        1. Resume evidence explanation: ask the candidate to explain a specific sentence, project, responsibility, or claimed outcome from the resume.
        2. STAR / problem-solving: ask for the situation, task, action, and result behind a concrete experience or achievement.
        3. Knowledge validation: if the resume mentions an important technology, architecture, or domain, ask a practical knowledge question that validates real understanding instead of buzzwords.
        4. Scenario / design: propose a realistic constraint or failure condition based on the resume context and ask how they would design or respond.
        Avoid generic prompts like "Tell me about X technology" unless they are anchored to a real resume claim.
        Avoid lists of sub-questions.
        Avoid mentioning that the question was generated from a resume.
        promptText should be concise and interview-ready.
        bodyText should guide the expected depth, such as asking for STAR structure, decision criteria, trade-offs, failure handling, metrics, or design constraints.
        tags should be short topic labels.
        focusSkillNames should align to the actual technical or behavioral skills being assessed.
        resumeEvidence should contain one or two short, specific resume snippets that justify why this question was asked.
        Prefer evidence tied to a concrete project, experience, award, certification, or education record.
        Do not paste long paragraphs from the resume.
        generationRationale should briefly explain which resume evidence triggered this question style.
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
        if (input.resumeEvidenceCandidates.isNotEmpty()) {
            appendLine()
            appendLine("Resume evidence candidates:")
            input.resumeEvidenceCandidates.forEach { candidate ->
                appendLine("- [${candidate.sourceRecordType}:${candidate.sourceRecordId}] section=${candidate.section} label=${candidate.label ?: "-"} snippet=${candidate.snippet}")
            }
        }
        if (input.preferredResumeEvidenceCandidates.isNotEmpty()) {
            appendLine()
            appendLine("Preferred evidence to prioritize for this opener:")
            input.preferredResumeEvidenceCandidates.forEach { candidate ->
                appendLine("- [${candidate.sourceRecordType}:${candidate.sourceRecordId}] section=${candidate.section} label=${candidate.label ?: "-"} snippet=${candidate.snippet}")
            }
            appendLine("When possible, anchor the opener to one of the preferred evidence items above.")
        }
        appendLine()
        appendLine("Question design goal:")
        appendLine("Generate a realistic opener that is specific enough to be answerable from the resume, but deep enough to reveal explanation quality, problem-solving structure, technical understanding, or design judgment.")
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

    private fun parseResumeEvidenceItem(node: com.fasterxml.jackson.databind.JsonNode): GeneratedInterviewResumeEvidence? {
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
}
