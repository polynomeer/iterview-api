package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class ResumeEditorLayoutEstimator {
    fun buildSections(blocks: List<ResumeEditorBlockDto>): List<EstimatedSection> =
        blocks.map { block ->
            EstimatedSection(
                sectionKey = block.blockId,
                title = block.title ?: block.blockType.replace('_', ' '),
                lines = listOfNotNull(block.text?.takeIf { it.isNotBlank() }).plus(block.lines),
            )
        }

    fun estimatePageCountFromPlainText(plainText: String): Int =
        ceil((plainText.length.coerceAtLeast(1)) / (charsPerLine * maxLinesPerPage).toDouble()).toInt().coerceAtLeast(1)

    fun estimateLineSpan(block: ResumeEditorBlockDto): Int {
        val titleLines = if (block.title.isNullOrBlank()) 0 else 1
        val textLines = block.text?.let { estimateWrappedLineCount(it) } ?: 0
        val listLines = block.lines.sumOf { estimateWrappedLineCount(it).coerceAtLeast(1) }
        return (titleLines + textLines + listLines).coerceAtLeast(1)
    }

    fun estimateWrappedLineCount(text: String): Int =
        ceil(text.length.coerceAtLeast(1) / charsPerLine.toDouble()).toInt().coerceAtLeast(1)

    fun wrapText(text: String): List<String> {
        if (text.length <= charsPerLine) {
            return listOf(text)
        }
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        text.split(" ").forEach { token ->
            if (current.isNotEmpty() && current.length + token.length + 1 > charsPerLine) {
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

    companion object {
        const val maxLinesPerPage = 24
        const val charsPerLine = 72
    }
}

data class EstimatedSection(
    val sectionKey: String,
    val title: String,
    val lines: List<String>,
)
