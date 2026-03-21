# Resume Editor V2 Backend Prompt

You are implementing the backend API upgrade for the Iterview resume editor so the frontend can provide a Notion-level resume workspace on top of immutable resume versions.

Use the existing backend architecture, runtime OpenAPI style, and current resume-editor slice as the base. The new work must preserve the current immutable resume-version model and additive editor workspace model.

## Product Goal

Upgrade the current block-oriented resume editor into a rich document workspace that supports:

- markdown-friendly editing
- nested or tree-based block structure
- inline text marks
- stable inline selection anchors
- contextual actions from block or sentence selection
- comments and replies on inline ranges
- question cards on inline ranges
- deterministic question and rewrite suggestions on inline ranges
- tracked changes and merge-preview on rich nodes
- print preview and revisions on the richer document model

The frontend should be able to render one main document surface instead of one repeated block form list.

## Non-negotiable constraints

- resume versions remain immutable
- editor remains one additive workspace layer on top of one immutable resume version
- current v1 endpoints should stay available during migration where practical
- new fields should be additive when possible
- the checked-in OpenAPI snapshot and frontend-facing docs must be updated
- merge-preview and revision semantics must still work with optimistic concurrency
- this is not CRDT or true collaborative editing; presence remains lightweight

## Current backend baseline

The backend already has:

- `GET /api/resume-versions/{versionId}/editor`
- `PUT /api/resume-versions/{versionId}/editor/document`
- `POST /api/resume-versions/{versionId}/editor/import-markdown`
- comment, reply, question-card, suggestion, presence, print-preview, revision, tracked-change, and merge-preview endpoints

The current limitation is that the document model is too flat and selection anchors are too weak for a true Notion-like frontend.

## Required backend changes

### 1. Rich document model

Extend the editor workspace contract so the frontend can read a rich tree document.

Additive DTO direction:

- `ResumeEditorWorkspaceDto`
  - add `documentModel`
  - add `selectionCapabilities`
  - add `contextMenuActions`
- `ResumeEditorDocumentDto`
  - add `rootNodeId`
  - add `nodes[]`
  - add `tableOfContents[]`
- add `ResumeEditorNodeDto`
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
- add `ResumeEditorTextRunDto`
  - `text`
  - `marks[]`

The backend should still be able to materialize:

- `markdownSource`
- legacy `blocks[]`

during the migration period.

### 2. Operation-based editing API

Add one granular write endpoint:

- `PATCH /api/resume-versions/{versionId}/editor/document/operations`

Request shape should include:

- `operations[]`
- `baseRevisionNo`
- `changeSource`
- `clientSessionKey`
- `clientChangeId`

Operations must be expressive enough for:

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

Response should return the updated workspace or at minimum the updated rich document plus revision metadata.

### 3. Stable selection anchors

Comments, question cards, and suggestion endpoints need stable inline anchors, not only `blockId`.

Add `ResumeEditorSelectionAnchorDto` with fields such as:

- `nodeId`
- `anchorPath`
- `fieldPath`
- `selectionStartOffset`
- `selectionEndOffset`
- `selectedText`
- `anchorQuote`
- `sentenceIndex`

Update create-comment, create-question-card, and suggestion request DTOs to accept this richer anchor object while still tolerating legacy block fields during migration.

### 4. Rich tracked changes and merge preview

Upgrade:

- `GET /editor/tracked-changes`
- `POST /editor/merge-preview`

so they can identify:

- node-level additions/removals/moves
- text changes inside one node
- conflicting inline edits on the same node

Merge conflicts should include enough information for the frontend to render:

- base text
- current text
- proposed text
- node id
- conflict type
- whether the conflict is text, structure, or move related

### 5. Revision and print-preview compatibility

Revision detail and print preview should continue to work with the rich document model.

- revision detail should expose the rich document snapshot
- print preview can still expose section/page/layout hints
- frontend must not lose markdown exportability

### 6. Heatmap and source-anchor alignment

Rich editor nodes should preserve source anchor linkage:

- `sourceAnchorType`
- `sourceAnchorRecordId`
- `sourceAnchorKey`
- `fieldPath`

This is needed so resume heatmap and editor annotations can stay aligned.

## Compatibility requirements

- keep `PUT /editor/document` for coarse replacement flows
- keep `POST /editor/import-markdown`
- keep current v1 block fields while introducing v2 rich-node fields
- if `documentModel` is absent, frontend should still be able to fall back to v1 behavior

## Documentation and API outputs required

Update:

- runtime OpenAPI
- `docs/openapi/frontend-integration.yaml`
- `docs/08-frontend-api.md`
- `docs/feature-resume-editor.md`
- any relevant API contract docs

Document clearly:

- what is live v1
- what is live or planned v2
- migration and compatibility behavior

## Testing expectations

Add backend tests for:

- workspace bootstrap into rich document form
- operation-based document patching
- selection-anchor comment creation
- selection-anchor question-card creation
- suggestion requests on inline anchors
- merge preview with text conflict
- merge preview with structural conflict
- tracked changes across revisions
- markdown import compatibility with the rich document model

## Output expectations

For each completed backend slice, report:

1. what was implemented
2. which files changed
3. which endpoints and DTOs were added or updated
4. what remains next

If runtime OpenAPI and checked-in docs differ, make runtime OpenAPI the source of truth and update the checked-in docs to match.
