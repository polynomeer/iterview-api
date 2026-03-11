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
- extracted resume skills can be retrieved for a specific resume version
- extracted resume experiences can be retrieved for a specific resume version
- resume risk items can be retrieved for a specific resume version
- existing answer attempts remain tied to the resume version that was active or selected at submission time

## Question Tree and Follow-Up
- a question can expose follow-up nodes without changing the base question record contract
- question tree responses show stable ordering for child nodes
- node state can distinguish unanswered, answered, weak, and strong
- question tree data does not break existing question detail consumers

## Answer Submission and Analysis
- user can submit an answer attempt
- `attempt_no` increments correctly per user-question
- answer score row is created
- feedback items are created
- progress row is inserted or updated
- retry scheduling occurs when score or answer mode requires it
- richer answer analysis, when introduced, is persisted separately from the current score row
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

## Data and Schema Quality
- every schema change uses Flyway
- all table and column names remain snake_case
- DTOs stay separate from entities
- answer attempts remain immutable after submission
- resume versions remain immutable records
- user-question progress remains the cached aggregate for per-question learning state

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
