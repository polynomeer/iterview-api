package com.example.interviewplatform.interview.service

import com.example.interviewplatform.common.service.AppLocaleService
import com.example.interviewplatform.interview.entity.InterviewRecordEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

class PracticalInterviewStructuringEnrichmentServiceTest {
    @Test
    fun `enrich merges overrides into parsed transcript`() {
        val appLocaleService = mock(AppLocaleService::class.java)
        `when`(appLocaleService.resolveLanguage()).thenReturn("en")
        val service = PracticalInterviewStructuringEnrichmentService(
            client = FakeClient(
                PracticalInterviewStructuringEnrichment(
                    overallSummary = "AI refined summary",
                    questions = listOf(
                        PracticalInterviewStructuringQuestionEnrichment(
                            orderIndex = 2,
                            questionType = "technical_depth",
                            topicTags = listOf("caching", "performance"),
                            intentTags = listOf("clarification_probe"),
                            parentOrderIndex = 1,
                            answerSummary = "Refined answer summary",
                            weaknessTags = listOf("missing_tradeoff"),
                            strengthTags = listOf("quantified"),
                            confidenceMarkers = listOf("production_validation"),
                            analysis = mapOf("specificity" to "high"),
                        ),
                    ),
                    interviewerProfile = PracticalInterviewInterviewerProfileOverride(
                        styleTags = listOf("depth_probe"),
                        toneProfile = "probing",
                        pressureLevel = "high",
                        depthPreference = "deep",
                        followUpPatterns = listOf("clarification_probe"),
                        favoriteTopics = listOf("caching"),
                        openingPattern = "technical_depth",
                        closingPattern = "technical_depth",
                    ),
                ),
            ),
            appLocaleService = appLocaleService,
        )

        val result = service.enrich(
            record = InterviewRecordEntity(
                id = 1,
                userId = 1,
                companyName = "Example Corp",
                roleName = "Backend Engineer",
                interviewDate = null,
                interviewType = "onsite",
                sourceAudioFileUrl = "/uploads/interview-audio/test.wav",
                sourceAudioFileName = "test.wav",
                sourceAudioDurationMs = null,
                sourceAudioContentType = "audio/wav",
                rawTranscript = null,
                cleanedTranscript = null,
                confirmedTranscript = null,
                transcriptStatus = "confirmed",
                analysisStatus = "completed",
                linkedResumeVersionId = null,
                linkedJobPostingId = null,
                interviewerProfileId = null,
                overallSummary = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
            transcriptText = "interviewer: Why Redis? candidate: To reduce load.",
            parsedTranscript = ParsedInterviewTranscript(
                segments = emptyList(),
                questions = listOf(
                    ParsedQuestion(
                        orderIndex = 1,
                        segmentStartSequence = 1,
                        segmentEndSequence = 1,
                        text = "Why Redis?",
                        normalizedText = "Why Redis?",
                        questionType = "technical_depth",
                        topicTags = listOf("caching"),
                        intentTags = listOf("technical_validation"),
                        derivedFromResumeSection = null,
                        derivedFromResumeRecordType = null,
                        derivedFromResumeRecordId = null,
                        parentOrderIndex = null,
                        answer = ParsedAnswer(
                            segmentStartSequence = 2,
                            segmentEndSequence = 2,
                            text = "To reduce load.",
                            normalizedText = "To reduce load.",
                            summary = "To reduce load.",
                            confidenceMarkers = emptyList(),
                            weaknessTags = listOf("missing_metrics"),
                            strengthTags = emptyList(),
                            analysis = mapOf("specificity" to "low"),
                        ),
                    ),
                    ParsedQuestion(
                        orderIndex = 2,
                        segmentStartSequence = 3,
                        segmentEndSequence = 3,
                        text = "How did you invalidate it?",
                        normalizedText = "How did you invalidate it?",
                        questionType = "general",
                        topicTags = listOf("general"),
                        intentTags = listOf("general_probe"),
                        derivedFromResumeSection = null,
                        derivedFromResumeRecordType = null,
                        derivedFromResumeRecordId = null,
                        parentOrderIndex = null,
                        answer = ParsedAnswer(
                            segmentStartSequence = 4,
                            segmentEndSequence = 4,
                            text = "TTL and explicit deletion.",
                            normalizedText = "TTL and explicit deletion.",
                            summary = "TTL and explicit deletion.",
                            confidenceMarkers = emptyList(),
                            weaknessTags = emptyList(),
                            strengthTags = emptyList(),
                            analysis = mapOf("specificity" to "medium"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("AI refined summary", result.overallSummaryOverride)
        assertEquals(1, result.questions[1].parentOrderIndex)
        assertEquals("technical_depth", result.questions[1].questionType)
        assertEquals("Refined answer summary", result.questions[1].answer?.summary)
        assertEquals(listOf("missing_tradeoff"), result.questions[1].answer?.weaknessTags)
        assertNotNull(result.interviewerProfileOverride)
        assertEquals("probing", result.interviewerProfileOverride?.toneProfile)
    }

    private class FakeClient(
        private val enrichment: PracticalInterviewStructuringEnrichment,
    ) : PracticalInterviewStructuringEnrichmentClient {
        override fun isEnabled(): Boolean = true

        override fun enrich(input: PracticalInterviewStructuringEnrichmentInput): PracticalInterviewStructuringEnrichment = enrichment
    }
}
