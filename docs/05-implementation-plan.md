# 05-implementation-plan

## Goal
Extend the existing backend toward the new product direction without breaking the current profile, resume, question, answer, review, home, and feed flows.

## Phase 0 - Current Baseline
Already implemented:
1. authentication and current-user APIs
2. profile, settings, and target companies
3. resume container and resume version management
4. question catalog and question detail
5. answer submission, scoring, and feedback persistence
6. user-question progress updates
7. retry scheduling, archive flow, daily cards, and feed
8. OpenAPI, CORS, and API integration coverage

This baseline should be preserved.

## Phase 1 - Resume Intelligence on Top of Resume Versions
1. formalize `resume_version_id` as the anchor for uploaded PDF files and derived resume signals
2. add Flyway migrations for:
   - `resume_profile_snapshots`
   - `resume_contact_points`
   - `resume_competency_items`
   - `resume_skill_snapshots`
   - `resume_experience_snapshots`
   - `resume_project_snapshots`
   - `resume_project_tags`
   - `resume_achievement_items`
   - `resume_education_items`
   - `resume_certification_items`
   - `resume_award_items`
   - `resume_risk_items`
3. add backward-compatible metadata columns needed for parser lifecycle, such as file metadata and parse status
4. implement multipart PDF upload support for creating a new immutable version
5. expose read APIs for skills, experiences, and risks by resume version
6. keep `POST /api/resumes/{resumeId}/versions` backward compatible for import and test flows
7. use `parsed_json` as an interim import source if needed
8. add a parser service boundary so real PDF extraction can replace placeholder logic later without controller changes
9. add an LLM extraction service boundary that accepts `raw_text` and returns normalized resume fields
10. validate and map LLM output into domain-aligned skill, experience, project, and risk snapshots
11. persist prompt and model metadata so extraction quality can be audited without mutating the version record
12. preserve the original resume section grouping so profile, credentials, and timeline data remain explainable to the frontend

Acceptance intent:
- existing resume APIs still work unchanged
- PDF upload creates a new immutable version even if parsing completes later
- new resume intelligence APIs are additive
- LLM-backed structured extraction is layered on top of raw parsing rather than replacing it

## Phase 1A - LLM Resume Structuring
1. define a provider-agnostic extraction interface in the `resume` domain
2. pass `raw_text`, role context, and optional target company context to the extraction boundary
3. require the extraction result to return:
   - profile headline and summary
   - typed contact points and external links
   - competency statements
   - normalized skills
   - normalized experiences
   - projects and quantified achievements
   - project-level content, tags, and category classification
   - education, awards, and certifications
   - risk items
   - confidence metadata
   - source text references
4. reject or down-rank malformed extraction output before persistence
5. keep retry and re-extract behavior idempotent per resume version

Acceptance intent:
- the system can preserve raw text even when structured extraction fails
- extraction retries do not create duplicate version rows
- extraction metadata is traceable for later prompt tuning
- project lists extracted from one version remain queryable with their own content, tags, and category metadata

## Phase 1B - Job-Aware Resume Analysis
1. add Flyway migration for:
   - `job_postings`
   - `resume_analyses`
   - `resume_analysis_suggestions`
2. add a job-posting parsing boundary that accepts text or link-style input and returns:
   - requirements
   - nice-to-have items
   - keywords
   - responsibilities
   - inferred company and role metadata
3. persist one analysis run per `resumeVersionId` with optional `jobPostingId`
4. generate deterministic analysis output first:
   - match score
   - strong matches
   - missing keywords
   - weak signals
   - recommended focus areas
   - suggested headline
   - suggested summary
   - recommended format type
5. persist section-level rewrite suggestions separately from the analysis header row
6. expose additive APIs for:
   - `POST /api/job-postings`
   - `GET /api/job-postings`
   - `GET /api/job-postings/{jobPostingId}`
   - `POST /api/resume-versions/{versionId}/analyses`
   - `GET /api/resume-versions/{versionId}/analyses`
   - `GET /api/resume-versions/{versionId}/analyses/{analysisId}`
   - `PATCH /api/resume-versions/{versionId}/analyses/{analysisId}/suggestions/{suggestionId}`
   - `POST /api/resume-versions/{versionId}/analyses/{analysisId}/exports`
   - `GET /api/resume-versions/{versionId}/analyses/{analysisId}/exports`
   - `GET /api/resume-versions/{versionId}/analyses/{analysisId}/exports/{exportId}/file`
7. add link fetch support for `job_postings` and persist fetch metadata
8. persist one tailored document view per analysis and use it as the single source for preview/export
9. add OpenAI-backed rewrite generation with deterministic fallback
10. add one resume-question heatmap read model that aggregates practical interview question pressure back onto parsed resume anchors
11. add one manual remap API for correcting question-to-resume anchor linkage without mutating imported practical interview rows

