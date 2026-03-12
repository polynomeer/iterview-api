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
- answer history and answer analysis
- skill radar, gap, and progress APIs
- review queue APIs
- interview session APIs

It is intentionally additive. Existing baseline endpoints such as auth, profile, and feed still exist and remain available from the live `/v3/api-docs` document.

## Integration Notes
- Authenticated endpoints use bearer JWT auth.
- The backend path names are `/api/skills/radar` and `/api/skills/gaps`.
- Resume PDF upload is `POST /api/resumes/{resumeId}/versions/upload` with `multipart/form-data`.
- Resume version polling is `GET /api/resume-versions/{versionId}`.
- Resume file download is authenticated at `GET /api/resume-versions/{versionId}/file`.
- The skill APIs recalculate and persist score snapshots server-side; frontend clients should treat them as read APIs.
- Interview sessions are minimal turn-based APIs. They do not imply realtime or streaming behavior.
- The home payload is backward compatible. Newly added fields are optional and can be ignored by older clients.

## Recommended Frontend Usage
- During local integration, point Swagger or codegen tooling at `/v3/api-docs`.
- For PR review, schema discussion, or frontend mocking, use the checked-in snapshot file.
- If the runtime API and snapshot diverge, treat the runtime `/v3/api-docs` as the operational source and update the snapshot in the same backend change.
