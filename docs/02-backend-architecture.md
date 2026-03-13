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
- `interview`

Planned additive domain:
- `skill`

The new product direction should fit into this structure instead of introducing a parallel architecture.

## Domain Responsibilities
### `user`
- current user profile and settings
- target company priorities
- role and experience context used by question relevance and benchmark comparisons
- preferred language selection used for localized responses and system-generated text

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
- archive read models should remain question-level even when the source is an interview turn

### `dailycard`
- daily question generation
- home aggregation
- future readiness, weak-skill, and resume-risk previews

### `feed`
- popular, trending, and company-related catalog slices

### `interview`
- interview session lifecycle
- interview start context selection, especially resume-version selection for `resume_mock`
- session-scoped question progression
- AI-generated opening interview question generation from resume evidence
- follow-up question generation and storage
- session history reads
- linkage from interview turns into archive, answer, and review flows

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
- locale resolution and message source configuration

### `common.exception`
- global exception translation
- domain validation helpers

### `common.service`
- cross-domain infrastructure services such as clock and current-user access
- locale resolution helpers and translation fallback helpers

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
- store `preferred_language` for authenticated users
- resolve the effective locale in this order:
  1. explicit request locale
  2. stored user preference
  3. `Accept-Language`
  4. default `ko`

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
- preserve uploaded or user-authored resume source content in the original language
- do not replace original user text with translated text in persistent storage

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

### Interview Sessions
- create interview session from active resume context, topic context, or review context
- for `resume_mock`, require or resolve one explicit `resumeVersionId` before creating the first session question
- support an additive `interview_mode` inside the interview domain so session depth and planning rules can vary without replacing the base session model
- persist session-level summary and status
- persist session question snapshots for main questions and follow-up questions
- persist opening-question generation metadata so the system can distinguish catalog-seeded openings from AI-generated resume-specific openings
- submit session answers through the same answer scoring pipeline
- expose interview history separately from archive
- mark archive-visible source metadata so archived questions can still show whether they came from practice or interview
- archive every interview turn, including follow-ups, as a question-level record linked back to the parent interview session
- for `resume_mock`, generate follow-up prompts through an interview-specific LLM service boundary when configured
- use the selected resume version plus the immediately preceding answer as the primary grounding input for follow-up generation
- persist generation metadata on session question snapshots so seeded questions, catalog follow-ups, fallback follow-ups, and AI-generated follow-ups remain distinguishable
- persist compact `resumeEvidence` snippets on session-question snapshots so question cards can explain which resume sentence, project, or credential triggered the prompt
- persist the locale used to generate AI opener, follow-up, and analysis text so mixed-language history stays traceable
- for `full_coverage`, create a session-scoped inventory of resume evidence units before asking questions
- use a coverage planner to choose the next evidence unit first, then let the LLM phrase the actual question against that evidence
- persist question-to-evidence links so the result screen can map resume evidence items back to asked questions
- expose a result-oriented resume map so hover and click interactions can reveal related questions for one resume sentence or structured record
- keep a deterministic non-LLM fallback path so local development and non-AI environments remain usable

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
- interview answer submission should remain transactional with session-question progression and answer persistence
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
- static/reference-data translation backfill or refresh jobs

## Localization Architecture
### Supported Locales
- initial supported locales are `ko` and `en`
- unsupported locales fall back deterministically to `ko`

### Data Classification Rules
- user-originated source data:
  - uploaded resumes
  - answer content
  - file names
  - manually entered original text
  must remain stored and returned in the original language
- system-generated text:
  - AI interview questions
  - AI follow-ups
  - AI answer analysis
  - system feedback summaries
  should be generated and stored with an explicit `content_locale`
- static/reference data:
  - categories
  - skills
  - tags
  - question catalog text
  - learning material display text
  should be served through locale-aware translation storage with fallback

### Storage Direction
- prefer translation tables or translation subrecords for static/reference entities rather than overwriting canonical rows
- keep machine identifiers stable across locales:
  - codes
  - enums
  - status values
  - source types
- expose localized human-readable labels separately from machine-readable fields when needed

### API Semantics
- API responses should keep machine fields locale-neutral and localize only display text
- error payloads should keep stable `code` values while localizing `message`
- original user text returned by resume, answer, or archive APIs should remain in the original language

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

Recommended interview pipeline:
1. fetch candidate resume options and let the user choose one explicit `resumeVersionId`
2. create `interview_sessions` row with `session_type = resume_mock` and the selected `resumeVersionId`
3. call an interview-specific LLM boundary to generate the opening question from resume evidence
4. persist the opening question as an `interview_session_questions` snapshot even if it does not map to the global catalog
5. accept the user's answer through the normal answer pipeline
6. call the interview follow-up generator with:
   - selected resume version context
   - prior session question snapshot
   - submitted answer content
   - current follow-up depth and session state
7. persist each follow-up snapshot and link it to its parent with `parent_session_question_id`
8. write every asked session question into archive-oriented progress metadata so archive remains question-level

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
