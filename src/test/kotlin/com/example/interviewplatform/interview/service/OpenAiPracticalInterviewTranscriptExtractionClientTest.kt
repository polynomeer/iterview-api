package com.example.interviewplatform.interview.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.http.HttpTimeoutException
import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.writeBytes

class OpenAiPracticalInterviewTranscriptExtractionClientTest {
    @Test
    fun `labeling timeout falls back to heuristic speaker labels when fail-open is enabled`() {
        val transport = object : InterviewLlmApiTransport {
            override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
                throw HttpTimeoutException("request timed out")
            }

            override fun postMultipart(
                url: String,
                apiKey: String,
                parts: Map<String, InterviewLlmMultipartPart>,
                timeout: Duration,
            ): String = """{"text":"How did you improve cache performance?\nI added Redis and tuned TTL settings."}"""
        }

        val client = OpenAiPracticalInterviewTranscriptExtractionClient(
            objectMapper = ObjectMapper(),
            transport = transport,
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            transcriptionModel = "gpt-4o-mini-transcribe",
            labelingModel = "gpt-5-mini",
            promptVersion = "practical-interview-transcription-v1",
            timeoutSeconds = 60,
            labelingTimeoutSeconds = 120,
            speakerLabelingFailOpen = true,
            maxFileSizeBytes = 1024 * 1024,
            chunkingEnabled = false,
            ffmpegCommand = "ffmpeg",
            chunkSegmentSeconds = 480,
            chunkAudioBitrate = "48k",
            ffmpegTimeoutSeconds = 30,
        )

        val audioFile = Files.createTempFile("transcription-client-test", ".wav")
        try {
            audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            val extracted = client.extract(
                PracticalInterviewTranscriptExtractionInput(
                    audioFilePath = audioFile,
                    fileName = "sample.wav",
                    contentType = "audio/wav",
                ),
            )
            assertEquals(
                "interviewer: How did you improve cache performance?\ncandidate: I added Redis and tuned TTL settings.",
                extracted.transcriptText,
            )
        } finally {
            Files.deleteIfExists(audioFile)
        }
    }

    @Test
    fun `labeling timeout propagates error when fail-open is disabled`() {
        val transport = object : InterviewLlmApiTransport {
            override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
                throw HttpTimeoutException("request timed out")
            }

            override fun postMultipart(
                url: String,
                apiKey: String,
                parts: Map<String, InterviewLlmMultipartPart>,
                timeout: Duration,
            ): String = """{"text":"hello"}"""
        }

        val client = OpenAiPracticalInterviewTranscriptExtractionClient(
            objectMapper = ObjectMapper(),
            transport = transport,
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            transcriptionModel = "gpt-4o-mini-transcribe",
            labelingModel = "gpt-5-mini",
            promptVersion = "practical-interview-transcription-v1",
            timeoutSeconds = 60,
            labelingTimeoutSeconds = 120,
            speakerLabelingFailOpen = false,
            maxFileSizeBytes = 1024 * 1024,
            chunkingEnabled = false,
            ffmpegCommand = "ffmpeg",
            chunkSegmentSeconds = 480,
            chunkAudioBitrate = "48k",
            ffmpegTimeoutSeconds = 30,
        )

        val audioFile = Files.createTempFile("transcription-client-test", ".wav")
        try {
            audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
            assertThrows(HttpTimeoutException::class.java) {
                client.extract(
                    PracticalInterviewTranscriptExtractionInput(
                        audioFilePath = audioFile,
                        fileName = "sample.wav",
                        contentType = "audio/wav",
                    ),
                )
            }
        } finally {
            Files.deleteIfExists(audioFile)
        }
    }
}
