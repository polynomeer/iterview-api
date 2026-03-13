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

## Sequencing Rules
- do not bundle schema, API, and UI assumptions into one oversized change
- commit after each completed vertical slice
- every migration set should have corresponding repository or API coverage
- keep Swagger/OpenAPI docs in sync with each new endpoint

## Explicit Deferred Work
These should not be implemented unless requested:
1. live or streaming mock interview sessions beyond the current additive session CRUD/progression flow
2. voice transcription pipeline
3. streaming AI interactions
4. public sharing or comparison features
5. admin tooling
