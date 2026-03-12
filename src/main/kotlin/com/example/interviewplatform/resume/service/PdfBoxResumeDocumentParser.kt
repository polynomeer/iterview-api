package com.example.interviewplatform.resume.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class PdfBoxResumeDocumentParser : ResumeDocumentParser {
    override fun parse(filePath: Path): ParsedResumeDocument {
        Loader.loadPDF(filePath.toFile()).use { document ->
            val rawText = PDFTextStripper().getText(document)
                .replace("\u0000", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            return ParsedResumeDocument(
                rawText = rawText,
                summaryText = rawText
                    .split(Regex("(?<=[.!?])\\s+"))
                    .map(String::trim)
                    .firstOrNull { it.length >= 20 }
                    ?.take(280),
            )
        }
    }
}
