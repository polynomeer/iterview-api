# 08-frontend-api

## Goal
Provide a frontend-facing API reference for the currently implemented interview intelligence flows without mixing documentation concerns into business logic.

## Available Sources
Use these in order:

1. Runtime OpenAPI JSON
   - `GET /v3/api-docs`
2. Runtime Swagger UI
   - `GET /swagger-ui.html`
   - enabled by default in `local`
3. Checked-in frontend snapshot
   - [`docs/openapi/frontend-integration.yaml`](/Users/hammac/Projects/iterview-api/docs/openapi/frontend-integration.yaml)

## Scope of the Frontend Snapshot
The checked-in OAS file focuses on the endpoints the current frontend is most likely to consume for the updated product direction:

- home aggregation
- profile and profile image upload
- resume intelligence
- question detail, tree, and follow-ups
- future question reference content such as model answers and curated learning materials
- answer history and answer analysis
- skill radar, gap, and progress APIs
- review queue APIs
- interview session APIs

It is intentionally additive. Existing baseline endpoints such as auth, profile, and feed still exist and remain available from the live `/v3/api-docs` document.

## Integration Notes
- Authenticated endpoints use bearer JWT auth.
- Product locale initially supports `ko` and `en`.
- Clients should send `Accept-Language` when they need an explicit localized response.
- Stored user preference may also affect localized fields returned by the backend.
- Original user-authored source content such as resume excerpts and answer text remains in the original language even when surrounding UI-facing fields are localized.
- Machine-readable fields such as `status`, `sourceType`, and error `code` remain locale-neutral.
- The backend path names are `/api/skills/radar` and `/api/skills/gaps`.
- Resume PDF upload is `POST /api/resumes/{resumeId}/versions/upload` with `multipart/form-data`.
- Resume version polling is `GET /api/resume-versions/{versionId}`.
- Resume extraction status is `GET /api/resume-versions/{versionId}/extraction`.
- Resume file download is authenticated at `GET /api/resume-versions/{versionId}/file`.
- Resume re-extraction is `POST /api/resume-versions/{versionId}/re-extract`.
- The current resume flow guarantees raw PDF parsing and versioned storage. Structured field mapping now returns explicit extraction metadata, and OpenAI-backed extraction is used when configured.
- `llmExtractionStatus` is additive metadata. Clients should handle at least `pending`, `completed`, `skipped`, `fallback`, and `failed`.
- Implemented rich resume reads are:
  - `GET /api/resume-versions/{versionId}/profile`
  - `GET /api/resume-versions/{versionId}/contacts`
  - `GET /api/resume-versions/{versionId}/competencies`
  - `GET /api/resume-versions/{versionId}/skills`
  - `GET /api/resume-versions/{versionId}/experiences`
  - `GET /api/resume-versions/{versionId}/projects`
  - `GET /api/resume-versions/{versionId}/achievements`
  - `GET /api/resume-versions/{versionId}/education`
  - `GET /api/resume-versions/{versionId}/certifications`
  - `GET /api/resume-versions/{versionId}/awards`
  - `GET /api/resume-versions/{versionId}/risks`
- The current project endpoint should be treated as the stable base for resume-derived project cards.
- Implemented project payload fields now include:
  - `contentText`
  - `projectCategoryCode`
  - `projectCategoryName`
  - `tags`
- Frontend should render projects as richer cards rather than only short experience subrows.
- Question detail includes generic `learningMaterials` and additive `referenceAnswers`.
- Dedicated question reference-content reads are:
  - `GET /api/questions/{questionId}/reference-answers`
  - `GET /api/questions/{questionId}/learning-materials`
- The skill APIs recalculate and persist score snapshots server-side; frontend clients should treat them as read APIs.
- Interview sessions are minimal turn-based APIs. They do not imply realtime or streaming behavior.
- Interview history is now available from `GET /api/interview-sessions` as session-level summaries.
- Archive payloads now include additive source fields so the frontend can render `Practice` and `Interview` badges without changing archive list semantics.
- Asked interview turns are now mirrored into archive as question-level records, while interview history remains session-level.
- Session question payloads now include follow-up metadata:
  - `sourceType`
  - `parentSessionQuestionId`
  - `isFollowUp`
  - `depth`
  - `categoryName`
- Resume interview sessions may now return AI-generated follow-up snapshots with:
  - `bodyText`
  - `tags`
  - `focusSkillNames`
  - `resumeContextSummary`
  - `generationRationale`
  - `generationStatus`
  - `llmModel`
  - `llmPromptVersion`
- Implemented additive session-question metadata for resume-grounded evidence:
  - `resumeEvidence`
  - each item may include:
    - `type`
    - `section`
    - `label`
    - `snippet`
    - `sourceRecordType`
    - `sourceRecordId`
    - `confidence`
- Current interview generation scope is narrower than the full resume model:
  - opener and follow-up generation currently use `project` and `experience` evidence only
  - profile summary, contacts, competencies, awards, certifications, and education are not currently used as interview question sources
