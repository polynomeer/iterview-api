package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentDto
import com.example.interviewplatform.resume.dto.ResumeEditorInlineMarkDto
import org.springframework.stereotype.Service

@Service
class ResumeEditorMarkdownService {
    fun parse(markdownSource: String): ResumeEditorDocumentDto {
        val lines = markdownSource.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<ResumeEditorBlockDto>()
        val paragraphBuffer = mutableListOf<String>()
        var order = 0

        fun flushParagraph() {
            if (paragraphBuffer.isEmpty()) {
                return
            }
            val raw = paragraphBuffer.joinToString("\n").trim()
            if (raw.isNotBlank()) {
                val parsed = parseInline(raw)
                blocks += ResumeEditorBlockDto(
                    blockId = "md-paragraph-$order",
                    blockType = "body",
                    title = null,
                    text = parsed.first,
                    lines = emptyList(),
                    sourceAnchorType = null,
                    sourceAnchorRecordId = null,
                    sourceAnchorKey = null,
                    fieldPath = null,
                    displayOrder = order++,
                    metadata = mapOf("source" to "markdown_import"),
                    inlineMarks = parsed.second,
                )
            }
            paragraphBuffer.clear()
        }

        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            when {
                trimmed.isBlank() -> {
                    flushParagraph()
                    index++
                }
                trimmed.startsWith("# ") -> {
                    flushParagraph()
                    val parsed = parseInline(trimmed.removePrefix("# ").trim())
                    blocks += ResumeEditorBlockDto(
                        blockId = "md-header-$order",
                        blockType = "header",
                        title = parsed.first,
                        text = null,
                        lines = emptyList(),
                        sourceAnchorType = null,
                        sourceAnchorRecordId = null,
                        sourceAnchorKey = null,
                        fieldPath = null,
                        displayOrder = order++,
                        metadata = mapOf("source" to "markdown_import"),
                        inlineMarks = parsed.second,
                    )
                    index++
                }
                trimmed.startsWith("## ") -> {
                    flushParagraph()
                    val parsed = parseInline(trimmed.removePrefix("## ").trim())
                    blocks += ResumeEditorBlockDto(
                        blockId = "md-section-$order",
                        blockType = "section_heading",
                        title = parsed.first,
                        text = null,
                        lines = emptyList(),
                        sourceAnchorType = null,
                        sourceAnchorRecordId = null,
                        sourceAnchorKey = null,
                        fieldPath = null,
                        displayOrder = order++,
                        metadata = mapOf("source" to "markdown_import"),
                        inlineMarks = parsed.second,
                    )
                    index++
                }
                trimmed.startsWith("- ") -> {
                    flushParagraph()
                    val bulletLines = mutableListOf<String>()
                    while (index < lines.size && lines[index].trim().startsWith("- ")) {
                        bulletLines += parseInline(lines[index].trim().removePrefix("- ").trim()).first
                        index++
                    }
                    blocks += ResumeEditorBlockDto(
                        blockId = "md-bullet-$order",
                        blockType = "bullet_item",
                        title = null,
                        text = null,
                        lines = bulletLines,
                        sourceAnchorType = null,
                        sourceAnchorRecordId = null,
                        sourceAnchorKey = null,
                        fieldPath = null,
                        displayOrder = order++,
                        metadata = mapOf("source" to "markdown_import"),
                    )
                }
                trimmed.startsWith("> ") -> {
                    flushParagraph()
                    val parsed = parseInline(trimmed.removePrefix("> ").trim())
                    blocks += ResumeEditorBlockDto(
                        blockId = "md-quote-$order",
                        blockType = "quote",
                        title = null,
                        text = parsed.first,
                        lines = emptyList(),
                        sourceAnchorType = null,
                        sourceAnchorRecordId = null,
                        sourceAnchorKey = null,
                        fieldPath = null,
                        displayOrder = order++,
                        metadata = mapOf("source" to "markdown_import"),
                        inlineMarks = parsed.second,
                    )
                    index++
                }
                else -> {
                    paragraphBuffer += trimmed
                    index++
                }
            }
        }
        flushParagraph()

