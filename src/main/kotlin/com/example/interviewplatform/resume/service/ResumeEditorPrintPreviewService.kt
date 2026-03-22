package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintLayoutItemDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewPageDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewDto
import com.example.interviewplatform.resume.dto.ResumeEditorPrintPreviewSectionDto
import org.springframework.stereotype.Service

@Service
class ResumeEditorPrintPreviewService(
    private val layoutEstimator: ResumeEditorLayoutEstimator,
) {
    fun build(
        resumeVersionId: Long,
        workspaceId: Long,
        title: String,
        blocks: List<ResumeEditorBlockDto>,
    ): ResumeEditorPrintPreviewDto {
        val sections = layoutEstimator.buildSections(blocks).map { section ->
            ResumeEditorPrintPreviewSectionDto(section.sectionKey, section.title, section.lines)
        }
        val plainText = sections.joinToString("\n\n") { section ->
            listOf(section.title).plus(section.lines).joinToString("\n")
        }.trim()
        val pageEstimate = layoutEstimator.estimatePageCountFromPlainText(plainText)
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
        val maxLinesPerPage = ResumeEditorLayoutEstimator.maxLinesPerPage
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
        val maxLinesPerPage = ResumeEditorLayoutEstimator.maxLinesPerPage
        var currentPage = 1
        var currentYOffset = 0
        blocks.forEach { block ->
            val estimatedLineSpan = layoutEstimator.estimateLineSpan(block)
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

}
