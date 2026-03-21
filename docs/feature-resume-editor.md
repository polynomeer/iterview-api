# Feature: Resume Editor

## Goal

Provide one additive editor workspace on top of one immutable `resume_version` so the frontend can support:

- block and markdown fallback editing
- richer tree-based document rendering
- inline marks
- stable inline selection anchors
- comments and replies
- question cards
- deterministic question and rewrite suggestions
- revision history
- tracked changes
- merge preview
- print preview

The source resume version remains immutable. The editor workspace is a draft layer only.

## Live API Surface

Workspace:

- `GET /api/resume-versions/{versionId}/editor`
- `PUT /api/resume-versions/{versionId}/editor/document`
- `PATCH /api/resume-versions/{versionId}/editor/document/operations`
- `POST /api/resume-versions/{versionId}/editor/import-markdown`

Annotations and suggestions:

- `POST /api/resume-versions/{versionId}/editor/comments`
- `PATCH /api/resume-versions/{versionId}/editor/comments/{commentId}`
- `POST /api/resume-versions/{versionId}/editor/comments/{commentId}/replies`
- `POST /api/resume-versions/{versionId}/editor/question-cards`
- `PATCH /api/resume-versions/{versionId}/editor/question-cards/{cardId}`
- `POST /api/resume-versions/{versionId}/editor/auto-question-suggestions`
- `POST /api/resume-versions/{versionId}/editor/rewrite-suggestions`

Collaboration, history, and preview:

- `POST /api/resume-versions/{versionId}/editor/presence`
- `GET /api/resume-versions/{versionId}/editor/revisions`
- `GET /api/resume-versions/{versionId}/editor/revisions/{revisionId}`
- `GET /api/resume-versions/{versionId}/editor/tracked-changes`
- `POST /api/resume-versions/{versionId}/editor/merge-preview`
- `GET /api/resume-versions/{versionId}/editor/print-preview`

## V1 and V2 Compatibility

V1 remains live:

- `document.blocks[]`
- `markdownSource`
- `PUT /editor/document`
- `POST /editor/import-markdown`
- legacy `blockId`-based comment/question-card/suggestion payloads

V2 additive behavior is now live:

- `documentModel = rich_tree`
- `document.rootNodeId`
- `document.nodes[]`
- `document.tableOfContents[]`
- `selectionCapabilities`
- `contextMenuActions`
- `selectionAnchor`
- `PATCH /editor/document/operations`
- node-aware tracked changes
- node-aware merge conflicts

Frontend migration rule:

1. if `documentModel = rich_tree`, prefer `nodes[]` for semantic rendering
2. still keep `blocks[]` and `markdownSource` for fallback and compatibility
3. prefer `selectionAnchor` over raw `blockId` for inline actions

## Rich Document Model

`ResumeEditorDocumentDto` now carries both:

- legacy `blocks[]`
- additive rich tree fields:
  - `rootNodeId`
  - `nodes[]`
  - `tableOfContents[]`

`ResumeEditorNodeDto` preserves:

- stable `nodeId`
- `parentNodeId`
- `nodeType`
- `text`
- `textRuns[]`
- `children[]`
- `collapsed`
- `depth`
- `sourceAnchorType`
- `sourceAnchorRecordId`
- `sourceAnchorKey`
- `fieldPath`
- `displayOrder`
- `metadata`

This is intentionally additive so the frontend can evolve from repeated block forms into one document surface without losing backward compatibility.

## Selection Anchors

Inline annotation and suggestion flows now accept `ResumeEditorSelectionAnchorDto`:

- `nodeId`
- `anchorPath`
- `fieldPath`
- `selectionStartOffset`
- `selectionEndOffset`
- `selectedText`
- `anchorQuote`
- `sentenceIndex`

Compatibility behavior:

- legacy `blockId` payloads still work
- if `selectionAnchor` is present, it is the preferred source of truth

## Operation Patch API

`PATCH /editor/document/operations` supports granular editor writes under the same `baseRevisionNo` concurrency guard.

Current supported operation types:

- `text_insert`
- `text_replace`
- `text_delete`
- `block_split`
- `block_merge`
- `block_move`
- `block_duplicate`
- `block_remove`
- `block_type_change`
- `indent`
- `outdent`
- `inline_mark_add`
- `inline_mark_remove`
- `collapse_toggle`

The response returns the updated workspace so the frontend can refresh revision metadata and rich document state in one round trip.

## Revision, Diff, and Merge

`GET /editor/tracked-changes` now returns node-aware change rows with:

- `nodeId`
- `beforeParentNodeId`
- `afterParentNodeId`
- `beforeDepth`
- `afterDepth`
- `textChanged`
- `structureChanged`
- `moveRelated`

`POST /editor/merge-preview` now returns conflict rows with:

- `nodeId`
- `conflictType`
- `baseText`
- `currentText`
- `proposedText`
- `conflictScopes`
- `baseParentNodeId`
- `currentParentNodeId`
- `proposedParentNodeId`

This is still optimistic-concurrency-based merge assistance, not CRDT or live multi-user editing.

## Print Preview

Print preview remains a server-built read model and stays compatible with the richer workspace:

- section grouping
- page hints
- layout items

These are layout hints only, not true PDF coordinates.

## Heatmap Alignment

Rich editor nodes continue to preserve:

- `sourceAnchorType`
- `sourceAnchorRecordId`
- `sourceAnchorKey`
- `fieldPath`

This keeps the editor interoperable with resume heatmap and overlay views.