Acceptance intent:
- immutable resume versions stay unchanged while analyses are stored separately
- one saved job posting can drive multiple analyses across different resume versions
- suggestion acceptance is persisted without overwriting source resume content
- tailored document preview and PDF export reuse the same persisted normalized output

## Phase 1C - Resume Interview Heatmap
1. add Flyway migration for:
   - `resume_question_heatmap_links`
2. aggregate practical interview questions by parsed resume anchor such as:
   - project
   - experience
   - skill
   - competency
   - summary
3. expose additive APIs for:
   - `GET /api/resume-versions/{versionId}/question-heatmap`
   - `POST /api/resume-versions/{versionId}/question-heatmap/links`
   - `PATCH /api/resume-versions/{versionId}/question-heatmap/links/{linkId}`
4. support scope filters such as:
   - `all`
   - `main`
   - `follow_up`
5. score each anchor using:
   - direct question count
   - follow-up count
   - pressure-question count
   - distinct interview count
   - weak-answer count
6. allow manual override links to correct bad inferred mappings from imported practical interviews

Acceptance intent:
- the heatmap is additive and does not rewrite immutable resume snapshots
- manual remaps do not mutate imported practical interview question text
- practical interview review and resume review can share the same parsed anchor ids

## Phase 1D - Sentence-Level Resume Overlay
1. introduce parsed resume overlay targets inside each anchor block
2. support mixed overlay layers:
   - `block` for whole-project or whole-experience questions
   - `sentence` for precise sentence-triggered questions
   - optional `phrase` or `keyword` later
3. add sentence segmentation for parsed resume fields such as:
   - `project.summaryText`
   - `project.contentText`
   - `experience.summaryText`
   - `experience.impactText`
   - `profile.summaryText`
4. persist text-range aware question links separately from current anchor-level links
5. expose additive read data that lets the frontend:
   - tint the whole anchor block
   - hover one sentence and preview linked question cards
   - distinguish project-wide questions from sentence-specific questions
6. keep anchor-level heat summary as the stable first-layer model

Acceptance intent:
- whole-project questions and sentence-specific questions can coexist in one resume viewer
- hoverable sentence overlays do not require raw PDF coordinate extraction
- manual correction can eventually happen at sentence-range level without mutating source resume text

Current step status:
- implemented:
  - `resume_document_overlay_targets` persistence
  - block and sentence target generation during resume extraction and re-extraction
- next:
  - public read API for overlay targets
  - question-to-overlay linking
  - additive `overlayTargets` on heatmap reads

## Phase 2 - Question Tree and Follow-Up Relationships
1. add Flyway migration for `question_relationships`
2. seed follow-up relationships for high-value core questions
3. implement `GET /api/questions/{questionId}/tree`
4. derive node status from current user progress and answer history
5. add question reference-content schema for:
   - `question_reference_answers`
   - richer learning-material metadata or question-specific ordering
6. expose read APIs for model answers and curated learning materials
7. keep `GET /api/questions/{questionId}` as the stable base detail API

Acceptance intent:
- current question list/detail clients do not break
- question tree is a separate read model
- model answers remain separate from user-submitted `answer_attempts`

## Phase 3 - Richer Answer Analysis and Review Signals
1. add Flyway migration for `answer_analyses`
2. persist richer dimensions after answer submission or in a follow-up analysis step
3. connect review prioritization to:
   - low total score
   - repeated weakness
   - resume risk linkage
   - follow-up depth gaps
4. keep `answer_scores` as the backward-compatible scoring contract
5. extend answer detail with optional analysis fields only when the API change is ready

Acceptance intent:
- existing answer submission behavior remains intact
- review queue becomes more informative without changing its core lifecycle

## Phase 4 - Skill Radar and Gap Analysis
1. add Flyway migrations for:
   - `skill_category_scores`
   - `career_benchmarks`
   - optionally `question_skill_mappings`
2. introduce `skill` domain package with service, repository, dto, and controller layers
3. implement:
   - `GET /api/skills/radar`
   - `GET /api/skills/gaps`
   - optional `GET /api/skills/progress`
4. calculate category scores from existing answer, progress, and analysis data
5. compare against job role and experience-based benchmarks

Acceptance intent:
- radar and gap analysis are grounded in persisted answer behavior, not mock-only numbers

## Phase 5 - Home Dashboard Evolution
1. keep the current `GET /api/home` structure as the base contract
2. add optional fields for:
   - skill radar preview
   - weak-skill highlights
   - resume risk preview
3. adjust daily recommendation logic to prefer:
   - pending retry items
   - high-gap skill categories
   - high-risk resume defense questions
4. preserve the current daily card and retry preview behavior

Acceptance intent:
- old clients can ignore the new fields
- new clients can render the updated learning dashboard

