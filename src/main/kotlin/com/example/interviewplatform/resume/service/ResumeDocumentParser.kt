package com.example.interviewplatform.resume.service

import java.nio.file.Path

interface ResumeDocumentParser {
    fun parse(filePath: Path): ParsedResumeDocument
}

data class ParsedResumeDocument(
    val rawText: String,
    val summaryText: String?,
)
