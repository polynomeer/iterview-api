# 02-backend-architecture

## Architecture Style
Use package-by-domain and keep business rules in services.

Current implemented domains:
- `auth`
- `common`
- `user`
- `resume`
- `question`
- `answer`
- `review`
- `dailycard`
- `feed`

Planned additive domain:
- `skill`

The new product direction should fit into this structure instead of introducing a parallel architecture.

## Domain Responsibilities
### `user`
- current user profile and settings
- target company priorities
- role and experience context used by question relevance and benchmark comparisons

### `resume`
- resume container lifecycle
- immutable resume versions
- future resume extraction snapshots, resume risks, and resume-derived question hooks

### `question`
- global question catalog
- category, tag, company, role, and learning material relationships
- future question tree and follow-up relationships
- future question-to-skill mappings

### `answer`
- immutable answer attempts
- centralized scoring
- feedback persistence
- future richer answer analysis snapshots

### `review`
- retry scheduling
- archive decisions
- review queue reads and actions
- future review prioritization inputs from resume risk and skill gaps

### `dailycard`
- daily question generation
- home aggregation
- future readiness, weak-skill, and resume-risk previews

### `feed`
- popular, trending, and company-related catalog slices

### `skill` (planned)
- skill-category score aggregation
- benchmark comparison
- gap analysis
- progress trend APIs

## Package Layout
Each domain package should continue to use:
- `controller`
- `service`
- `repository`
- `entity`
- `dto`
- `mapper`
- `enum`

If a new feature cannot fit one of those folders cleanly, the feature design should be reconsidered before adding a new layer.

## Cross-Cutting Packages
### `common.config`
- Spring configuration
- security
- OpenAPI
- CORS

### `common.exception`
- global exception translation
- domain validation helpers

### `common.service`
- cross-domain infrastructure services such as clock and current-user access

Do not move product rules into `common`.

## Extension Principles
### 1. Reuse Existing Aggregates
- `resume_versions` is the anchor for resume intelligence
- `user_question_progress` remains the canonical user-question cache
- `answer_attempts`, `answer_scores`, and `answer_feedback_items` remain the base evidence for later analysis

### 2. Add, Don’t Break
- prefer nullable columns, new tables, or new read models over incompatible schema changes
- keep current endpoints stable while adding optional fields or new endpoints
- do not break the current authentication and question-answer-review flow

### 3. Keep Read and Write Models Simple
- command paths stay transactional and domain-owned
- analytical or dashboard-oriented payloads may assemble from multiple tables in service code
- denormalized read models are acceptable if introduced later for performance

## Service Boundaries
### Profile and Preferences
- get current user aggregate
- update profile
- update settings
- replace target companies

### Resume Intelligence
- create resume container
- create resume version
- activate version
- list versions
- future: persist extraction results for skills, experiences, and risks

### Question Discovery
- list active questions with filters
- get question detail with optional user progress
- future: get question tree, follow-up nodes, and resume-based recommendations

### Answer and Analysis
- submit answer attempt
- calculate attempt number
- score answer
- persist feedback
- update progress aggregate
- future: persist analysis dimensions used by skill radar and gap analysis

### Review and Learning Loop
- determine retry need
- schedule or clear retry items
- list review queue
- archive mastered questions
- future: raise review priority for resume-risk and benchmark-gap areas

### Home and Readiness
- generate today’s primary question
- show retry preview
- show learning materials
- future: add weak-skill preview, resume-risk preview, and readiness summary

## Transaction Boundaries
- answer submission must remain transactional
- score persistence, feedback creation, progress updates, and retry scheduling should stay in the same transaction
- resume activation should remain transactional so one version is active per resume
- read-heavy aggregation endpoints should use read-only transactions where appropriate

## Future Async Boundaries
These belong behind service interfaces and should not leak into controllers:
- resume parsing
- resume signal extraction
- answer deep analysis
- skill score recalculation
- benchmark refresh jobs

The current implementation can remain synchronous until those workflows are explicitly introduced.

## Error Handling
At minimum keep explicit domain errors for:
- user not found
- resume not found
- resume version not found
- question not found
- invalid resume version ownership
- answer attempt not found
- invalid review queue action

Planned additions:
- resume extraction not ready
- question tree not found
- skill benchmark not available

## Backward Compatibility Rules
- existing REST paths remain valid
- existing DTO fields keep their current names
- newly added intelligence features should be exposed through additive DTO fields or new endpoints
- current `question`, `answer`, `review`, and `home` payloads must remain usable by existing clients
