package com.example.interviewplatform.answer.service

import com.example.interviewplatform.interview.service.InterviewLlmApiTransport
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiAnswerDeepFeedbackGenerationClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.answer-feedback.llm.api-key:\${app.interview.llm.api-key:}}")
    private val apiKey: String,
    @Value("\${app.answer-feedback.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.answer-feedback.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.answer-feedback.llm.prompt-version:answer-feedback-v1}")
    private val promptVersion: String,
    @Value("\${app.answer-feedback.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : AnswerDeepFeedbackGenerationClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun generate(input: AnswerDeepFeedbackGenerationInput): GeneratedAnswerDeepFeedback {
        require(isEnabled()) { "OpenAI answer feedback generation is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response, input)
    }

    private fun buildRequest(input: AnswerDeepFeedbackGenerationInput): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf("role" to "system", "content" to listOf(mapOf("type" to "input_text", "text" to systemPrompt()))),
            mapOf("role" to "user", "content" to listOf(mapOf("type" to "input_text", "text" to userPrompt(input)))),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "answer_deep_feedback",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String, input: AnswerDeepFeedbackGenerationInput): GeneratedAnswerDeepFeedback {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI answer feedback response did not include output_text")
        val payload = objectMapper.readTree(outputText)
        return GeneratedAnswerDeepFeedback(
            detailedFeedback = payload.path("detailedFeedback").asText("").trim(),
            strengthPoints = payload.path("strengthPoints").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } },
            improvementPoints = payload.path("improvementPoints").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } },
            missedPoints = payload.path("missedPoints").mapNotNull { it.asText(null)?.trim()?.ifBlank { null } },
            modelAnswerText = payload.path("modelAnswerText").asText("").trim(),
            llmModel = root.path("model").asText(model),
            contentLocale = input.outputLanguage,
        )
    }

    private fun systemPrompt(): String = """
        You review software interview answers.
        Produce:
        - detailedFeedback: a richer paragraph-level critique
        - strengthPoints: 2-4 concrete positives
        - improvementPoints: 2-4 concrete improvements
        - missedPoints: 1-4 important ideas or evidence the answer did not cover
        - modelAnswerText: a strong example answer in the same output language
        Keep the feedback interview-oriented and actionable.
        Make the model answer realistic and concise enough to study, not essay-like fluff.
        Return only valid schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: AnswerDeepFeedbackGenerationInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
        appendLine("Output language: ${input.outputLanguage}")
        appendLine("Question title: ${input.questionTitle}")
        input.questionBody?.takeIf { it.isNotBlank() }?.let { appendLine("Question body: $it") }
        appendLine("Answer mode: ${input.answerMode}")
        appendLine("Answer text:")
        appendLine(input.answerText)
        appendLine()
        appendLine("Rule-based score summary:")
        appendLine("- total=${input.totalScore}")
        appendLine("- structure=${input.structureScore}")
        appendLine("- specificity=${input.specificityScore}")
        appendLine("- technicalAccuracy=${input.technicalAccuracyScore}")
        if (input.feedbackTitles.isNotEmpty()) {
            appendLine("Existing feedback hints:")
            input.feedbackTitles.zip(input.feedbackBodies).forEach { (title, body) ->
                appendLine("- $title: $body")
            }
        }
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "detailedFeedback" to mapOf("type" to "string"),
            "strengthPoints" to stringArraySchema(),
            "improvementPoints" to stringArraySchema(),
            "missedPoints" to stringArraySchema(),
            "modelAnswerText" to mapOf("type" to "string"),
        ),
        "required" to listOf("detailedFeedback", "strengthPoints", "improvementPoints", "missedPoints", "modelAnswerText"),
    )

    private fun stringArraySchema(): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to mapOf("type" to "string"),
    )
}
