package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
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
        return ResumeEditorPrintPreviewDto(
            resumeVersionId = resumeVersionId,
            workspaceId = workspaceId,
            title = title,
            pageEstimate = pageEstimate,
            plainText = plainText,
            sections = sections,
        )
    }
}
