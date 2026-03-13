package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class OpenAiInterviewFollowUpGenerationClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `is disabled when api key is blank`() {
        val client = OpenAiInterviewFollowUpGenerationClient(
            objectMapper = objectMapper,
            transport = FakeTransport("{}"),
            apiKey = "",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "interview-follow-up-v1",
            timeoutSeconds = 30,
        )

        assertFalse(client.isEnabled())
    }

    @Test
    fun `generate parses structured follow up payload`() {
        val response = """
            {
              "model": "gpt-5-mini",
              "output_text": "{\"promptText\":\"How did you verify rollback readiness?\",\"bodyText\":\"Focus on the signals and fallback threshold.\",\"tags\":[\"payments\",\"rollback\"],\"focusSkillNames\":[\"Risk Management\",\"Observability\"],\"resumeContextSummary\":\"Resume references a payments migration and latency target.\",\"generationRationale\":\"The answer skipped rollback criteria and monitoring specifics.\"}"
            }
        """.trimIndent()
        val client = OpenAiInterviewFollowUpGenerationClient(
            objectMapper = objectMapper,
            transport = FakeTransport(response),
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "interview-follow-up-v1",
            timeoutSeconds = 30,
        )

        val result = client.generate(
            InterviewFollowUpGenerationInput(
                parentPromptText = "Tell me about a payments migration",
                parentBodyText = "Explain the rollout.",
                answerText = "We migrated traffic gradually.",
                resumeSummaryText = "Backend engineer with payments migration work.",
                resumeSkillNames = listOf("Kotlin", "Spring Boot"),
                resumeProjectSummaries = listOf("Payments migration - backend lead"),
                resumeRiskSummaries = listOf("Latency claim (HIGH): need deeper defense"),
                parentTags = listOf("payments"),
                parentFocusSkillNames = listOf("Distributed Systems"),
            ),
        )

        assertTrue(client.isEnabled())
        assertEquals("How did you verify rollback readiness?", result.promptText)
        assertEquals("Focus on the signals and fallback threshold.", result.bodyText)
        assertEquals(listOf("payments", "rollback"), result.tags)
        assertEquals(listOf("Risk Management", "Observability"), result.focusSkillNames)
        assertEquals("Resume references a payments migration and latency target.", result.resumeContextSummary)
        assertEquals("The answer skipped rollback criteria and monitoring specifics.", result.generationRationale)
        assertEquals("gpt-5-mini", result.llmModel)
        assertEquals("interview-follow-up-v1", result.llmPromptVersion)
    }

    private class FakeTransport(
        private val response: String,
    ) : InterviewLlmApiTransport {
        override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String = response
    }
}
