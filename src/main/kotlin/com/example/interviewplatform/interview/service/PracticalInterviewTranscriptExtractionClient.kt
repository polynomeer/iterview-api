package com.example.interviewplatform.interview.service

import java.nio.file.Path

interface PracticalInterviewTranscriptExtractionClient {
    fun isEnabled(): Boolean

    fun extract(input: PracticalInterviewTranscriptExtractionInput): ExtractedPracticalInterviewTranscript
}

data class PracticalInterviewTranscriptExtractionInput(
    val audioFilePath: Path,
    val fileName: String,
    val contentType: String?,
    val languageHint: String? = null,
)

data class ExtractedPracticalInterviewTranscript(
    val transcriptText: String,
    val llmModel: String?,
)
