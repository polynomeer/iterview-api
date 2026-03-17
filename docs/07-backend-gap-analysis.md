# 07-backend-gap-analysis

## Purpose
This document maps the rewritten product docs to the current backend implementation so feature work can proceed incrementally without rewriting the system.

## Current Backend Coverage
Implemented today:
- auth and current-user APIs
- profile, settings, and target companies
- resume containers and immutable resume versions
- question list and detail APIs
- answer submission, score persistence, feedback persistence, and answer history
- user-question progress aggregation
- retry queue, archive, home, and feed
- Flyway migrations, reference seeds, and API integration tests
- saved job-posting parsing APIs
- persisted resume analysis runs and suggestion acceptance APIs
- resume interview heatmap aggregation and manual remap APIs

This is the stable baseline and should remain operational throughout the extension work.

## Gap Summary
### Missing Domain Structures
- no persisted resume profile snapshot
- no persisted resume contact points
- no persisted resume competency section
- no persisted resume skill snapshots
- no persisted resume experience snapshots
- no persisted resume project snapshots
- no persisted project tag rows or project category metadata for resume-derived projects
- no persisted resume achievement items
- no persisted resume education items
- no persisted resume certification items
- no persisted resume award items
- no persisted resume risk items
- no question relationship table for follow-up trees
- no question-to-skill mapping table
- no `answer_analyses` table
- no `skill` domain package
- no persisted skill category scores
- no benchmark table
- no interview session tables

### Missing APIs
- no latest resume helper endpoint
- no resume intelligence read APIs for skills, experiences, and risks
- no question tree endpoint
- no recommended follow-up or resume-based question endpoints
- no answer analysis retrieval endpoint
- no skill radar, gaps, or progress endpoints
- no updated home fields for weak skills or resume risks
- no interview session APIs
- no interview history listing endpoint
- no archive source metadata that distinguishes practice questions from interview questions
- no interview-start contract that forces explicit resume-version selection for `resume_mock`
- no AI-generated opening-question contract for resume-based interviews
- no guarantee that every interview turn is archived as a question-level record

### Missing Seed Coverage
- current Flyway seed only covers companies, roles, categories, and tags
- current local dummy seed has sample users, resumes, questions, and review data
- there is no seed support for skill categories, benchmarks, question trees, resume risks, or follow-up examples

### Missing Calculation and Service Boundaries
- no resume parsing boundary beyond `parsed_json`
- no provider-backed structured extraction layer that can map raw resume text into stable experience, project, skill, and risk fields
- no answer deep-analysis boundary separate from `ScoringService`
- no skill score calculation service
- no benchmark comparison service
- no tree status derivation service
- no review priority service using confidence, resume risk, or age
- no locale resolution policy tied to user preference and request headers
- no translation storage for static/reference data
- no persisted locale metadata for AI-generated interview or analysis text

## Integration Choices
### Least-Disruptive Path
- keep current `resume`, `question`, `answer`, `review`, `dailycard`, and `feed` modules
- add a new `skill` module for radar and benchmarks
- extend `resume`, `question`, and `answer` with additive tables and endpoints
- keep current DTO shapes stable and add new endpoints for new feature areas

### Interview Session Scope Decision
The rewritten docs mark interview-session work as deferred, but the requested execution plan includes it. The least disruptive path is:
- implement a minimal additive interview session slice
- reuse current question and answer models where possible
- avoid streaming, realtime, or voice-specific design

## Implementation Checklist
### Phase 1 - Schema and Core Domain Additions
- add Flyway migration for:
  - `resume_skill_snapshots`
  - `resume_experience_snapshots`
  - `resume_risk_items`
  - `question_relationships`
  - `question_skill_mappings`
  - `answer_analyses`
  - `skill_category_scores`
  - `career_benchmarks`
  - `interview_sessions`
  - `interview_session_questions`
- add entities, repositories, and enums for those tables
- preserve current schema and constraints

### Phase 2 - Seed and Foundational Data
- extend reference seed for skill categories if modeled as reference data
- add realistic development seed data for:
  - follow-up question trees
  - question-skill mappings
  - benchmark rows
  - resume risk examples

### Phase 3 - Resume Intelligence
- add DTOs and endpoints for:
  - latest resume lookup
  - resume-version profile
  - resume-version contacts
  - resume-version competencies
  - resume-version skills
  - resume-version experiences
  - resume-version projects
  - resume-project tags and category metadata
  - resume-version achievements
  - resume-version education
  - resume-version certifications
  - resume-version awards
  - resume-version risks
- add a placeholder parsing/extraction boundary that can read `parsed_json` today
- add a provider-agnostic LLM extraction boundary after raw PDF parsing so mapping quality can improve without changing controllers
- keep resume versions immutable

