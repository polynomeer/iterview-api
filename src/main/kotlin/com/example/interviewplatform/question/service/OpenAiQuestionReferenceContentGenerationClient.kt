package com.example.interviewplatform.question.service

import com.example.interviewplatform.interview.service.InterviewLlmApiTransport
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OpenAiQuestionReferenceContentGenerationClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.question-reference.llm.api-key:\${app.interview.llm.api-key:}}")
    private val apiKey: String,
    @Value("\${app.question-reference.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.question-reference.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.question-reference.llm.prompt-version:question-reference-v1}")
    private val promptVersion: String,
    @Value("\${app.question-reference.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : QuestionReferenceContentGenerationClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun generate(input: QuestionReferenceContentGenerationInput): GeneratedQuestionReferenceContent {
        require(isEnabled()) { "OpenAI question reference generation is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(input))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response, input)
    }

    private fun buildRequest(input: QuestionReferenceContentGenerationInput): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf("role" to "system", "content" to listOf(mapOf("type" to "input_text", "text" to systemPrompt()))),
            mapOf("role" to "user", "content" to listOf(mapOf("type" to "input_text", "text" to userPrompt(input)))),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "question_reference_content",
                "strict" to true,
                "schema" to responseSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String, input: QuestionReferenceContentGenerationInput): GeneratedQuestionReferenceContent {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI question reference generation did not include output_text")
        val payload = objectMapper.readTree(outputText)
        return GeneratedQuestionReferenceContent(
            referenceAnswers = payload.path("referenceAnswers").mapIndexedNotNull { index, node ->
                val title = node.path("title").asText("").trim()
                val answerText = node.path("answerText").asText("").trim()
                if (title.isBlank() || answerText.isBlank()) {
                    null
                } else {
                    GeneratedQuestionReferenceAnswer(
                        title = title,
                        answerText = answerText,
                        answerFormat = node.path("answerFormat").asText("full_answer"),
                        displayOrder = node.path("displayOrder").asInt(index + 1),
                    )
                }
            },
            learningMaterials = payload.path("learningMaterials").mapIndexedNotNull { index, node ->
                val title = node.path("title").asText("").trim()
                if (title.isBlank()) {
                    null
                } else {
                    GeneratedQuestionLearningMaterial(
                        title = title,
                        materialType = node.path("materialType").asText("note"),
                        description = node.path("description").asText(null)?.trim()?.ifBlank { null },
                        contentText = node.path("contentText").asText(null)?.trim()?.ifBlank { null },
                        contentUrl = node.path("contentUrl").asText(null)?.trim()?.ifBlank { null },
                        difficultyLevel = node.path("difficultyLevel").asText(null)?.trim()?.ifBlank { null },
                        estimatedMinutes = node.path("estimatedMinutes").takeIf { it.isInt }?.asInt(),
                        relationshipType = node.path("relationshipType").asText(null)?.trim()?.ifBlank { null },
                        displayOrder = node.path("displayOrder").asInt(index + 1),
                        relevanceScore = node.path("relevanceScore").takeIf { it.isNumber }?.asDouble(),
                    )
                }
            },
            llmModel = root.path("model").asText(model),
            contentLocale = input.outputLanguage,
        )
    }

    private fun systemPrompt(): String = """
        You generate useful question-linked study aids for software interview preparation.
        Return exactly:
        - 2 reference answers
        - 2 learning materials
        The outputs must be specific to the question, technically credible, and practical for interview prep.
        Reference answers should include:
        - one outline-style answer
        - one fuller model answer
        Learning materials should include:
        - one prerequisite or concept refresher
        - one practice or deep-dive aid
        Do not use markdown tables.
        Keep everything in the requested output language.
        Return only valid schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(input: QuestionReferenceContentGenerationInput): String = buildString {
        appendLine("Prompt version: $promptVersion")
        appendLine("Output language: ${input.outputLanguage}")
        appendLine("Question title: ${input.questionTitle}")
        input.questionBody?.takeIf { it.isNotBlank() }?.let { appendLine("Question body: $it") }
        appendLine("Question type: ${input.questionType}")
        appendLine("Difficulty: ${input.difficultyLevel}")
        input.categoryName?.takeIf { it.isNotBlank() }?.let { appendLine("Category: $it") }
        if (input.tags.isNotEmpty()) {
            appendLine("Tags: ${input.tags.joinToString(", ")}")
        }
        appendLine()
        appendLine("Make the assets feel interview-focused and immediately useful for practice.")
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "referenceAnswers" to mapOf("type" to "array", "items" to referenceAnswerSchema()),
            "learningMaterials" to mapOf("type" to "array", "items" to learningMaterialSchema()),
        ),
        "required" to listOf("referenceAnswers", "learningMaterials"),
    )

    private fun referenceAnswerSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "answerText" to mapOf("type" to "string"),
            "answerFormat" to mapOf("type" to "string"),
            "displayOrder" to mapOf("type" to "integer"),
        ),
        "required" to listOf("title", "answerText", "answerFormat", "displayOrder"),
    )

    private fun learningMaterialSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "title" to mapOf("type" to "string"),
            "materialType" to mapOf("type" to "string"),
            "description" to mapOf("type" to listOf("string", "null")),
            "contentText" to mapOf("type" to listOf("string", "null")),
            "contentUrl" to mapOf("type" to listOf("string", "null")),
            "difficultyLevel" to mapOf("type" to listOf("string", "null")),
            "estimatedMinutes" to mapOf("type" to listOf("integer", "null")),
            "relationshipType" to mapOf("type" to listOf("string", "null")),
            "displayOrder" to mapOf("type" to "integer"),
            "relevanceScore" to mapOf("type" to listOf("number", "null")),
        ),
        "required" to listOf(
            "title",
            "materialType",
            "description",
            "contentText",
            "contentUrl",
            "difficultyLevel",
            "estimatedMinutes",
            "relationshipType",
            "displayOrder",
            "relevanceScore",
        ),
    )
}
