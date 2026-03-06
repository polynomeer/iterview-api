# AGENTS.md

## Repository Purpose
This repository contains the backend implementation for an interview training platform.

The MVP backend is responsible for:
- profile and settings
- target companies
- resume and resume versioning
- question catalog
- daily question selection
- answer submission
- answer scoring
- feedback persistence
- retry scheduling
- archive state
- basic feed

## Tech Stack
- Kotlin
- Spring Boot
- Gradle Kotlin DSL
- Spring Web
- Spring Data JPA
- Flyway
- PostgreSQL
- Bean Validation
- JUnit 5

## Architectural Style
Use package-by-domain.

Recommended package structure:

com.example.interviewplatform
- common
- user
- resume
- question
- answer
- review
- dailycard
- feed

Each domain package should contain:
- controller
- service
- repository
- entity
- dto
- mapper
- enum

## Hard Rules
- Keep business logic in services.
- Do not put business rules in controllers.
- Do not use Hibernate schema auto-generation as source of truth.
- Every schema change must be a Flyway migration.
- Use snake_case for table and column names.
- Use request/response DTOs separate from entities.
- Prefer explicit service methods over generic god services.
- Use constructor injection only.
- Keep answer attempts immutable after submission.
- Treat resume versions as immutable records.

## Domain Rules
- Questions are global assets shared by all users.
- User progress is specific to a user-question pair.
- The same question may have multiple answer attempts per user.
- A low-quality answer must create or update retry scheduling.
- Archived questions must not appear in active retry flow unless explicitly reset.
- Daily cards should prioritize pending retry items before general recommendation.
- User question progress is a cached aggregate and must be updated after answer submission.
- Scoring rules must be centralized in one service.

## Database Rules
- Use Flyway for all DDL.
- Add indexes for main read paths.
- Seed only reference data in initial seeds.
- Avoid soft delete unless clearly needed.
- Use created_at and updated_at consistently.

## Testing Rules
Minimum required tests:
- scoring service unit tests
- retry scheduling unit tests
- archive decision unit tests
- repository integration tests for critical queries
- controller/API tests for core flows

## Out of Scope
Do not implement in this repository unless explicitly requested:
- lounge community
- mock interview
- GitHub sync
- public answer comparison
- admin moderation tools

## Definition of Done
A task is complete only if:
- code builds
- tests pass
- Flyway migrations run
- API contracts match docs
- acceptance criteria are satisfied

## Git Commit Rules

Commit changes automatically at logical checkpoints.

A checkpoint means:
- one migration set is complete
- one API slice is complete
- one screen flow is complete
- one testable unit of work is complete

Before every commit:
1. run the relevant tests for the changed scope
2. ensure the project still builds
3. check git diff for unrelated changes
4. include only files related to the current task

Commit style:
- use Conventional Commits
- format: <type>(<scope>): <summary>

Allowed types:
- feat
- fix
- refactor
- test
- docs
- chore

Examples:
- feat(profile): add user profile update API
- feat(resume): add resume version activation flow
- feat(answer): persist answer attempts and score records
- fix(review): prevent archived questions from entering retry queue
- docs(api): update answer submission contract

Commit frequency:
- commit after each completed milestone
- do not bundle unrelated changes into one commit
- if a task is large, create intermediate checkpoint commits

After completing a task:
- create a commit automatically if git commit is permitted in the environment
- if commit is blocked by sandbox or approval policy, explicitly report that the code is ready and provide the exact commit message that should be used
