# Resume Editor V2 Backend Prompt

You are implementing Resume Editor V2 for Iterview.

This document is both the design brief and the execution prompt. Do not treat it as planning-only. Carry the work through backend implementation, tests, runtime OpenAPI, checked-in OpenAPI, and backend docs.

## Read First

Review and extend the current backend implementation and docs around the editor slice:

- `/Users/hammac/Projects/iterview-api/docs/openapi/frontend-integration.yaml`
- `/Users/hammac/Projects/iterview-api/docs/08-frontend-api.md`
- `/Users/hammac/Projects/iterview-api/docs/04-api-contracts.md`
- `/Users/hammac/Projects/iterview-api/docs/feature-resume-editor.md`

Source-of-truth priority:

1. runtime OpenAPI at `/v3/api-docs`
2. checked-in OpenAPI snapshot
3. backend docs
4. frontend docs

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
- additive changes are strongly preferred over breaking changes
- merge-preview and revision semantics must still work with optimistic concurrency
- `baseRevisionNo` remains the concurrency guard
- this is not CRDT or true collaborative editing
- presence remains lightweight heartbeat state only

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

for the migration period.

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

## Minimum implementation scope

Implement the backend slices needed for:

1. rich document model support
2. operation-based editor writes
3. stable selection anchors for comments/question cards/suggestions
4. rich tracked changes
5. rich merge preview
6. revision and print-preview compatibility
7. source-anchor alignment for heatmap/editor interoperability

## Expected live API surface

Keep existing endpoints working:

- `GET /api/resume-versions/{versionId}/editor`
- `PUT /api/resume-versions/{versionId}/editor/document`
- `POST /api/resume-versions/{versionId}/editor/import-markdown`
- existing comment, reply, question-card, suggestion, presence, print-preview, revision, tracked-change, merge-preview endpoints

Add the new granular write endpoint:

- `PATCH /api/resume-versions/{versionId}/editor/document/operations`

Upgrade existing editor DTOs and request DTOs to support the v2 rich-node model and selection anchors while remaining migration-safe.

## Required backend deliverables

### 1. Implementation

Implement:

- rich document persistence/materialization for editor workspace reads
- operation patch handling
- selection-anchor aware comment creation
- selection-anchor aware question-card creation
- selection-anchor aware suggestion requests
- rich merge preview behavior
- rich tracked changes behavior

### 2. Tests

Add or update backend tests for:

- workspace bootstrap into rich document form
- operation-based patching
- markdown import compatibility with the rich document model
- comment creation on inline range anchors
- question-card creation on inline range anchors
- suggestion requests on inline range anchors
- tracked changes across revisions
- merge preview with text conflicts
- merge preview with structural conflicts

### 3. API/docs

Update:

- runtime OpenAPI generation
- `/Users/hammac/Projects/iterview-api/docs/openapi/frontend-integration.yaml`
- `/Users/hammac/Projects/iterview-api/docs/08-frontend-api.md`
- `/Users/hammac/Projects/iterview-api/docs/04-api-contracts.md`
- `/Users/hammac/Projects/iterview-api/docs/feature-resume-editor.md`

Document clearly:

- what is live v1
- what is additive v2
- migration compatibility behavior

## Implementation guidance

- prefer extending the current editor slice rather than creating a disconnected parallel system
- keep legacy `blocks[]` and `markdownSource` materialization available while v2 rolls out
- make the rich-node model the long-term source for editor semantics
- ensure merge preview and tracked changes can reference stable node identifiers
- preserve source-anchor metadata on rich nodes so resume heatmap integration remains possible
- do not stop at DTO definitions or docs-only updates
- if runtime OpenAPI and checked-in docs differ, update the checked-in docs to match runtime OpenAPI

## Output format

For each completed slice, report:

1. what was implemented
2. which files changed
3. which endpoints and DTOs were added or updated
4. what remains next

## Definition of done

The task is only done when:

- backend implementation is complete for the agreed v2 scope
- relevant automated tests pass
- runtime OpenAPI reflects the new contracts
- checked-in OpenAPI snapshot matches runtime behavior
- backend docs are updated
- migration compatibility with v1 editor behavior is preserved
