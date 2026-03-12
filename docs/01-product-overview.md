# 01-product-overview

## Product Summary
This project is an interview preparation backend for experienced software engineers.

The updated product direction is:
- resume-driven interview preparation
- answer-driven learning and retry loops
- skill radar and gap analysis
- question tree and follow-up visualization
- curated model answers and related learning materials
- personalized daily practice grounded in the user's role, target companies, and resume history

The current implementation already provides the learning backbone:
- user profile and settings
- target companies
- resume containers and immutable resume versions
- question catalog and question detail
- answer submission, scoring, and feedback persistence
- user-question progress aggregation
- retry queue, archive flow, daily card generation, and feed data

The next product evolution should extend those capabilities rather than replace them.

## Core Product Loop
The intended learning loop for this system is:

```text
Resume PDF Upload
-> Resume Version
-> Raw Text Parse
-> LLM Structured Extraction
-> Extract + Validate Signals
-> Question Selection
-> Answer Submission
-> Score + Feedback
-> Progress + Review Queue
-> Skill Radar + Gap Analysis
-> Next Recommended Question
```

This is an additive evolution of the current backend, not a new product line.

## Product Pillars
### 1. Resume Intelligence
- accept resume PDF uploads and persist them as immutable resume versions
- derive raw text from the uploaded PDF and preserve that extraction on the immutable version
- use an LLM-backed extraction step to map raw resume text into normalized skills, experiences, and risk signals
- validate and persist structured resume signals after the LLM extraction step completes
- persist richer resume structure beyond skills and risks, including:
  - profile headline and summary
  - contact channels and public links
  - core competency statements
  - work experience timeline
  - project and initiative records
  - education history
  - awards and certifications
  - quantified achievement claims
- surface high-risk resume claims that likely trigger follow-up questions
- keep version-specific extraction results so older resume snapshots remain queryable
- support resume-based question recommendation without breaking the existing catalog flow

### 2. Structured Question Learning
- keep the current global question catalog
- add follow-up relationships so a question can belong to a tree or graph
- attach curated model answers that show strong answer structure without mixing them into user attempts
- attach question-linked learning materials that explain concepts, tradeoffs, and background knowledge
- let users understand both breadth and depth of their preparation

### 3. Answer Analysis and Review
- keep answer attempts append-only
- keep scoring centralized in one service
- enrich analysis output so weak patterns, follow-up readiness, and skill gaps can be derived from answer history
- preserve current retry scheduling and archive semantics

### 4. Skill and Readiness Insights
- calculate skill-category performance from answer history
- compare user progress against role and career-stage benchmarks
- feed those insights back into home recommendations, review queue prioritization, and resume defense preparation

## Current Scope vs Extension Scope
### Implemented Today
- authentication and current-user profile APIs
- resume list, create, version upload, and activation APIs
- question list and detail APIs
- answer submission, answer history, and answer detail APIs
- review queue, archive, home, and feed APIs
- scoring, retry scheduling, and archive decisions

### Planned Additive Extensions
- LLM-backed structured extraction from parsed resume raw text
- extraction confidence, traceability, and failure visibility for resume signal mapping
- richer resume extraction snapshots for skills, experiences, and resume risks
- richer structured resume sections for profile, contacts, education, awards, certifications, and project-level achievements
- question relationship modeling for follow-up trees
- question-linked model answers and richer learning material metadata
- richer answer analysis beyond the current score + feedback rows
- skill radar, gap analysis, and benchmark APIs
- stronger home dashboard summaries tied to readiness and risk

### Explicitly Out of Scope Unless Requested
- mock interview sessions
- live voice or streaming interview features
- public answer publishing or comparison
- GitHub sync
- community or lounge features
- admin moderation tooling

## Product Rules
- questions are global shared assets
- answer attempts remain immutable after submission
- resume versions remain immutable historical records
- extracted resume skills, experiences, and risks are always scoped to one resume version
- raw text parsing and structured field extraction are separate pipeline stages and may complete independently
- structured resume sections should preserve the user document’s original grouping so career timeline and supporting credentials stay explainable
- user progress is cached aggregate state per user-question pair
- retry scheduling is persisted, not recomputed ad hoc on every read
- archived questions must stay out of the active retry loop unless explicitly reset
- new intelligence features should reuse current progress, answer, and resume records where possible
- model answers and learning materials are global reference content, not user-generated answer attempts

## User Value
The updated product should help a user answer three questions every time they open the app:
- What should I practice today?
- Where am I weak relative to my resume and target role?
- Which follow-up questions am I still not ready to defend?
