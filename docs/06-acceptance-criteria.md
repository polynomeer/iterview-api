# 06-acceptance-criteria

## Baseline Preservation
- existing authentication endpoints continue to work
- existing profile, settings, and target company endpoints continue to work
- existing resume list/create/version/activate endpoints continue to work
- existing question list/detail endpoints continue to work
- existing answer submission, answer history, and answer detail endpoints continue to work
- existing review queue, archive, home, and feed endpoints continue to work

## Resume Intelligence
- resume versions remain immutable after creation
- user can upload a PDF and receive a new immutable resume version
- the system exposes whether a resume version is pending, completed, or failed for parsing
- raw PDF parsing can complete successfully even if structured extraction later fails
- the system can persist top-level resume summary and contact/link information for a specific resume version
- the system can persist work timeline, project, education, award, and certification data for a specific resume version
- the system can persist project title, detailed content, tags, and category metadata for a specific resume version
- quantified achievement claims remain attributable to the source text of one resume version
- extracted resume skills can be retrieved for a specific resume version
- extracted resume experiences can be retrieved for a specific resume version
- resume risk items can be retrieved for a specific resume version
- parse failure for a new version does not corrupt or overwrite prior versions
- extracted resume data for one uploaded PDF does not leak into another version
- existing answer attempts remain tied to the resume version that was active or selected at submission time
- when LLM extraction is introduced, the system stores enough metadata to understand which model and prompt version produced the mapped resume fields
- malformed or low-confidence LLM extraction output does not silently overwrite already persisted snapshots without validation rules
- resumes that contain sectioned content such as competencies, awards, certifications, and projects do not lose those sections by being flattened into only skills and risks
- project tags and project categories can be added without breaking existing project reads
- uploaded resume source text remains stored and retrievable in the original language

## Question Tree and Follow-Up
- a question can expose follow-up nodes without changing the base question record contract
- question tree responses show stable ordering for child nodes
- node state can distinguish unanswered, answered, weak, and strong
- question tree data does not break existing question detail consumers
- model answers, when introduced, are retrievable separately from user answer attempts
- related learning materials can be retrieved in stable display order for a question
- model answers and learning materials remain global reference content shared across users

## Answer Submission and Analysis
- user can submit an answer attempt
- `attempt_no` increments correctly per user-question
- answer score row is created
- feedback items are created
- progress row is inserted or updated
- retry scheduling occurs when score or answer mode requires it
- richer answer analysis, when introduced, is persisted separately from the current score row
- saved job postings can be created from text or link-style input without mutating resume records
- one saved job posting can expose parsed requirements, responsibilities, and keywords
- link-based job postings can fetch and persist readable source text plus fetch metadata
- one resume version can have multiple persisted analysis runs without losing immutable version history
- a resume analysis can persist:
  - overall match score
  - strong matches
  - missing keywords
  - weak signals
  - recommended focus areas
  - suggested headline and summary
  - recommended format type
- a resume analysis can persist section-level rewrite suggestions separately from the main analysis row
- accepting one suggestion must not rewrite the source `resume_versions` record or extracted resume snapshot tables
- each analysis can persist one tailored document view with stable section order and preview-ready section content
- the backend can generate and persist PDF export history for one tailored analysis
- one resume version can expose a practical-interview heatmap grouped by parsed resume anchors
- the heatmap can distinguish direct question volume, follow-up density, pressure questions, and weak-answer hotspots
- a practical interview question can be manually remapped to another parsed resume anchor without mutating the original imported question row
- heatmap filtering can distinguish all questions, main questions, and follow-up questions
- the anchor-level heatmap expands into sentence-level overlays without breaking the existing summary contract
- the future resume viewer should be able to show both:
  - whole-project or whole-experience question coverage
  - sentence-specific hover cards for precise question triggers
- resume extraction persists block and sentence overlay targets without requiring raw PDF coordinate extraction
- the backend exposes both nested `overlayTargets` and a flattened overlay-target read for hover-oriented resume viewers
- project-wide questions can remain linked to a whole-anchor `block` target while sentence-specific questions attach to one `sentence` target
- the same anchor can now expose finer `phrase` and `keyword` overlay targets without breaking the anchor-level summary contract
- manual remap can override both the anchor and the exact overlay target inside that anchor
- additive heatmap filters can narrow reads by:
  - weak-only questions
  - company name
  - interview date window
- additive heatmap reads can also be narrowed by routed overlay target type:
  - `block`
  - `sentence`
  - `phrase`
  - `keyword`
- the backend returns a precomputed filter summary so the frontend can render company/date/type chips without rebuilding them from raw question rows
- one resume version can expose a lazy-initialized editor workspace without mutating the immutable source version
- the editor workspace can persist block-based document structure plus markdown-compatible source
- comment threads can target one block or selection range and move between `open` and `resolved`
- question cards can target one block or selection range and move between `active` and `archived`
- deterministic auto-question suggestions can be generated for a selected block or sentence without being auto-persisted
- deterministic rewrite suggestions can be generated for a selected block or sentence without mutating stored source content
- scoring rules remain centralized in one service

