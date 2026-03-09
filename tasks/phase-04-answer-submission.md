Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Implement the answer submission flow for iterview-api.

Scope:
- POST /api/questions/{questionId}/answers
- GET /api/questions/{questionId}/answers
- GET /api/answer-attempts/{id}

Requirements:
- create answer_attempts records
- create answer_scores records
- create answer_feedback_items records
- update user_question_progress
- create review_queue entries when retry is needed
- increment attempt_no correctly per user and question
- use placeholder scoring logic for now, but isolate it in a dedicated scoring service
- scoring placeholder must return:
  - totalScore
  - structureScore
  - specificityScore
  - technicalAccuracyScore
  - roleFitScore
  - companyFitScore
  - communicationScore
  - evaluationResult
- map evaluation result to progress status and review scheduling
- keep retry scheduling logic in a dedicated service
- add tests for:
  - attempt creation
  - progress update
  - retry queue creation
  - archive decision path
  - attempt number increment

Out of scope:
- voice upload
- transcript handling
- real AI scoring integration
- public answer sharing
- community features

When finished:
1. summarize implemented endpoints
2. explain placeholder scoring behavior
3. explain progress update and retry scheduling logic
4. list assumptions and follow-up tasks
