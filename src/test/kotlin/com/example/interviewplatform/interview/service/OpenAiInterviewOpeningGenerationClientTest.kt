package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class OpenAiInterviewOpeningGenerationClientTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `is disabled when api key is blank`() {
        val client = OpenAiInterviewOpeningGenerationClient(
            objectMapper = objectMapper,
            transport = FakeTransport("{}"),
            apiKey = "",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "interview-opening-v1",
            timeoutSeconds = 30,
        )

        assertFalse(client.isEnabled())
    }

    @Test
    fun `generate includes recovery hint for weak evidence`() {
        val response = """
            {
              "model": "gpt-5-mini",
              "output_text": "{\"promptText\":\"Your earlier answer did not fully defend the rollout metric. Walk me through the proof.\",\"bodyText\":\"Focus on the baseline, instrumentation, and why the metric is trustworthy.\",\"tags\":[\"resume\",\"metric\"],\"focusSkillNames\":[\"Observability\"],\"resumeContextSummary\":\"Resume references a latency reduction claim.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Reduced p95 latency by 28 percent after changing rollback checks.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":12,\"confidence\":0.97,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"The preferred evidence was weak, so the opener asks for concrete proof instead of another summary.\"}"
            }
        """.trimIndent()
        val transport = FakeTransport(response)
        val client = OpenAiInterviewOpeningGenerationClient(
            objectMapper = objectMapper,
            transport = transport,
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "interview-opening-v1",
            timeoutSeconds = 30,
        )

        val result = client.generate(
            InterviewOpeningGenerationInput(
                outputLanguage = "en",
                resumeSummaryText = "Backend engineer who led a payments migration.",
                resumeSkillNames = listOf("Kotlin", "Spring Boot"),
                resumeProjectSummaries = listOf("Payments migration - backend lead"),
                resumeRiskSummaries = listOf("Latency claim (HIGH): needs stronger defense"),
                resumeEvidenceCandidates = listOf(
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = "Payments migration",
                        snippet = "Reduced p95 latency by 28 percent after changing rollback checks.",
                        facet = "metric",
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = 12,
                    ),
                ),
                preferredResumeEvidenceCandidates = listOf(
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = "Payments migration",
                        snippet = "Reduced p95 latency by 28 percent after changing rollback checks.",
                        facet = "metric",
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = 12,
                    ),
                ),
                preferredEvidenceRecoveryStatus = "weak",
                preferredOpeningStyle = "evidence_challenge",
            ),
        )

        assertEquals("Your earlier answer did not fully defend the rollout metric. Walk me through the proof.", result.promptText)
        assertEquals("Focus on the baseline, instrumentation, and why the metric is trustworthy.", result.bodyText)
        assertEquals(listOf("resume", "metric"), result.tags)
        assertEquals(listOf("Observability"), result.focusSkillNames)
        assertEquals("Resume references a latency reduction claim.", result.resumeContextSummary)
        assertEquals(1, result.resumeEvidence.size)
        assertEquals("project", result.resumeEvidence[0].section)
        assertEquals("Payments migration", result.resumeEvidence[0].label)
        assertEquals("resume_project_snapshot", result.resumeEvidence[0].sourceRecordType)
        assertEquals(12L, result.resumeEvidence[0].sourceRecordId)
        assertEquals(
            "The preferred evidence was weak, so the opener asks for concrete proof instead of another summary.",
            result.generationRationale,
        )
        assertEquals("gpt-5-mini", result.llmModel)
        assertEquals("interview-opening-v1", result.llmPromptVersion)
        assertEquals("en", result.contentLocale)
        assertTrue(transport.capturedBody.orEmpty().contains("Preferred evidence recovery status: weak"))
        assertTrue(transport.capturedBody.orEmpty().contains("Preferred opening style: evidence_challenge"))
        assertTrue(transport.capturedBody.orEmpty().contains("If the preferred evidence recovery status is weak, ask for proof"))
        assertTrue(transport.capturedBody.orEmpty().contains("facet=metric"))
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
