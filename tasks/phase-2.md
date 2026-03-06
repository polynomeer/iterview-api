# phase-2

## Goal
Implement the core answer loop and read APIs.

## Tasks
1. implement profile and settings endpoints
2. implement resume and resume version endpoints
3. implement question list and detail endpoints
4. implement answer submission endpoint
5. implement scoring service
6. implement feedback persistence
7. implement user_question_progress update logic
8. implement review_queue scheduling logic
9. implement home, archive, and feed endpoints
10. add tests for scoring, retry, and archive behavior

## Constraints
- keep scoring logic in a dedicated service
- use transactions for answer submission flow
- do not implement post-MVP features

## Done When
- core endpoints match docs/04-api-contracts.md
- acceptance criteria in docs/06-acceptance-criteria.md are satisfied
- tests pass