## Review Queue and Learning Loop
- low-quality answers create or update a pending retry item
- archived questions do not produce active retry items
- review queue actions keep valid state transitions
- review prioritization can be extended with resume risk and skill gap inputs without breaking existing queue semantics
- the home flow still prioritizes pending retry items before general recommendation

## Skill Radar and Gap Analysis
- skill radar scores are derived from persisted answer and progress data
- benchmark comparison is based on job role and experience context
- gap analysis can identify weak categories in a stable, ranked form
- users can retrieve current radar and gap data without affecting existing home or question APIs

## Home Dashboard
- home endpoint returns at least one daily question when available
- retry questions remain separate from the primary daily question
- summary stats remain included
- optional new fields for weak skills, radar preview, or resume risks are backward compatible

## Interview Sessions
- users can create a session without bypassing the existing question-answer-review flow
- `resume_mock` interview creation can require explicit resume-version selection without breaking other session types
- session questions keep stable ordering and expose current, queued, and answered states
- interview history is visible at the session level
- interview history can show which resume version grounded the session
- follow-up questions remain attributable to the parent session question
- the opening question for a resume-based interview can be AI-generated from the selected resume version and still be stored as a stable session snapshot
- session answer submission reuses standard answer scoring, feedback persistence, and retry scheduling
- a session can complete without mutating historical answer attempts
- minimal session support remains additive and does not imply live or realtime interview behavior
- archive remains question-level even for interview-originated questions
- archive items can distinguish `practice` and `interview` origin through additive metadata
- every asked interview question and follow-up can later appear as a question-level archive item linked back to the parent session
- `resume_mock` can insert AI-generated follow-up questions without mutating prior session questions
- generated follow-up snapshots preserve prompt text, optional body text, focus skills, resume context summary, and generation rationale
- generated opener and follow-up snapshots can also preserve one or more compact `resumeEvidence` snippets that explain why the question was asked
- each evidence item can identify the resume section or parsed record type it came from, such as project, experience, award, certification, or education
- evidence snippets remain short supporting context and do not replace the main prompt text
- missing or empty `resumeEvidence` must not break session rendering or session persistence
- generated follow-up prompts can use the immediately preceding answer as grounding input
- invalid or empty LLM follow-up output does not break the session; the service falls back safely
- additive interview modes can coexist on the same session domain without breaking current `resume_mock`, `review_mock`, or `topic_mock` behavior
- `full_coverage` can report coverage against session-scoped resume evidence items
- a full-coverage result can map resume evidence items back to related asked questions for hover and click interactions on the result screen
- a completed full-coverage result can render a structured resume viewer where experience or project blocks are visually highlighted by `coverageStatus`
- hovering one highlighted resume block can reveal one or more related interview turns without losing the current result context
- clicking one related interview turn can focus or scroll to the linked session question card
- AI-generated interview and analysis text can be produced in the selected system language while original resume evidence remains in the source language
- a practical interview upload can preserve raw transcript, cleaned transcript, and user-confirmed transcript without overwriting earlier processing stages
- a practical interview record can expose structured question, answer, and follow-up-edge data that remain queryable independently of replay simulation
- imported real-interview questions can become archive-visible question assets with additive source metadata such as `real_interview`
- `replay_mock` sessions can preserve one imported interviewer profile while still generating dynamic answer-dependent follow-up questions

## Data and Schema Quality
- every schema change uses Flyway
- all table and column names remain snake_case
- DTOs stay separate from entities
- answer attempts remain immutable after submission
- resume versions remain immutable records
- user-question progress remains the cached aggregate for per-question learning state
- curated model answers do not reuse `answer_attempts` storage
- LLM extraction metadata is stored separately from user-authored resume content where possible
- localization metadata is stored separately from original user-authored content

## Localization
- the product supports at least Korean and English modes
- user-authored or uploaded source content remains persisted in the original language
- UI language can switch independently of the original language of resume or answer content
- system-generated text can be generated and stored in the selected locale
- static and reference data can be served in the selected locale with fallback
- machine-readable fields remain stable across locales
- localized error messages do not change stable error codes

## Test Coverage
Minimum required tests across the evolving product:
- scoring service unit tests
- retry scheduling unit tests
- archive decision unit tests
- skill score and gap calculation unit tests when that domain is introduced
- repository integration tests for critical queries
- controller/API tests for core flows and new additive endpoints

## Definition of Done
A documentation or implementation slice is complete only if:
- the product language is consistent with the current codebase
- backward compatibility expectations are explicit
- API contracts match implemented behavior for current endpoints
- new planned endpoints are clearly labeled as not yet implemented
- code builds
- relevant tests pass
