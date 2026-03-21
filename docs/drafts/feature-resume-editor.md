# feature-resume-editor

## Goal
Upgrade the current resume editor from a flat block-form workspace into a document-centered resume workspace that can support a Notion-level frontend while preserving immutable resume versions.

The source resume version must remain immutable. The editor continues to be one additive draft workspace layered on top of a single immutable resume version.

## Current v1 baseline

The backend currently supports:

- `GET /api/resume-versions/{versionId}/editor`
- `PUT /api/resume-versions/{versionId}/editor/document`
- `POST /api/resume-versions/{versionId}/editor/import-markdown`
- `POST /api/resume-versions/{versionId}/editor/comments`
- `PATCH /api/resume-versions/{versionId}/editor/comments/{commentId}`
- `POST /api/resume-versions/{versionId}/editor/comments/{commentId}/replies`
- `POST /api/resume-versions/{versionId}/editor/presence`
- `POST /api/resume-versions/{versionId}/editor/question-cards`
- `PATCH /api/resume-versions/{versionId}/editor/question-cards/{cardId}`
- `POST /api/resume-versions/{versionId}/editor/auto-question-suggestions`
- `POST /api/resume-versions/{versionId}/editor/rewrite-suggestions`
- `GET /api/resume-versions/{versionId}/editor/print-preview`
- `GET /api/resume-versions/{versionId}/editor/revisions`
- `GET /api/resume-versions/{versionId}/editor/revisions/{revisionId}`
- `GET /api/resume-versions/{versionId}/editor/tracked-changes`
- `POST /api/resume-versions/{versionId}/editor/merge-preview`

This is enough for a markdown-first fallback workspace, but not enough for a true Notion-like frontend because the document model is still too flat and the selection anchors are too weak.

## Product direction for v2

The editor should become a single document-centered workspace where users can:

- edit resume content in one markdown-friendly rich surface
- click a block or select one inline sentence or phrase
- open one contextual popover or menu for comments, question cards, suggestions, or source-aware actions
- keep comments and question cards attached to stable inline anchors
- split, merge, move, indent, outdent, and re-type blocks without rewriting the whole document for every small edit
- preserve revision history, tracked changes, print preview, and merge-preview recovery

The backend should not implement true real-time CRDT collaboration. Presence remains lightweight heartbeat state.

## Required v2 backend changes

### 1. Rich document model

Keep the existing workspace endpoint, but extend it with a richer additive document contract.

`ResumeEditorWorkspaceDto` should add fields such as:

- `documentModel`
- `selectionCapabilities`
- `contextMenuActions`

`ResumeEditorDocumentDto` should add fields such as:

- `rootNodeId`
- `nodes[]`
- `tableOfContents[]`
- `markdownSource`
- legacy `blocks[]` during migration

Add `ResumeEditorNodeDto` with fields such as:

- `nodeId`
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

Add `ResumeEditorTextRunDto` with fields such as:

- `text`
- `marks[]`

### 2. Operation-based write API

Add one granular write endpoint:

- `PATCH /api/resume-versions/{versionId}/editor/document/operations`

The request should contain:

- `operations[]`
- `baseRevisionNo`
- `changeSource`
- `clientSessionKey`
- `clientChangeId`

Supported operation types should cover at least:

- text insert
- text replace
- text delete
- block split
- block merge
- block move
- block duplicate
- block remove
- block type change
- indent
- outdent
- inline mark add
- inline mark remove
- collapse toggle

`PUT /editor/document` remains as a full-document replacement fallback for coarse saves, import acceptance, or merge acceptance.

### 3. Stable selection anchors

Comments, question cards, and suggestion requests need a richer selection anchor than one flat `blockId`.

Add `ResumeEditorSelectionAnchorDto` with fields such as:

- `nodeId`
- `anchorPath`
- `fieldPath`
- `selectionStartOffset`
- `selectionEndOffset`
- `selectedText`
- `anchorQuote`
- `sentenceIndex`

Update the create-comment, create-question-card, question-suggestion, and rewrite-suggestion request DTOs so they can accept one additive `selectionAnchor` object while still tolerating legacy block-level fields during migration.

### 4. Rich tracked changes and merge preview

Upgrade:

- `GET /editor/tracked-changes`
- `POST /editor/merge-preview`

so they can report:

- node-level additions, removals, and moves
- text changes inside a node
- conflicting inline edits on the same node
- structural conflicts versus text conflicts

Conflict DTOs should identify at least:

- `nodeId`
- `conflictType`
- `baseText`
- `currentText`
- `proposedText`
- optionally structural context such as parent or index changes

### 5. Revision and print-preview compatibility

Revision detail and print-preview must continue to work with the richer document model.

- revision detail should expose the rich node snapshot
- print preview can still remain approximate and section-based
- markdown portability must remain intact

### 6. Heatmap and source-anchor alignment

Rich nodes must preserve source-anchor linkage so the editor can stay aligned with the resume heatmap:

- `sourceAnchorType`
- `sourceAnchorRecordId`
- `sourceAnchorKey`
- `fieldPath`

## Compatibility policy

- v1 editor endpoints remain valid during migration
- `markdownSource` remains first-class
- `blocks[]` can remain available for fallback and compatibility
- frontend can feature-detect `documentModel = rich_tree` or operation capabilities before switching to the richer editor surface

## Documentation requirements

When implementing v2, update all of the following together:

- runtime OpenAPI `/v3/api-docs`
- `docs/openapi/frontend-integration.yaml`
- `docs/04-api-contracts.md`
- `docs/08-frontend-api.md`
- this `docs/feature-resume-editor.md`

The docs must clearly distinguish:

- current live v1 behavior
- planned or newly live v2 behavior
- compatibility or migration behavior

## Testing requirements

Add backend tests for:

- workspace bootstrap into the rich document form
- operation-based patching
- selection-anchor comment creation
- selection-anchor question-card creation
- deterministic suggestions on inline anchors
- tracked changes across revisions
- merge preview for text conflicts
- merge preview for structural conflicts
- markdown import compatibility with the rich document model

## Success criteria

V2 is ready when:

- the frontend can render one document-centered editor surface
- the frontend can open one contextual menu from block or inline selection
- comments and question cards can survive normal document edits with stable anchors when practical
- merge preview and revision compare work against the richer document model
- the immutable source resume version is still never overwritten
