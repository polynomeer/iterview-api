package com.example.interviewplatform.resume.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

@Service
class ResumeAnalysisPdfExportService {
    fun export(document: TailoredResumeDocument): PdfExportResult {
        PDDocument().use { pdf ->
            val regularFont = loadUnicodeFont(pdf) ?: PDType1Font(Standard14Fonts.FontName.HELVETICA)
            val boldFont = loadUnicodeFont(pdf) ?: PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            var page = PDPage(PDRectangle.A4)
            pdf.addPage(page)
            var contentStream = PDPageContentStream(pdf, page)
            var y = 790f

            fun ensureSpace(requiredHeight: Float) {
                if (y - requiredHeight >= 50f) {
                    return
                }
                contentStream.close()
                page = PDPage(PDRectangle.A4)
                pdf.addPage(page)
                contentStream = PDPageContentStream(pdf, page)
                y = 790f
            }

            fun writeLine(text: String, font: PDFont, fontSize: Float, indent: Float = 50f) {
                ensureSpace(fontSize + 6f)
                contentStream.beginText()
                contentStream.setFont(font, fontSize)
                contentStream.newLineAtOffset(indent, y)
                contentStream.showText(normalizeRenderableText(text, font).take(140))
                contentStream.endText()
                y -= fontSize + 6f
            }

            writeLine(document.title, boldFont, 18f)
            document.targetCompany?.let { writeLine(it, regularFont, 10f) }
            document.targetRole?.let { writeLine(it, regularFont, 10f) }
            document.summary?.let {
                y -= 4f
                wrapText(it, 92).forEach { line ->
                    writeLine(line, regularFont, 10f)
                }
            }

            document.sections.forEach { section ->
                y -= 6f
                writeLine(section.title, boldFont, 12f)
                section.lines.forEach { line ->
                    wrapText(line, 92).forEach { wrapped ->
                        writeLine("• $wrapped", regularFont, 10f, 60f)
                    }
                }
            }

            contentStream.close()
            val output = ByteArrayOutputStream()
            pdf.save(output)
            return PdfExportResult(
                content = output.toByteArray(),
                pageCount = pdf.numberOfPages,
            )
        }
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) {
            return listOf(text)
        }
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        text.split(" ").forEach { token ->
            if (current.isNotEmpty() && current.length + token.length + 1 > maxChars) {
                parts += current.toString()
                current = StringBuilder(token)
            } else {
                if (current.isNotEmpty()) {
                    current.append(' ')
                }
                current.append(token)
            }
        }
        if (current.isNotEmpty()) {
            parts += current.toString()
        }
        return parts
    }

    private fun loadUnicodeFont(document: PDDocument): PDFont? =
        fontCandidates.asSequence()
            .filter(Files::exists)
            .mapNotNull { path ->
                runCatching {
                    Files.newInputStream(path).use { PDType0Font.load(document, it, true) }
                }.getOrNull()
            }
            .firstOrNull()

    private fun normalizeRenderableText(text: String, font: PDFont): String =
        if (font is PDType1Font) {
            text.map { if (it.code <= 0xFF) it else ' ' }.joinToString("")
        } else {
            text
        }

    private companion object {
        private val fontCandidates = listOf(
            Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
            Path.of("/System/Library/Fonts/AppleSDGothicNeo.ttc"),
            Path.of("/System/Library/Fonts/Supplemental/AppleGothic.ttf"),
            Path.of("/Library/Fonts/Arial Unicode.ttf"),
            Path.of("/Library/Fonts/NanumGothic.ttf"),
            Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"),
            Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
        )
    }
}

data class PdfExportResult(
    val content: ByteArray,
    val pageCount: Int,
)
