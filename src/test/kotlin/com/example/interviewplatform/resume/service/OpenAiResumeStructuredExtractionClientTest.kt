package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class OpenAiResumeStructuredExtractionClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `is disabled when api key is blank`() {
        val client = OpenAiResumeStructuredExtractionClient(
            objectMapper = objectMapper,
            transport = FakeTransport("{}"),
            apiKey = "",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "resume-extract-v1",
            timeoutSeconds = 30,
        )

        assertFalse(client.isEnabled())
    }

    @Test
    fun `extract parses structured output payload`() {
        val response = """
            {
              "model": "gpt-5-mini",
              "output_text": "{\"skills\":[{\"skillName\":\"Kotlin\",\"sourceText\":\"Built Kotlin APIs\",\"confidenceScore\":0.91}],\"experiences\":[{\"projectName\":\"Payments Platform\",\"summaryText\":\"Led payments API redesign\",\"impactText\":\"Reduced latency by 35%\",\"sourceText\":\"Led payments API redesign and reduced latency by 35%\",\"riskLevel\":\"high\"}],\"risks\":[{\"riskType\":\"impact_claim\",\"title\":\"Latency claim\",\"description\":\"Need details for 35% latency reduction\",\"severity\":\"HIGH\"}],\"overallConfidence\":0.88}"
            }
        """.trimIndent()
        val client = OpenAiResumeStructuredExtractionClient(
            objectMapper = objectMapper,
            transport = FakeTransport(response),
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "resume-extract-v1",
            timeoutSeconds = 30,
        )

        val result = client.extract(sampleVersion())

        assertTrue(client.isEnabled())
        assertEquals("openai", result.sourceType)
        assertEquals("gpt-5-mini", result.llmModel)
        assertEquals("resume-extract-v1", result.llmPromptVersion)
        assertEquals(0.88, result.extractionConfidence)
        assertEquals(1, result.skills.size)
        assertEquals("Kotlin", result.skills.first().skillName)
        assertEquals(1, result.experiences.size)
        assertEquals(1, result.risks.size)
    }

    private fun sampleVersion(): ResumeVersionEntity = ResumeVersionEntity(
        id = 42,
        resumeId = 7,
        versionNo = 3,
        rawText = "Built Kotlin APIs and reduced latency by 35%.",
        summaryText = "Backend engineer resume",
        parsingStatus = "completed",
        uploadedAt = Instant.parse("2026-03-12T00:00:00Z"),
        createdAt = Instant.parse("2026-03-12T00:00:00Z"),
    )

    private class FakeTransport(
        private val response: String,
    ) : ResumeLlmApiTransport {
        override fun postJson(url: String, apiKey: String, body: String, timeout: java.time.Duration): String = response
    }
}

