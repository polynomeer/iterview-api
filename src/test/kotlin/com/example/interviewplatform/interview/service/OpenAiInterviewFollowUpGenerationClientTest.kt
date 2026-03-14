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
              "output_text": "{\"promptText\":\"How did you verify rollback readiness?\",\"bodyText\":\"Focus on the signals and fallback threshold.\",\"tags\":[\"payments\",\"rollback\"],\"focusSkillNames\":[\"Risk Management\",\"Observability\"],\"resumeContextSummary\":\"Resume references a payments migration and latency target.\",\"resumeEvidence\":[{\"type\":\"resume_sentence\",\"section\":\"project\",\"label\":\"Payments migration\",\"snippet\":\"Led phased rollout of the payments migration with rollback safeguards.\",\"sourceRecordType\":\"resume_project_snapshot\",\"sourceRecordId\":12,\"confidence\":0.93,\"startOffset\":null,\"endOffset\":null}],\"generationRationale\":\"The answer skipped rollback criteria and monitoring specifics.\"}"
            }
        """.trimIndent()
        val transport = FakeTransport(response)
        val client = OpenAiInterviewFollowUpGenerationClient(
            objectMapper = objectMapper,
            transport = transport,
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5-mini",
            promptVersion = "interview-follow-up-v1",
            timeoutSeconds = 30,
        )

        val result = client.generate(
            InterviewFollowUpGenerationInput(
                outputLanguage = "en",
                answerQualitySignal = "weak",
                preferredFollowUpStyle = "evidence_challenge",
                parentPromptText = "Tell me about a payments migration",
                parentBodyText = "Explain the rollout.",
                answerText = "We migrated traffic gradually.",
                resumeSummaryText = "Backend engineer with payments migration work.",
                resumeSkillNames = listOf("Kotlin", "Spring Boot"),
                resumeProjectSummaries = listOf("Payments migration - backend lead"),
                resumeRiskSummaries = listOf("Latency claim (HIGH): need deeper defense"),
                resumeEvidenceCandidates = listOf(
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = "Payments migration",
                        snippet = "Led phased rollout of the payments migration with rollback safeguards.",
                        facet = "tradeoff",
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = 12,
                    ),
                ),
                parentResumeEvidenceCandidates = listOf(
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = "Payments migration",
                        snippet = "Led phased rollout of the payments migration with rollback safeguards.",
                        facet = "tradeoff",
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = 12,
                    ),
                ),
                preferredResumeEvidenceCandidates = listOf(
                    InterviewResumeEvidenceCandidate(
                        section = "project",
                        label = "Payments migration",
                        snippet = "Cut p95 latency by 28 percent after moving rollback checks earlier in the flow.",
                        facet = "metric",
                        sourceRecordType = "resume_project_snapshot",
                        sourceRecordId = 12,
                    ),
                ),
                usedFacetsForPreferredRecord = listOf("problem", "tradeoff"),
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
        assertEquals(1, result.resumeEvidence.size)
        assertEquals("project", result.resumeEvidence[0].section)
        assertEquals("Payments migration", result.resumeEvidence[0].label)
        assertEquals("resume_project_snapshot", result.resumeEvidence[0].sourceRecordType)
        assertEquals(12L, result.resumeEvidence[0].sourceRecordId)
        assertEquals("The answer skipped rollback criteria and monitoring specifics.", result.generationRationale)
        assertEquals("gpt-5-mini", result.llmModel)
        assertEquals("interview-follow-up-v1", result.llmPromptVersion)
        assertEquals("en", result.contentLocale)
        assertTrue(transport.capturedBody.orEmpty().contains("facet=tradeoff"))
        assertTrue(transport.capturedBody.orEmpty().contains("Answer quality signal: weak"))
        assertTrue(transport.capturedBody.orEmpty().contains("Preferred follow-up style: evidence_challenge"))
        assertTrue(transport.capturedBody.orEmpty().contains("Preferred follow-up evidence candidates"))
        assertTrue(transport.capturedBody.orEmpty().contains("Already covered facets for this record: problem, tradeoff"))
        assertTrue(transport.capturedBody.orEmpty().contains("tradeoff=alternatives and accepted downside"))
        assertTrue(transport.capturedBody.orEmpty().contains("weak=evidence challenge or STAR deepening"))
    }

    private class FakeTransport(
        private val response: String,
    ) : InterviewLlmApiTransport {
        var capturedBody: String? = null

        override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
            capturedBody = body
            return response
        }
    }
}
