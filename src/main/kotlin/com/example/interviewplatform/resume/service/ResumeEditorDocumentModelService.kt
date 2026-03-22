package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeEditorBlockDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentDto
import com.example.interviewplatform.resume.dto.ResumeEditorDocumentOperationDto
import com.example.interviewplatform.resume.dto.ResumeEditorInlineMarkDto
import com.example.interviewplatform.resume.dto.ResumeEditorNodeDto
import com.example.interviewplatform.resume.dto.ResumeEditorTableOfContentsItemDto
import com.example.interviewplatform.resume.dto.ResumeEditorTextRunDto
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ResumeEditorDocumentModelService(
    private val resumeEditorMarkdownService: ResumeEditorMarkdownService,
    private val selectionAnchorService: ResumeEditorSelectionAnchorService,
) {
    fun materializeDocument(
        currentDocument: ResumeEditorDocumentDto,
        blocks: List<ResumeEditorBlockDto>?,
        rootNodeId: String?,
        nodes: List<ResumeEditorNodeDto>?,
        tableOfContents: List<ResumeEditorTableOfContentsItemDto>?,
        markdownSource: String?,
        layoutMetadata: Map<String, String>,
    ): ResumeEditorDocumentDto {
        val effectiveBlocks = when {
            !blocks.isNullOrEmpty() -> blocks
            !nodes.isNullOrEmpty() -> deriveBlocksFromNodes(rootNodeId ?: currentDocument.rootNodeId ?: ROOT_NODE_ID, nodes)
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Either blocks or nodes must be provided")
        }
        requireValidBlocks(effectiveBlocks)
        return normalizeDocument(
            ResumeEditorDocumentDto(
                astVersion = currentDocument.astVersion + 1,
                markdownSource = markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(effectiveBlocks),
                blocks = effectiveBlocks,
                rootNodeId = rootNodeId ?: currentDocument.rootNodeId,
                nodes = nodes.orEmpty(),
                tableOfContents = tableOfContents.orEmpty(),
                layoutMetadata = layoutMetadata,
            ),
        )
    }

    fun normalizeDocument(document: ResumeEditorDocumentDto): ResumeEditorDocumentDto {
        val normalizedBlocks = document.blocks
            .mapIndexed { index, block ->
                block.copy(
                    displayOrder = index,
                    metadata = block.metadata.filterKeys { it.isNotBlank() },
                )
            }
        requireValidBlocks(normalizedBlocks)
        val rootNodeId = document.rootNodeId ?: ROOT_NODE_ID
        val normalizedNodes = when {
            document.nodes.isNotEmpty() -> normalizeNodes(rootNodeId, document.nodes)
            else -> buildNodesFromBlocks(normalizedBlocks, rootNodeId)
        }
        val blocksFromNodes = deriveBlocksFromNodes(rootNodeId, normalizedNodes)
        return ResumeEditorDocumentDto(
            astVersion = document.astVersion.coerceAtLeast(DOCUMENT_AST_VERSION),
            markdownSource = document.markdownSource?.trim()?.takeIf { it.isNotEmpty() } ?: resumeEditorMarkdownService.render(blocksFromNodes),
            blocks = blocksFromNodes,
            rootNodeId = rootNodeId,
            nodes = normalizedNodes,
            tableOfContents = if (document.tableOfContents.isNotEmpty()) {
                document.tableOfContents
                    .filter { it.nodeId.isNotBlank() }
                    .map { toc ->
                        val node = normalizedNodes.firstOrNull { it.nodeId == toc.nodeId }
                        toc.copy(
                            title = node?.text ?: toc.title,
                            depth = node?.depth ?: toc.depth,
                            fieldPath = node?.fieldPath ?: toc.fieldPath,
                        )
                    }
            } else {
                buildTableOfContents(normalizedNodes)
            },
            layoutMetadata = document.layoutMetadata,
        )
    }

    fun applyOperations(
        currentDocument: ResumeEditorDocumentDto,
        operations: List<ResumeEditorDocumentOperationDto>,
    ): ResumeEditorDocumentDto {
        var workingBlocks = currentDocument.blocks.map { it.copy(metadata = it.metadata.toMutableMap()) }.toMutableList()
        operations.forEachIndexed { index, operation ->
            when (operation.operationType.trim().lowercase()) {
                "text_insert" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    if (startOffset > text.length) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "text_insert startOffset exceeds node text length")
                    }
                    workingBlocks[blockIndex] = rewriteBlockContent(block, text.substring(0, startOffset) + operation.text.orEmpty() + text.substring(startOffset))
                }
                "text_replace" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    selectionAnchorService.validateSelection(text.length, startOffset, endOffset)
                    workingBlocks[blockIndex] = rewriteBlockContent(block, text.substring(0, startOffset) + operation.text.orEmpty() + text.substring(endOffset))
                }
                "text_delete" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    selectionAnchorService.validateSelection(text.length, startOffset, endOffset)
                    workingBlocks[blockIndex] = rewriteBlockContent(block, text.removeRange(startOffset, endOffset))
                }
                "block_split" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val beforeText = text.substring(0, startOffset).trimEnd()
                    val afterText = text.substring(startOffset).trimStart()
                    workingBlocks[blockIndex] = rewriteBlockContent(block, beforeText)
                    workingBlocks.add(
                        blockIndex + 1,
                        rewriteBlockContent(
                            block.copy(
                                blockId = generateOperationNodeId(block.blockId, "split", index),
                                metadata = block.metadata + (METADATA_SPLIT_ORIGIN to block.blockId),
                            ),
                            afterText,
                        ),
                    )
                }
                "block_merge" -> {
                    val primaryIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val referenceIndex = operation.referenceNodeId?.let { requireBlockIndex(workingBlocks, it) }
                        ?: (primaryIndex + 1).takeIf { it < workingBlocks.size }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "block_merge requires a following block or referenceNodeId")
                    val firstIndex = minOf(primaryIndex, referenceIndex)
                    val secondIndex = maxOf(primaryIndex, referenceIndex)
                    val mergedText = listOf(blockContent(workingBlocks[firstIndex]), blockContent(workingBlocks[secondIndex]))
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    workingBlocks[firstIndex] = rewriteBlockContent(workingBlocks[firstIndex], mergedText)
                    workingBlocks.removeAt(secondIndex)
                }
                "block_move" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks.removeAt(blockIndex)
                    val targetIndex = operation.referenceNodeId?.let { requireBlockIndex(workingBlocks, it) } ?: workingBlocks.size
                    val updatedBlock = if (!operation.parentNodeId.isNullOrBlank()) {
                        block.copy(metadata = block.metadata + (METADATA_PARENT_NODE_ID to operation.parentNodeId.trim()))
                    } else block
                    workingBlocks.add(targetIndex.coerceIn(0, workingBlocks.size), updatedBlock)
                }
                "block_duplicate" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val source = workingBlocks[blockIndex]
                    workingBlocks.add(
                        blockIndex + 1,
                        source.copy(
                            blockId = generateOperationNodeId(source.blockId, "copy", index),
                            metadata = source.metadata + (METADATA_DUPLICATED_FROM to source.blockId),
                        ),
                    )
                }
                "block_remove" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    workingBlocks.removeAt(blockIndex)
                }
                "block_type_change" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val nodeType = operation.nodeType?.trim()?.takeIf { it.isNotEmpty() }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "block_type_change requires nodeType")
                    workingBlocks[blockIndex] = workingBlocks[blockIndex].copy(blockType = nodeType)
                }
                "indent" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val current = workingBlocks[blockIndex]
                    val previous = workingBlocks.getOrNull(blockIndex - 1)
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "indent requires a previous node")
                    val previousDepth = previous.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1
                    workingBlocks[blockIndex] = current.copy(
                        metadata = current.metadata + mapOf(
                            METADATA_PARENT_NODE_ID to previous.blockId,
                            METADATA_DEPTH to (previousDepth + 1).toString(),
                        ),
                    )
                }
                "outdent" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val current = workingBlocks[blockIndex]
                    val currentDepth = current.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1
                    val parentNodeId = current.metadata[METADATA_PARENT_NODE_ID]
                    val parentParentNodeId = parentNodeId?.let { parentId ->
                        workingBlocks.firstOrNull { it.blockId == parentId }?.metadata?.get(METADATA_PARENT_NODE_ID)
                    }
                    val updatedMetadata = current.metadata.minus(METADATA_PARENT_NODE_ID)
                        .plus(METADATA_DEPTH to (currentDepth - 1).coerceAtLeast(1).toString())
                        .let { metadata ->
                            if (parentParentNodeId.isNullOrBlank()) metadata - METADATA_PARENT_NODE_ID
                            else metadata + (METADATA_PARENT_NODE_ID to parentParentNodeId)
                        }
                    workingBlocks[blockIndex] = current.copy(metadata = updatedMetadata)
                }
                "inline_mark_add" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val text = blockContent(block)
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    val markType = operation.markType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "inline_mark_add requires markType")
                    selectionAnchorService.validateSelection(text.length, startOffset, endOffset)
                    val newMark = ResumeEditorInlineMarkDto(markType, startOffset, endOffset, text.substring(startOffset, endOffset), operation.href?.trim()?.takeIf { it.isNotEmpty() })
                    workingBlocks[blockIndex] = block.copy(
                        inlineMarks = (block.inlineMarks + newMark)
                            .distinctBy { Triple(it.markType, it.startOffset, it.endOffset) to it.href }
                            .sortedWith(compareBy({ it.startOffset }, { it.endOffset }, { it.markType })),
                    )
                }
                "inline_mark_remove" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val startOffset = requireOffset(operation.startOffset, "startOffset")
                    val endOffset = requireOffset(operation.endOffset, "endOffset")
                    val markType = operation.markType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                    workingBlocks[blockIndex] = block.copy(
                        inlineMarks = block.inlineMarks.filterNot { mark ->
                            (markType == null || mark.markType == markType) &&
                                mark.startOffset == startOffset &&
                                mark.endOffset == endOffset
                        },
                    )
                }
                "collapse_toggle" -> {
                    val blockIndex = requireBlockIndex(workingBlocks, operation.nodeId)
                    val block = workingBlocks[blockIndex]
                    val currentCollapsed = block.metadata[METADATA_COLLAPSED] == "true"
                    workingBlocks[blockIndex] = block.copy(metadata = block.metadata + (METADATA_COLLAPSED to (operation.collapsed ?: !currentCollapsed).toString()))
                }
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported editor operationType: ${operation.operationType}")
            }
        }
        return normalizeDocument(
            ResumeEditorDocumentDto(
                astVersion = currentDocument.astVersion + 1,
                markdownSource = resumeEditorMarkdownService.render(workingBlocks),
                blocks = workingBlocks.mapIndexed { index, block -> block.copy(displayOrder = index) },
                rootNodeId = currentDocument.rootNodeId,
                nodes = emptyList(),
                tableOfContents = emptyList(),
                layoutMetadata = currentDocument.layoutMetadata,
            ),
        )
    }

    fun emptyDocument(): ResumeEditorDocumentDto =
        normalizeDocument(ResumeEditorDocumentDto(astVersion = 1, markdownSource = null, blocks = emptyList(), layoutMetadata = emptyMap()))

    private fun requireValidBlocks(blocks: List<ResumeEditorBlockDto>) {
        if (blocks.map { it.blockId }.toSet().size != blocks.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume editor blockId values must be unique")
        }
        if (blocks.any { it.blockId.isBlank() || it.blockType.isBlank() }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume editor blocks require non-blank blockId and blockType")
        }
        blocks.forEach { block ->
            block.inlineMarks.forEach { mark ->
                if (mark.endOffset <= mark.startOffset || mark.endOffset > blockContent(block).length) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Inline mark range is invalid for block ${block.blockId}")
                }
            }
        }
    }

    private fun normalizeNodes(rootNodeId: String, nodes: List<ResumeEditorNodeDto>): List<ResumeEditorNodeDto> {
        val baseNodes = nodes.filter { it.nodeId.isNotBlank() }.associateBy { it.nodeId }.toMutableMap()
        if (rootNodeId !in baseNodes) {
            baseNodes[rootNodeId] = ResumeEditorNodeDto(
                nodeId = rootNodeId, parentNodeId = null, nodeType = NODE_TYPE_ROOT, text = null,
                textRuns = emptyList(), children = emptyList(), collapsed = false, depth = 0,
                sourceAnchorType = null, sourceAnchorRecordId = null, sourceAnchorKey = null, fieldPath = null,
                displayOrder = -1, metadata = emptyMap(),
            )
        }
        val nonRootNodes = baseNodes.values.filter { it.nodeId != rootNodeId }.sortedWith(compareBy<ResumeEditorNodeDto>({ it.depth }, { it.displayOrder }, { it.nodeId }))
        val normalized = mutableListOf<ResumeEditorNodeDto>()
        val childrenByParentId = linkedMapOf<String, MutableList<ResumeEditorNodeDto>>()
        nonRootNodes.forEach { node ->
            val parentId = node.parentNodeId?.takeIf { it in baseNodes && it != node.nodeId } ?: rootNodeId
            childrenByParentId.computeIfAbsent(parentId) { mutableListOf() }.add(node.copy(parentNodeId = parentId))
        }
        normalized += baseNodes.getValue(rootNodeId).copy(
            parentNodeId = null, nodeType = NODE_TYPE_ROOT, text = null, textRuns = emptyList(),
            children = childrenByParentId[rootNodeId].orEmpty().sortedBy { it.displayOrder }.map { it.nodeId },
            collapsed = false, depth = 0, displayOrder = -1, metadata = emptyMap(),
        )
        fun visit(parentId: String, depth: Int) {
            childrenByParentId[parentId].orEmpty().sortedBy { it.displayOrder }.forEachIndexed { index, child ->
                val normalizedChild = child.copy(
                    parentNodeId = parentId,
                    depth = depth,
                    displayOrder = index,
                    children = childrenByParentId[child.nodeId].orEmpty().sortedBy { it.displayOrder }.map { it.nodeId },
                )
                normalized += normalizedChild
                visit(child.nodeId, depth + 1)
            }
        }
        visit(rootNodeId, 1)
        return normalized
    }

    private fun buildNodesFromBlocks(blocks: List<ResumeEditorBlockDto>, rootNodeId: String): List<ResumeEditorNodeDto> {
        val nodes = mutableListOf(
            ResumeEditorNodeDto(rootNodeId, null, NODE_TYPE_ROOT, null, emptyList(), emptyList(), false, 0, null, null, null, null, -1, emptyMap()),
        )
        blocks.forEach { block ->
            val text = when {
                block.blockType in titleOnlyBlockTypes -> block.title
                block.lines.isNotEmpty() -> block.lines.joinToString("\n")
                else -> block.text ?: block.title
            }
            nodes += ResumeEditorNodeDto(
                nodeId = block.blockId,
                parentNodeId = block.metadata[METADATA_PARENT_NODE_ID]?.takeIf { it.isNotBlank() } ?: rootNodeId,
                nodeType = block.blockType,
                text = text,
                textRuns = buildTextRuns(text.orEmpty(), block.inlineMarks),
                children = emptyList(),
                collapsed = block.metadata[METADATA_COLLAPSED] == "true",
                depth = block.metadata[METADATA_DEPTH]?.toIntOrNull() ?: 1,
                sourceAnchorType = block.sourceAnchorType,
                sourceAnchorRecordId = block.sourceAnchorRecordId,
                sourceAnchorKey = block.sourceAnchorKey,
                fieldPath = block.fieldPath,
                displayOrder = block.displayOrder,
                metadata = block.metadata - setOf(METADATA_PARENT_NODE_ID, METADATA_COLLAPSED, METADATA_DEPTH),
            )
        }
        return normalizeNodes(rootNodeId, nodes)
    }

    private fun deriveBlocksFromNodes(rootNodeId: String, nodes: List<ResumeEditorNodeDto>): List<ResumeEditorBlockDto> =
        normalizeNodes(rootNodeId, nodes).filter { it.nodeId != rootNodeId }.mapIndexed { index, node ->
            val (text, lines, title) = when {
                node.nodeType in titleOnlyBlockTypes -> Triple(null, emptyList(), node.text)
                node.nodeType in lineBasedBlockTypes -> Triple(null, node.text?.split('\n')?.filter { it.isNotBlank() }.orEmpty(), null)
                else -> Triple(node.text, emptyList(), null)
            }
            ResumeEditorBlockDto(
                blockId = node.nodeId,
                blockType = node.nodeType,
                title = title,
                text = text,
                lines = lines,
                sourceAnchorType = node.sourceAnchorType,
                sourceAnchorRecordId = node.sourceAnchorRecordId,
                sourceAnchorKey = node.sourceAnchorKey,
                fieldPath = node.fieldPath,
                displayOrder = index,
                metadata = node.metadata + mapOf(
                    METADATA_PARENT_NODE_ID to (node.parentNodeId ?: rootNodeId),
                    METADATA_COLLAPSED to node.collapsed.toString(),
                    METADATA_DEPTH to node.depth.toString(),
                ),
                inlineMarks = buildInlineMarks(node.textRuns),
            )
        }

    private fun buildTableOfContents(nodes: List<ResumeEditorNodeDto>): List<ResumeEditorTableOfContentsItemDto> =
        nodes.filter { it.nodeId != ROOT_NODE_ID && it.nodeType in tableOfContentsNodeTypes && !it.text.isNullOrBlank() }
            .map { ResumeEditorTableOfContentsItemDto(it.nodeId, it.text.orEmpty(), it.depth, it.fieldPath) }

    private fun buildTextRuns(text: String, inlineMarks: List<ResumeEditorInlineMarkDto>): List<ResumeEditorTextRunDto> {
        if (text.isEmpty()) return emptyList()
        if (inlineMarks.isEmpty()) return listOf(ResumeEditorTextRunDto(text = text))
        val boundaries = mutableSetOf(0, text.length)
        inlineMarks.forEach {
            boundaries += it.startOffset.coerceIn(0, text.length)
            boundaries += it.endOffset.coerceIn(0, text.length)
        }
        return boundaries.sorted().zipWithNext().mapNotNull { (start, end) ->
            if (end <= start) null else {
                val activeMarks = inlineMarks.filter { start >= it.startOffset && end <= it.endOffset }
                ResumeEditorTextRunDto(
                    text = text.substring(start, end),
                    marks = activeMarks.map { it.markType }.distinct(),
                    href = activeMarks.firstOrNull { it.markType == "link" }?.href,
                )
            }
        }
    }

    private fun buildInlineMarks(textRuns: List<ResumeEditorTextRunDto>): List<ResumeEditorInlineMarkDto> {
        val marks = mutableListOf<ResumeEditorInlineMarkDto>()
        var offset = 0
        val activeMarkRanges = linkedMapOf<Pair<String, String?>, Pair<Int, String>>()
        textRuns.forEach { run ->
            val nextOffset = offset + run.text.length
            val currentKeys = run.marks.map { it to if (it == "link") run.href else null }.toSet()
            val expiredKeys = activeMarkRanges.keys - currentKeys
            expiredKeys.forEach { key ->
                val removedRange = activeMarkRanges.remove(key) ?: return@forEach
                val (startOffset, text) = removedRange
                marks += ResumeEditorInlineMarkDto(key.first, startOffset, offset, text, key.second)
            }
            run.marks.forEach { markType ->
                val key = markType to if (markType == "link") run.href else null
                val existing = activeMarkRanges[key]
                activeMarkRanges[key] = if (existing == null) offset to run.text else existing.first to (existing.second + run.text)
            }
            offset = nextOffset
        }
        activeMarkRanges.forEach { (key, value) ->
            marks += ResumeEditorInlineMarkDto(key.first, value.first, offset, value.second, key.second)
        }
        return marks.sortedWith(compareBy({ it.startOffset }, { it.endOffset }, { it.markType }))
    }

    private fun requireBlockIndex(blocks: List<ResumeEditorBlockDto>, nodeId: String?): Int {
        val normalizedNodeId = nodeId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "nodeId is required for this editor operation")
        return blocks.indexOfFirst { it.blockId == normalizedNodeId }
            .takeIf { it >= 0 }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown resume editor nodeId: $normalizedNodeId")
    }

    private fun requireOffset(value: Int?, fieldName: String): Int =
        value ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$fieldName is required for this editor operation")

    private fun rewriteBlockContent(block: ResumeEditorBlockDto, newContent: String): ResumeEditorBlockDto =
        when (block.blockType) {
            in titleOnlyBlockTypes -> block.copy(title = newContent, text = null, lines = emptyList())
            in lineBasedBlockTypes -> block.copy(title = null, text = null, lines = splitContentLines(newContent))
            else -> block.copy(text = newContent, lines = emptyList())
        }

    private fun splitContentLines(text: String): List<String> =
        text.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }

    private fun generateOperationNodeId(baseNodeId: String, suffix: String, index: Int): String =
        "${baseNodeId}-${suffix}-${index + 1}"

    private fun blockContent(block: ResumeEditorBlockDto): String =
        listOfNotNull(block.title?.takeIf { it.isNotBlank() }, block.text?.takeIf { it.isNotBlank() })
            .plus(block.lines)
            .joinToString("\n")

    companion object {
        const val DOCUMENT_AST_VERSION = 2
        const val ROOT_NODE_ID = "root"
        const val NODE_TYPE_ROOT = "root"
        const val METADATA_PARENT_NODE_ID = "parentNodeId"
        const val METADATA_COLLAPSED = "collapsed"
        const val METADATA_DEPTH = "depth"
        const val METADATA_SPLIT_ORIGIN = "splitOrigin"
        const val METADATA_DUPLICATED_FROM = "duplicatedFrom"
        val titleOnlyBlockTypes = setOf("header", "section_heading")
        val lineBasedBlockTypes = setOf("bullet_item", "skills_group", "contact")
        val tableOfContentsNodeTypes = setOf("header", "section_heading")
    }
}
