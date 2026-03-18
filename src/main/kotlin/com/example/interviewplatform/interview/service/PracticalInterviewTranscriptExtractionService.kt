package com.example.interviewplatform.interview.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class PracticalInterviewTranscriptExtractionService(
    private val client: PracticalInterviewTranscriptExtractionClient,
) {
    fun isConfigured(): Boolean = client.isEnabled()

    fun extractOrNull(audioFilePath: Path, fileName: String, contentType: String?, languageHint: String?): String? {
        if (!client.isEnabled()) {
            logger.info("Practical interview transcription skipped because extractor is not configured fileName={}", fileName)
            return null
        }
        val extracted = client.extract(
            PracticalInterviewTranscriptExtractionInput(
                audioFilePath = audioFilePath,
                fileName = fileName,
                contentType = contentType,
                languageHint = languageHint,
            ),
        )
        return extracted.transcriptText.trim().ifBlank { null }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PracticalInterviewTranscriptExtractionService::class.java)
    }
}