- Resume interview creation should expose one explicit `resumeVersionId` selector in the frontend start flow rather than silently relying on whichever version happens to be active.
- implemented interview-start configuration now accepts `interviewMode` values such as `quick_screen`, `mock_30`, `mock_60`, `free_interview`, and `full_coverage`
- The opening question for a resume-based interview may also be AI-generated from the selected resume version and should be rendered from session snapshot fields the same way as AI follow-ups.
- Frontend should not assume every follow-up maps to a global `questionId`; AI-generated follow-ups may rely on snapshot fields only.
- Recommended rendering fallback for session questions:
  - use `title` as the primary visible prompt
  - use `bodyText` as supporting interviewer framing when present
  - use `questionId` only for deep-linking or fetching catalog question detail when non-null
  - use `sourceType` and `generationStatus` together to distinguish seeded, catalog follow-up, and AI-generated turns
- Recommended interview-start flow:
  - fetch resume containers or latest resume summary before opening the Interview start sheet
  - present one explicit resume-version selector for `resume_mock`
  - send the selected `resumeVersionId` in `POST /api/interview-sessions`
  - navigate to `/interviews/{sessionId}` and render the returned opening question immediately
- Recommended archive badge mapping:
  - `sourceType = practice` -> `Practice`
  - `sourceType = interview` -> `Interview`
  - `isFollowUp = true` may be rendered as a secondary follow-up badge, not a replacement for source type
- For interview-originated archive rows, use `sourceSessionId` as the backlink anchor and `sourceSessionQuestionId` as the stable turn identifier.
- Recommended interview timeline rendering:
  - order by `orderIndex`
  - use `parentSessionQuestionId` and `depth` for indentation or connector lines
  - show `focusSkillNames` and `resumeContextSummary` as secondary evidence, not as the main prompt content
  - when `resumeEvidence` is present, render one compact `Based on your resume` block on the question card
  - default to showing at most the first one or two evidence items
  - use `section` or `label` as a small badge or eyebrow label
  - use `snippet` as the visible quoted evidence text
  - never replace the question title with the evidence snippet
  - do not require deep-link behavior for the first implementation; `sourceRecordType` and `sourceRecordId` are forward-compatible metadata
- recommended session progression behavior:
  - treat `POST /api/interview-sessions/{sessionId}/next-question` as an advance action only
  - if the current question is still unanswered, prompt the user to submit an answer or skip the question first
  - use `POST /api/interview-sessions/{sessionId}/skip-question` when the user wants to bypass the current prompt
  - after answer or skip, `next-question` may resolve the next queued question or lazily generate the next `full_coverage` question
  - for `full_coverage`, `summary.weakFacetSummaries` and `summary.skippedFacetSummaries` are additive helpers for in-session side panels or progress callouts
  - once all `unasked` evidence has been consumed, the backend will usually revisit `weak` facets before `skipped` facets and will only then fall back to already-defended evidence
  - weak-facet revisit questions may sound more like re-validation or evidence challenge prompts than first-pass overview questions
- implemented full-coverage result support:
  - `GET /api/interview-sessions/{sessionId}/coverage`
  - `GET /api/interview-sessions/{sessionId}/resume-map`
  - use these to render a result-time resume viewer where one hovered or clicked resume evidence item can reveal related interview questions
- recommended full-coverage frontend behavior:
  - explain that coverage is measured against interviewable resume evidence units, not every raw character
  - one parsed project or experience block may map to multiple evidence snippets and therefore multiple interview turns over time
  - show overall coverage percent plus per-section completion
  - handle `coverageStatus` values such as `unasked`, `asked`, `defended`, `weak`, and `skipped`
  - do not assume the session ends immediately when coverage reaches 100%; the backend may continue with extra deep-dive questions
  - extra deep-dive turns generated after coverage completion may carry `generationStatus = coverage_extended`
  - prefer a structured resume viewer based on parsed experiences and projects instead of first attempting raw PDF overlays
  - use `sourceRecordType` + `sourceRecordId` from `resume-map` as the join key back into parsed resume sections
  - use `displayOrder` from `coverage` or `resume-map` to keep highlighted resume blocks aligned with parsed resume section ordering
  - `facetSummaries` are additive record-level summaries grouped by `sourceRecordType` + `sourceRecordId`
  - `weakFacetSummaries` can drive "needs more defense" panels without recomputing weak facets from raw evidence items
  - `skippedFacetSummaries` can drive separate skipped-area panels or badges in the result view
  - `facet` is additive metadata and can be used for debug labels or richer question grouping, but should not be required for the basic UI
  - in the result view, hovering a highlighted resume block should show related questions in a lightweight preview
  - clicking a highlighted resume block should pin the related questions and allow navigation or scrolling back to the relevant question card
  - use `primaryQuestionCount` and `followUpQuestionCount` to summarize how many turns are attached before expanding the full related-question list
  - use `relatedQuestions[].orderIndex`, `status`, and `isFollowUp` to render the preview in timeline order without fetching another intermediate mapping structure
  - treat current result highlighting scope as `project` and `experience` only
- The home payload is backward compatible. Newly added fields are optional and can be ignored by older clients.
- Mixed-language rendering is expected and should be handled gracefully:
  - UI chrome and localized labels may be English
  - AI-generated question text may be English
  - resume evidence snippets may still be Korean original text

## Recommended Frontend Usage
- During local integration, point Swagger or codegen tooling at `/v3/api-docs`.
- For PR review, schema discussion, or frontend mocking, use the checked-in snapshot file.
- If the runtime API and snapshot diverge, treat the runtime `/v3/api-docs` as the operational source and update the snapshot in the same backend change.