### Phase 3A - Job-Aware Resume Analysis
- implemented:
  - `POST /api/job-postings`
  - `GET /api/job-postings`
  - `GET /api/job-postings/{jobPostingId}`
  - `POST /api/resume-versions/{versionId}/analyses`
  - `GET /api/resume-versions/{versionId}/analyses`
  - `GET /api/resume-versions/{versionId}/analyses/{analysisId}`
  - `PATCH /api/resume-versions/{versionId}/analyses/{analysisId}/suggestions/{suggestionId}`
- current gap:
  - no DOCX or HTML export format yet
  - no dedicated async export pipeline yet
  - no persisted full rewritten resume document body separate from suggestion rows

### Phase 3B - Resume Interview Heatmap
- implemented:
  - `GET /api/resume-versions/{versionId}/question-heatmap`
  - `GET /api/resume-versions/{versionId}/question-heatmap/overlay-targets`
  - `POST /api/resume-versions/{versionId}/question-heatmap/links`
  - `PATCH /api/resume-versions/{versionId}/question-heatmap/links/{linkId}`
  - internal `resume_document_overlay_targets` persistence for block, sentence, phrase, and keyword segmentation during resume extraction
  - additive nested `overlayTargets[]` on anchor-level heatmap reads
  - additive filter params on heatmap reads:
    - `weakOnly`
    - `companyName`
    - `interviewDateFrom`
    - `interviewDateTo`
    - `targetType`
  - manual heatmap links can now also pin one exact overlay target inside an anchor
  - additive precomputed filter summary on heatmap reads
- current gap:
  - no sentence-offset or PDF-coordinate overlay yet
  - no precomputed anchor summary table; aggregation is computed on read from practical interview data plus manual overrides

### Phase 4 - Question Tree and Recommendation
- implement tree loading from `question_relationships`
- derive node states from user progress and answer data
- add:
  - `GET /api/questions/{questionId}/tree`
  - `GET /api/questions/{questionId}/recommended-followups`
  - `GET /api/questions/resume-based`

### Phase 5 - Answer Analysis
- persist `answer_analyses` with deterministic fallback logic
- add retrieval endpoint for answer analysis
- connect answer analysis generation to answer submission
- keep existing `answer_scores` and feedback behavior

### Phase 6 - Skill Radar and Gaps
- add `skill` domain package
- implement skill score calculation from answer scores, answer analyses, question mappings, and progress
- implement benchmark lookup and gap ranking
- add:
  - `GET /api/skills/radar`
  - `GET /api/skills/gaps`
  - `GET /api/skills/progress`

### Phase 7 - Review and Home Evolution
- extend review priority logic with:
  - low score
  - answer analysis confidence
  - resume risk
  - age / overdue weight
- keep existing review queue APIs backward compatible
- extend `GET /api/home` with:
  - weak skill highlights
  - skill radar preview
  - resume risk preview

### Phase 8 - Interview Sessions
- implement minimal session create/read/progress flow
- add:
  - `GET /api/interview-sessions`
  - `POST /api/interview-sessions`
  - `GET /api/interview-sessions/{sessionId}`
  - `POST /api/interview-sessions/{sessionId}/answers`
  - `POST /api/interview-sessions/{sessionId}/next-question`
- extend `POST /api/interview-sessions` so `resume_mock` uses one explicit `resumeVersionId`
- generate both opener and follow-up questions from resume context through an interview LLM boundary with deterministic fallback
- persist `resumeEvidence` snippet metadata on generated session-question snapshots so the frontend can show why a question was asked
- keep evidence linked to parsed resume records such as project, experience, award, certification, or education when possible
- no planner-driven `full_coverage` mode yet
- no session-scoped evidence inventory or coverage state rows yet
- no practical-interview upload domain for raw audio, staged transcripts, structured real-interview questions, or interviewer-style extraction yet
- no `replay_mock` session type yet for running dynamic simulations from imported real interviews
- no archive source path yet for imported `real_interview` or `replay_simulation` question assets
- no resume-to-question result map endpoint yet
- extend archive responses with source metadata for practice vs interview origin
- keep answer attempts immutable and linked cleanly

### Phase 9 - Tests and Validation
- add unit tests for:
  - answer analysis fallback
  - node status mapping
  - skill score calculation
  - gap ranking
  - review priority calculation
- add integration tests for:
  - resume intelligence endpoints
  - question tree endpoint
  - skill radar endpoints
  - interview session endpoints

### Phase 10 - Localization
- add `preferred_language` to settings and current-user payloads
- introduce locale negotiation and fallback rules
- localize static/reference data through translation-aware storage
- keep user-authored content in the original language only
- add locale metadata for AI-generated text and localized error responses

## Risks to Manage
- avoid duplicating current scoring logic in new analysis code
- avoid breaking `GET /api/home` and current question/answer endpoints
- keep seed data realistic enough to support manual UI integration
- keep new APIs clearly additive instead of mutating existing response contracts
- avoid translating user source content in a way that would overwrite or obscure the original document or answer text