## Phase 5A - Interview History and Archive Source Metadata
1. extend interview session APIs with session-history reads
2. require or resolve one explicit `resumeVersionId` at interview start for `resume_mock`
3. generate the opening interview question from the selected resume version through an interview-specific LLM boundary
4. persist enriched session question snapshots for opening questions and follow-up questions that do not exist in the global catalog
5. add an interview-specific LLM generation boundary for answer-driven resume-grounded follow-up prompts
6. validate generated question payloads before inserting them into an active session
7. persist generation metadata (`generationStatus`, rationale, model, prompt version) with each inserted opening or follow-up question
8. keep deterministic fallback behavior when the LLM is disabled or returns unusable output
9. add `resumeEvidence` snapshot support so each opener or follow-up can include one or more short resume excerpts that justify why the question was asked
10. store evidence as compact snippet metadata rather than recomputing it from raw resume text at read time
11. add question-level archive source metadata so archived questions can distinguish:
   - `practice`
   - `interview`
12. keep archive question-level and avoid replacing it with a session-only archive
13. ensure every asked interview turn is eligible for archive persistence
14. ensure follow-up interview turns can be revisited from both interview history and archive
15. introduce additive `interviewMode` planning so `quick_screen`, timed mocks, `free_interview`, and `full_coverage` can share the same base session model
16. for `full_coverage`, create a session-scoped evidence inventory and use a coverage planner to choose the next evidence target before asking the next question
17. add a structured full-coverage result view contract so parsed resume experiences and projects can be highlighted and mapped back to related interview turns
17. persist question-to-evidence links so the final result screen can map resume evidence items back to related session questions

Acceptance intent:
- one interview session appears once in interview history
- each question and follow-up from that session can still appear as a question-level archived item
- archive source metadata is additive and backward compatible
- resume-selected interview sessions stay pinned to the chosen resume version for their full lifetime
- question cards can later show a `Based on your resume` block without needing to recalculate evidence from the current resume state
- `full_coverage` can measure completion against resume evidence units rather than loosely estimated topic breadth
- the first result-time resume map should reuse parsed resume section APIs plus `resume-map` joins instead of requiring PDF coordinate extraction

## Phase 6 - Seed and Test Hardening
1. extend seed/reference data for:
   - skill categories
   - benchmarks
   - follow-up question relationships
   - model answers
   - curated learning materials
2. add unit tests for:
   - skill score calculation
   - gap ranking
   - question tree node status mapping
   - review priority computation
3. add repository integration tests for new query-heavy paths
4. add API integration tests for new endpoints
5. add contract coverage for interview snapshot evidence serialization and empty-evidence fallback behavior

## Phase 7 - Localization and Bilingual Delivery
1. add `preferred_language` support to user settings and current-user reads
2. add locale resolution that prefers explicit request locale over stored user preference and falls back to `ko`
3. introduce translation storage for static/reference data such as:
   - categories
   - skills
   - tags
   - question catalog text
   - learning material titles and descriptions
4. keep user-authored and uploaded source content stored only in the original language
5. add `content_locale` metadata to AI-generated text such as interview openers, follow-ups, and analysis summaries
6. localize error messages and human-readable labels without changing machine-readable codes or enums
7. keep mixed-language rendering safe so original resume evidence and answer content can remain in the source language while surrounding UI and generated text use the selected locale

Acceptance intent:
- user original content is never replaced by translated persistence
- UI-facing labels and system-generated text can switch between Korean and English
- static/reference data can be served in the selected locale with deterministic fallback

## Sequencing Rules
- do not bundle schema, API, and UI assumptions into one oversized change
- commit after each completed vertical slice
- every migration set should have corresponding repository or API coverage

## Planned Full-Scope Practical Interview Work
18. add `interview_records`, transcript segments, structured question/answer rows, and interviewer-profile storage without collapsing them into the ordinary answer-attempt flow
19. add the staged processing pipeline for audio upload, transcription, cleanup, structuring, resume/JD linkage, and interviewer-profile extraction
20. add transcript review and correction APIs so raw, cleaned, and confirmed transcript layers remain distinct
21. add real-interview analysis reads for question flow, topic distribution, weak-answer zones, and interviewer-style summaries
22. extend the interview session domain with `replay_mock` so imported real interviews can launch dynamic replay simulations through the existing session engine
23. extend archive source metadata so imported real-interview questions and replay-simulation questions remain distinguishable from practice and ordinary mock interviews
24. add frontend-facing contracts for real-interview detail, transcript review, interviewer profile, and replay simulation start flows
- keep Swagger/OpenAPI docs in sync with each new endpoint

## Explicit Deferred Work
These should not be implemented unless requested:
1. live or streaming mock interview sessions beyond the current additive session CRUD/progression flow
2. voice transcription pipeline
3. streaming AI interactions
4. public sharing or comparison features
5. admin tooling
