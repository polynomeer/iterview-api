package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.time.Duration

@Component
class OpenAiPracticalInterviewTranscriptExtractionClient(
    private val objectMapper: ObjectMapper,
    private val transport: InterviewLlmApiTransport,
    @Value("\${app.interview.transcription.api-key:\${app.interview.llm.api-key:}}")
    private val apiKey: String,
    @Value("\${app.interview.transcription.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.interview.transcription.model:gpt-4o-mini-transcribe}")
    private val transcriptionModel: String,
    @Value("\${app.interview.transcription.labeling-model:gpt-5-mini}")
    private val labelingModel: String,
    @Value("\${app.interview.transcription.prompt-version:practical-interview-transcription-v1}")
    private val promptVersion: String,
    @Value("\${app.interview.transcription.timeout-seconds:60}")
    private val timeoutSeconds: Long,
) : PracticalInterviewTranscriptExtractionClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun extract(input: PracticalInterviewTranscriptExtractionInput): ExtractedPracticalInterviewTranscript {
        require(isEnabled()) { "OpenAI practical interview transcription is not configured" }
        val rawTranscript = transcribeAudio(input)
        val speakerLabeledTranscript = labelSpeakers(rawTranscript)
        return ExtractedPracticalInterviewTranscript(
            transcriptText = speakerLabeledTranscript,
            llmModel = labelingModel,
        )
    }

    private fun transcribeAudio(input: PracticalInterviewTranscriptExtractionInput): String {
        val multipart = buildMap<String, InterviewLlmMultipartPart> {
            put(
                "file",
                InterviewLlmMultipartPart.FilePart(
                    fileName = input.fileName,
                    contentType = input.contentType ?: "application/octet-stream",
                    bytes = Files.readAllBytes(input.audioFilePath),
                ),
            )
            put("model", InterviewLlmMultipartPart.TextPart(transcriptionModel))
            put("response_format", InterviewLlmMultipartPart.TextPart("json"))
            put("temperature", InterviewLlmMultipartPart.TextPart("0"))
            put(
                "prompt",
                InterviewLlmMultipartPart.TextPart(
                    "Transcribe this software engineering interview as faithfully as possible. Keep the original language and wording.",
                ),
            )
        }
        val response = transport.postMultipart(
            url = "${baseUrl.trimEnd('/')}/audio/transcriptions",
            apiKey = apiKey,
            parts = multipart,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        val root = objectMapper.readTree(response)
        val text = root.path("text").asText("").trim()
        if (text.isBlank()) {
            throw IllegalStateException("OpenAI practical interview transcription did not include text")
        }
        return text
    }

    private fun labelSpeakers(rawTranscript: String): String {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to labelingModel,
                "input" to listOf(
                    mapOf(
                        "role" to "system",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_text",
                                "text" to systemPrompt(),
                            ),
                        ),
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_text",
                                "text" to userPrompt(rawTranscript),
                            ),
                        ),
                    ),
                ),
                "text" to mapOf(
                    "format" to mapOf(
                        "type" to "json_schema",
                        "name" to "speaker_labeled_interview_transcript",
                        "strict" to true,
                        "schema" to responseSchema(),
                    ),
                ),
            ),
        )
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        val root = objectMapper.readTree(response)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI practical interview speaker labeling did not include output_text")
        val payload = objectMapper.readTree(outputText)
        val transcriptLines = payload.path("lines")
            .mapNotNull { node ->
                val speaker = node.path("speaker").asText("").trim()
                val text = node.path("text").asText("").trim()
                if (speaker.isBlank() || text.isBlank()) null else "$speaker: $text"
            }
        if (transcriptLines.isEmpty()) {
            throw IllegalStateException("OpenAI practical interview speaker labeling returned zero lines")
        }
        return transcriptLines.joinToString("\n")
    }

    private fun systemPrompt(): String = """
        You are converting a raw interview transcript into speaker-labeled lines for downstream parsing.
        Rules:
        - Output one line item per utterance.
        - speaker must be exactly interviewer or candidate.
        - Preserve the original language and wording.
        - Do not summarize or translate.
        - Split the conversation into the most likely alternating turns.
        - If uncertain, prefer interviewer for question-like prompts and candidate for explanatory responses.
        - Return only schema-compliant JSON.
    """.trimIndent()

    private fun userPrompt(rawTranscript: String): String = buildString {
        appendLine("Prompt version: $promptVersion")
        appendLine()
        appendLine("Raw transcript:")
        appendLine(rawTranscript)
    }

    private fun responseSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "lines" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to mapOf(
                        "speaker" to mapOf("type" to "string", "enum" to listOf("interviewer", "candidate")),
                        "text" to mapOf("type" to "string"),
                    ),
                    "required" to listOf("speaker", "text"),
                ),
            ),
        ),
        "required" to listOf("lines"),
    )
}
