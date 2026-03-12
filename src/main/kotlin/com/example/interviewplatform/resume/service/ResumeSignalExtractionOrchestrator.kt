package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Service
@Primary
class ResumeSignalExtractionOrchestrator(
    private val fallbackExtractor: PlaceholderResumeSignalExtractionService,
    private val structuredExtractionClient: ResumeStructuredExtractionClient,
) : ResumeSignalExtractionService {
    override fun extract(version: ResumeVersionEntity): ExtractedResumeSignals {
        if (version.rawText.isNullOrBlank() || !structuredExtractionClient.isEnabled()) {
            return fallbackExtractor.extract(version)
        }
        return try {
            structuredExtractionClient.extract(version)
        } catch (ex: Exception) {
            log.warn("resume_llm_extraction_failed versionId={} message={}", version.id, ex.message, ex)
            fallbackExtractor.extract(version)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ResumeSignalExtractionOrchestrator::class.java)
    }
}
