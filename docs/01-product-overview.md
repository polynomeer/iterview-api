# 01-product-overview

## Product Summary
This project is an interview preparation backend for experienced software engineers.

The updated product direction is:
- resume-driven interview preparation
- answer-driven learning and retry loops
- skill radar and gap analysis
- question tree and follow-up visualization
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
-> Parse + Extract Signals
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
- derive structured resume signals from a selected resume version after parsing completes
- surface high-risk resume claims that likely trigger follow-up questions
- keep version-specific extraction results so older resume snapshots remain queryable
- support resume-based question recommendation without breaking the existing catalog flow

### 2. Structured Question Learning
- keep the current global question catalog
- add follow-up relationships so a question can belong to a tree or graph
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
- PDF-native resume upload and parser integration for version creation
- version processing status and failure visibility for resume parsing
- resume extraction snapshots for skills, experiences, and resume risks
- question relationship modeling for follow-up trees
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
- user progress is cached aggregate state per user-question pair
- retry scheduling is persisted, not recomputed ad hoc on every read
- archived questions must stay out of the active retry loop unless explicitly reset
- new intelligence features should reuse current progress, answer, and resume records where possible

## User Value
The updated product should help a user answer three questions every time they open the app:
- What should I practice today?
- Where am I weak relative to my resume and target role?
- Which follow-up questions am I still not ready to defend?
