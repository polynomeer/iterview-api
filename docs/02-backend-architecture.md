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
- resume file intake metadata and processing state
- raw PDF parsing, LLM-backed structured extraction, resume extraction snapshots, resume risks, and resume-derived question hooks
- normalized resume profile, contact, credential, education, employment, and project records
- resume project records should be extensible enough to preserve title, long-form content, tags, and category classification from one PDF version

### `question`
- global question catalog
- category, tag, company, role, and learning material relationships
- future model-answer relationships and answer-exemplar metadata
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
- `learning_materials` remains the shared content library for editorial reference content
- richer resume subsections should still hang off `resume_version_id`, not create a second resume aggregate

### 2. Add, Don’t Break
- prefer nullable columns, new tables, or new read models over incompatible schema changes
- keep current endpoints stable while adding optional fields or new endpoints
- do not break the current authentication and question-answer-review flow

### 3. Keep Read and Write Models Simple
- command paths stay transactional and domain-owned
- analytical or dashboard-oriented payloads may assemble from multiple tables in service code
- denormalized read models are acceptable if introduced later for performance
- do not overload `answer_attempts` for curated model-answer content

## Service Boundaries
### Profile and Preferences
- get current user aggregate
- update profile
- update settings
- replace target companies

### Resume Intelligence
- create resume container
- create resume version from uploaded PDF metadata or backward-compatible imported text payload
- activate version
- list versions
- expose version processing state for pending, completed, or failed parsing
- persist raw parsed text on the version record
- future: call an LLM extraction boundary to map raw text into normalized skills, experiences, and risks
- future: persist extraction results and extraction metadata for:
  - profile headline and summary
  - contact points and external links
  - core competency statements
  - work experiences and project initiatives
  - project content blocks, project tags, and project categories
  - education, awards, and certifications
  - skill, risk, and achievement signals

### Question Discovery
- list active questions with filters
- get question detail with optional user progress
- future: get question tree, follow-up nodes, resume-based recommendations, model answers, and curated learning materials

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
- PDF file storage and retrieval
- resume parsing
- resume signal extraction
- LLM resume field extraction and normalization
- answer deep analysis
- skill score recalculation
- benchmark refresh jobs

Recommended resume pipeline:
1. accept multipart PDF upload and create immutable `resume_versions` row
2. persist file metadata and set `parsing_status`
3. parse binary PDF into `raw_text`
4. hand off `raw_text` to an LLM extraction service boundary
5. validate and normalize extracted fields against current domain vocabularies
6. persist structured resume sections against `resume_version_id`
7. derive skills, experiences, projects, quantified achievements, and risks from the structured output
7. expose status and extracted results through read APIs

Recommended validation layers for rich resume extraction:
- document-level checks:
  - headline or summary is not fabricated when absent
  - contact points are typed consistently as email, phone, blog, github, linkedin, etc.
- timeline checks:
  - employment periods and education periods preserve source ordering
  - company, role, and project groupings remain attributable to the same version
- achievement checks:
  - quantified metrics stay linked to the source claim text
  - high-impact claims can later feed interview-defense risk generation
- project checks:
  - project title, content, tags, and category remain attributable to one immutable resume version
  - project tags can be normalized without discarding the source excerpt that produced them

The current implementation already supports synchronous PDF parsing into `raw_text`. The next additive step should keep that behavior, then layer LLM-backed structured extraction behind a service boundary so prompt and provider changes do not leak into controllers.

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
- resume parsing failed
- resume structured extraction failed
- resume structured extraction returned invalid data
- question tree not found
- model answer not found
- learning material not found
- skill benchmark not available

## Backward Compatibility Rules
- existing REST paths remain valid
- existing DTO fields keep their current names
- newly added intelligence features should be exposed through additive DTO fields or new endpoints
- current `question`, `answer`, `review`, and `home` payloads must remain usable by existing clients
