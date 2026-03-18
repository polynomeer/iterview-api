package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintLayoutItemDto
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
        val layoutItems = buildLayoutItems(blocks)
        return ResumeEditorPrintPreviewDto(
            resumeVersionId = resumeVersionId,
            workspaceId = workspaceId,
            title = title,
            pageEstimate = pageEstimate,
            plainText = plainText,
            sections = sections,
            pages = pages,
            layoutItems = layoutItems,
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

    private fun buildLayoutItems(blocks: List<ResumeEditorBlockDto>): List<ResumeEditorPrintLayoutItemDto> {
        if (blocks.isEmpty()) {
            return emptyList()
        }
        val items = mutableListOf<ResumeEditorPrintLayoutItemDto>()
        val maxLinesPerPage = 24
        var currentPage = 1
        var currentYOffset = 0
        blocks.forEach { block ->
            val estimatedLineSpan = estimateLineSpan(block)
            if (currentYOffset > 0 && currentYOffset + estimatedLineSpan > maxLinesPerPage) {
                currentPage += 1
                currentYOffset = 0
            }
            items += ResumeEditorPrintLayoutItemDto(
                pageNumber = currentPage,
                sectionKey = block.blockId,
                blockId = block.blockId,
                blockType = block.blockType,
                yOffsetLines = currentYOffset,
                estimatedLineSpan = estimatedLineSpan,
            )
            currentYOffset += estimatedLineSpan
        }
        return items
    }

    private fun estimateLineSpan(block: ResumeEditorBlockDto): Int {
        val titleLines = if (block.title.isNullOrBlank()) 0 else 1
        val textLines = block.text?.let { estimateWrappedLineCount(it) } ?: 0
        val listLines = block.lines.sumOf { estimateWrappedLineCount(it).coerceAtLeast(1) }
        return (titleLines + textLines + listLines).coerceAtLeast(1)
    }

    private fun estimateWrappedLineCount(text: String): Int =
        ceil(text.length.coerceAtLeast(1) / 72.0).toInt().coerceAtLeast(1)
}
