package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class OpenAiPracticalInterviewStructuringEnrichmentClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `is disabled when api key is blank`() {
        val client = OpenAiPracticalInterviewStructuringEnrichmentClient(
            objectMapper = objectMapper,
            transport = FakeTransport("{}"),
            apiKey = "",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "practical-interview-structuring-v1",
            timeoutSeconds = 30,
        )

        assertFalse(client.isEnabled())
    }

    @Test
    fun `enrich parses question and interviewer profile overrides`() {
        val response = """
            {
              "model": "gpt-5-mini",
              "output_text": "{\"overallSummary\":\"This interview focused on caching trade-offs and production metrics.\",\"questions\":[{\"orderIndex\":1,\"questionType\":\"technical_depth\",\"topicTags\":[\"caching\",\"performance\"],\"intentTags\":[\"technical_validation\"],\"parentOrderIndex\":null,\"answerSummary\":\"The candidate explained using Redis to lower DB load and cited a 30 percent p95 improvement.\",\"weaknessTags\":[\"missing_tradeoff\"],\"strengthTags\":[\"quantified\",\"detailed\"],\"confidenceMarkers\":[\"quantified\",\"production_validation\"],\"analysis\":{\"specificity\":\"high\",\"containsNumbers\":true}},{\"orderIndex\":2,\"questionType\":\"technical_depth\",\"topicTags\":[\"caching\",\"performance\"],\"intentTags\":[\"clarification_probe\"],\"parentOrderIndex\":1,\"answerSummary\":\"The candidate described TTL plus explicit invalidation and bypass for strict consistency paths.\",\"weaknessTags\":[],\"strengthTags\":[\"tradeoff_aware\"],\"confidenceMarkers\":[\"concrete_scope\"],\"analysis\":{\"specificity\":\"high\",\"containsTradeoff\":true}}],\"interviewerProfile\":{\"styleTags\":[\"depth_probe\",\"metric_probe\"],\"toneProfile\":\"probing\",\"pressureLevel\":\"high\",\"depthPreference\":\"deep\",\"followUpPatterns\":[\"clarification_probe\",\"metric_probe\"],\"favoriteTopics\":[\"caching\",\"performance\"],\"openingPattern\":\"technical_depth\",\"closingPattern\":\"technical_depth\"}}"
            }
        """.trimIndent()
        val transport = FakeTransport(response)
        val client = OpenAiPracticalInterviewStructuringEnrichmentClient(
            objectMapper = objectMapper,
            transport = transport,
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "practical-interview-structuring-v1",
            timeoutSeconds = 30,
        )

        val result = client.enrich(
            PracticalInterviewStructuringEnrichmentInput(
                outputLanguage = "en",
                companyName = "Example Corp",
                roleName = "Backend Engineer",
                interviewType = "onsite",
                transcriptText = "interviewer: Why Redis? candidate: To reduce load.",
                deterministicSummary = "Imported 2 interview questions across caching, performance.",
                questions = listOf(
                    PracticalInterviewStructuringQuestionInput(
                        orderIndex = 1,
                        text = "Why did you use Redis?",
                        questionType = "technical_depth",
                        topicTags = listOf("caching"),
                        intentTags = listOf("technical_validation"),
                        parentOrderIndex = null,
                        answerText = "To reduce load.",
                        answerSummary = "To reduce load.",
                        weaknessTags = listOf("missing_metrics"),
                        strengthTags = emptyList(),
                    ),
                ),
            ),
        )

        assertEquals("This interview focused on caching trade-offs and production metrics.", result.overallSummary)
        assertEquals(2, result.questions.size)
        assertEquals(1, result.questions[1].parentOrderIndex)
        assertEquals("technical_depth", result.questions[0].questionType)
        assertEquals(listOf("clarification_probe"), result.questions[1].intentTags)
        assertEquals(listOf("quantified", "production_validation"), result.questions[0].confidenceMarkers)
        assertEquals("probing", result.interviewerProfile?.toneProfile)
        assertEquals(listOf("depth_probe", "metric_probe"), result.interviewerProfile?.styleTags)
        assertTrue(transport.capturedBody.orEmpty().contains("Deterministic structured questions"))
        assertTrue(transport.capturedBody.orEmpty().contains("Refine the structure conservatively"))
    }

    private class FakeTransport(
        private val response: String,
    ) : InterviewLlmApiTransport {
        var capturedBody: String? = null

        override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
            capturedBody = body
            return response
        }

        override fun postMultipart(
            url: String,
            apiKey: String,
            parts: Map<String, InterviewLlmMultipartPart>,
            timeout: Duration,
        ): String = throw UnsupportedOperationException("multipart transport not used in this test")
    }
}
