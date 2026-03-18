package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewPageDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewSectionDto
import org.springframework.stereotype.Service
import kotlin.math.ceil

@Service
class ResumeEditorPrintPreviewService {
    fun build(
        resumeVersionId: Long,
        workspaceId: Long,
        title: String,
        blocks: List<ResumeEditorBlockDto>,
    ): ResumeEditorPrintPreviewDto {
        val sections = blocks.map { block ->
            ResumeEditorPrintPreviewSectionDto(
                sectionKey = block.blockId,
                title = block.title ?: block.blockType.replace('_', ' '),
                lines = listOfNotNull(block.text?.takeIf { it.isNotBlank() }).plus(block.lines),
            )
        }
        val plainText = sections.joinToString("\n\n") { section ->
            listOf(section.title).plus(section.lines).joinToString("\n")
        }.trim()
        val pageEstimate = ceil((plainText.length.coerceAtLeast(1)) / 1800.0).toInt().coerceAtLeast(1)
        val pages = buildPages(sections)
        return ResumeEditorPrintPreviewDto(
            resumeVersionId = resumeVersionId,
            workspaceId = workspaceId,
            title = title,
            pageEstimate = pageEstimate,
            plainText = plainText,
            sections = sections,
            pages = pages,
        )
    }

    private fun buildPages(sections: List<ResumeEditorPrintPreviewSectionDto>): List<ResumeEditorPrintPreviewPageDto> {
        if (sections.isEmpty()) {
            return listOf(
                ResumeEditorPrintPreviewPageDto(
                    pageNumber = 1,
                    sectionKeys = emptyList(),
                    lineCount = 0,
                ),
            )
        }
        val pages = mutableListOf<ResumeEditorPrintPreviewPageDto>()
        val maxLinesPerPage = 24
        var pageNumber = 1
        var lineCount = 0
        var sectionKeys = mutableListOf<String>()
        sections.forEach { section ->
            val sectionLineCount = 1 + section.lines.size.coerceAtLeast(1)
            if (lineCount > 0 && lineCount + sectionLineCount > maxLinesPerPage) {
                pages += ResumeEditorPrintPreviewPageDto(
                    pageNumber = pageNumber++,
                    sectionKeys = sectionKeys.toList(),
                    lineCount = lineCount,
                )
                lineCount = 0
                sectionKeys = mutableListOf()
            }
            sectionKeys += section.sectionKey
            lineCount += sectionLineCount
        }
        pages += ResumeEditorPrintPreviewPageDto(
            pageNumber = pageNumber,
            sectionKeys = sectionKeys.toList(),
            lineCount = lineCount,
        )
        return pages
    }
}
