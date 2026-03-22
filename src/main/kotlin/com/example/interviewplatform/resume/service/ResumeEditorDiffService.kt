package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorChangeSummaryDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentDto
import com.example.interviewplatform.resume.dto.ResumeEditorMergeConflictDto
import com.example.interviewplatform.resume.dto.ResumeEditorNodeDto
import com.example.interviewplatform.resume.dto.ResumeEditorTrackedChangeDto
import org.springframework.stereotype.Service

@Service
class ResumeEditorDiffService {
    fun buildChangeSummary(previous: ResumeEditorDocumentDto, current: ResumeEditorDocumentDto): ResumeEditorChangeSummaryDto {
        val previousById = previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val currentById = current.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val added = currentById.keys - previousById.keys
        val removed = previousById.keys - currentById.keys
        val updated = currentById.keys.intersect(previousById.keys).filter { previousById[it] != currentById[it] }
        val previousMarkCount = previous.blocks.sumOf { it.inlineMarks.size }
        val currentMarkCount = current.blocks.sumOf { it.inlineMarks.size }
        return ResumeEditorChangeSummaryDto(
            addedBlockCount = added.size,
            removedBlockCount = removed.size,
            updatedBlockCount = updated.size,
            inlineMarkDelta = currentMarkCount - previousMarkCount,
            changedBlockIds = (added + removed + updated).sorted(),
        )
    }

    fun buildTrackedChanges(previous: ResumeEditorDocumentDto, current: ResumeEditorDocumentDto): List<ResumeEditorTrackedChangeDto> {
        val previousById = previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val currentById = current.nodes.filter { it.nodeId != ROOT_NODE_ID }.associateBy { it.nodeId }
        val orderedIds = (previous.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId } +
            current.nodes.filter { it.nodeId != ROOT_NODE_ID }.map { it.nodeId }).distinct()
        return orderedIds.mapNotNull { nodeId ->
            val before = previousById[nodeId]
            val after = currentById[nodeId]
            when {
                before == null && after != null -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = "added",
                    beforeBlockType = null,
                    afterBlockType = after.nodeType,
                    beforeText = null,
                    afterText = after.text,
                    fieldPath = after.fieldPath,
                    afterParentNodeId = after.parentNodeId,
                    afterDepth = after.depth,
                    textChanged = true,
                    structureChanged = true,
                    beforeTextLines = emptyList(),
                    afterTextLines = splitTextLines(after.text),
                )
                before != null && after == null -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = "removed",
                    beforeBlockType = before.nodeType,
                    afterBlockType = null,
                    beforeText = before.text,
                    afterText = null,
                    fieldPath = before.fieldPath,
                    beforeParentNodeId = before.parentNodeId,
                    beforeDepth = before.depth,
                    textChanged = true,
                    structureChanged = true,
                    beforeTextLines = splitTextLines(before.text),
                    afterTextLines = emptyList(),
                )
                before != null && after != null && before != after -> ResumeEditorTrackedChangeDto(
                    blockId = nodeId,
                    nodeId = nodeId,
                    changeType = when {
                        before.parentNodeId != after.parentNodeId || before.depth != after.depth -> "moved"
                        before.text != after.text -> "text_updated"
                        else -> "structure_updated"
                    },
                    beforeBlockType = before.nodeType,
                    afterBlockType = after.nodeType,
                    beforeText = before.text,
                    afterText = after.text,
                    fieldPath = after.fieldPath ?: before.fieldPath,
                    beforeParentNodeId = before.parentNodeId,
                    afterParentNodeId = after.parentNodeId,
                    beforeDepth = before.depth,
                    afterDepth = after.depth,
                    textChanged = before.text != after.text,
                    structureChanged = before.nodeType != after.nodeType ||
                        before.parentNodeId != after.parentNodeId ||
                        before.depth != after.depth ||
                        before.collapsed != after.collapsed,
                    moveRelated = before.parentNodeId != after.parentNodeId || before.depth != after.depth,
                    beforeTextLines = splitTextLines(before.text),
                    afterTextLines = splitTextLines(after.text),
                )
                else -> null
            }
        }
    }

    fun buildConflict(
        nodeId: String,
        baseNode: ResumeEditorNodeDto?,
        currentNode: ResumeEditorNodeDto?,
        proposedNode: ResumeEditorNodeDto?,
    ): ResumeEditorMergeConflictDto =
        ResumeEditorMergeConflictDto(
            blockId = nodeId,
            nodeId = nodeId,
            conflictType = if (currentNode == null || proposedNode == null) "deletion_conflict" else "content_conflict",
            baseText = baseNode?.text,
            currentText = currentNode?.text,
            proposedText = proposedNode?.text,
            conflictScopes = detectConflictScopes(baseNode, currentNode, proposedNode),
            baseParentNodeId = baseNode?.parentNodeId,
            currentParentNodeId = currentNode?.parentNodeId,
            proposedParentNodeId = proposedNode?.parentNodeId,
            baseTextLines = splitTextLines(baseNode?.text),
            currentTextLines = splitTextLines(currentNode?.text),
            proposedTextLines = splitTextLines(proposedNode?.text),
        )

    private fun detectConflictScopes(
        baseNode: ResumeEditorNodeDto?,
        currentNode: ResumeEditorNodeDto?,
        proposedNode: ResumeEditorNodeDto?,
    ): List<String> {
        val scopes = mutableListOf<String>()
        if ((currentNode?.text != baseNode?.text) && (proposedNode?.text != baseNode?.text) && currentNode?.text != proposedNode?.text) {
            scopes += "text"
        }
        if ((currentNode?.nodeType != baseNode?.nodeType) || (proposedNode?.nodeType != baseNode?.nodeType) ||
            (currentNode?.collapsed != baseNode?.collapsed) || (proposedNode?.collapsed != baseNode?.collapsed)
        ) {
            scopes += "structure"
        }
        if ((currentNode?.parentNodeId != baseNode?.parentNodeId) || (proposedNode?.parentNodeId != baseNode?.parentNodeId) ||
            (currentNode?.depth != baseNode?.depth) || (proposedNode?.depth != baseNode?.depth)
        ) {
            scopes += "move"
        }
        return scopes.distinct()
    }

    private fun splitTextLines(text: String?): List<String> =
        text?.split('\n')?.map { it.trimEnd() }?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        private const val ROOT_NODE_ID = "root"
    }
}