        return ResumeEditorDocumentDto(
            astVersion = 1,
            markdownSource = markdownSource.trim(),
            blocks = blocks,
            layoutMetadata = mapOf("source" to "markdown_import"),
        )
    }

    fun render(blocks: List<ResumeEditorBlockDto>): String =
        blocks.joinToString("\n\n") { block ->
            buildString {
                when (block.blockType) {
                    "header" -> block.title?.let { append("# ").append(renderInline(it, block.inlineMarks)) }
                    "section_heading" -> block.title?.let { append("## ").append(renderInline(it, block.inlineMarks)) }
                    "bullet_item" -> block.lines.forEach { append("- ").append(it).append('\n') }
                    "quote" -> block.text?.let { append("> ").append(renderInline(it, block.inlineMarks)) }
                    else -> {
                        block.title?.takeIf { it.isNotBlank() }?.let { append("### ").append(it).append('\n') }
                        block.text?.takeIf { it.isNotBlank() }?.let { append(renderInline(it, block.inlineMarks)).append('\n') }
                        block.lines.forEach { append("- ").append(it).append('\n') }
                    }
                }
            }.trim()
        }.trim()

    private fun parseInline(raw: String): Pair<String, List<ResumeEditorInlineMarkDto>> {
        val marks = mutableListOf<ResumeEditorInlineMarkDto>()
        val plain = StringBuilder()
        var index = 0
        while (index < raw.length) {
            when {
                raw.startsWith("**", index) -> {
                    val end = raw.indexOf("**", index + 2)
                    if (end > index + 2) {
                        val text = raw.substring(index + 2, end)
                        val startOffset = plain.length
                        plain.append(text)
                        marks += ResumeEditorInlineMarkDto("bold", startOffset, plain.length, text)
                        index = end + 2
                    } else {
                        plain.append(raw[index++])
                    }
                }
                raw.startsWith("`", index) -> {
                    val end = raw.indexOf('`', index + 1)
                    if (end > index + 1) {
                        val text = raw.substring(index + 1, end)
                        val startOffset = plain.length
                        plain.append(text)
                        marks += ResumeEditorInlineMarkDto("code", startOffset, plain.length, text)
                        index = end + 1
                    } else {
                        plain.append(raw[index++])
                    }
                }
                raw.startsWith("[", index) -> {
                    val mid = raw.indexOf("](", index + 1)
                    val end = raw.indexOf(")", (mid + 2).coerceAtLeast(index + 1))
                    if (mid > index + 1 && end > mid + 2) {
                        val text = raw.substring(index + 1, mid)
                        val href = raw.substring(mid + 2, end)
                        val startOffset = plain.length
                        plain.append(text)
                        marks += ResumeEditorInlineMarkDto("link", startOffset, plain.length, text, href)
                        index = end + 1
                    } else {
                        plain.append(raw[index++])
                    }
                }
                raw.startsWith("==", index) -> {
                    val end = raw.indexOf("==", index + 2)
                    if (end > index + 2) {
                        val text = raw.substring(index + 2, end)
                        val startOffset = plain.length
                        plain.append(text)
                        marks += ResumeEditorInlineMarkDto("highlight", startOffset, plain.length, text)
                        index = end + 2
                    } else {
                        plain.append(raw[index++])
                    }
                }
                raw.startsWith("*", index) -> {
                    val end = raw.indexOf('*', index + 1)
                    if (end > index + 1) {
                        val text = raw.substring(index + 1, end)
                        val startOffset = plain.length
                        plain.append(text)
                        marks += ResumeEditorInlineMarkDto("italic", startOffset, plain.length, text)
                        index = end + 1
                    } else {
                        plain.append(raw[index++])
                    }
                }
                else -> plain.append(raw[index++])
            }
        }
        return plain.toString() to marks
    }

    private fun renderInline(text: String, marks: List<ResumeEditorInlineMarkDto>): String {
        if (marks.isEmpty()) {
            return text
        }
        var rendered = text
        marks.sortedByDescending { it.startOffset }.forEach { mark ->
            val prefix = when (mark.markType) {
                "bold" -> "**"
                "italic" -> "*"
                "code" -> "`"
                "highlight" -> "=="
                "link" -> "["
                else -> ""
            }
            val suffix = when (mark.markType) {
                "bold" -> "**"
                "italic" -> "*"
                "code" -> "`"
                "highlight" -> "=="
                "link" -> "](${mark.href.orEmpty()})"
                else -> ""
            }
            if (mark.startOffset >= 0 && mark.endOffset <= rendered.length && mark.endOffset > mark.startOffset) {
                rendered =
                    rendered.substring(0, mark.startOffset) +
                        prefix + rendered.substring(mark.startOffset, mark.endOffset) + suffix +
                        rendered.substring(mark.endOffset)
            }
        }
        return rendered
    }

}
