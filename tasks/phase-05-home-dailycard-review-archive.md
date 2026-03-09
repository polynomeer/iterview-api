Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Implement the home, daily card, review queue, and archive APIs for iterview-api.

Scope:
- GET /api/home
- POST /api/daily-cards/{id}/open
- GET /api/review-queue
- POST /api/review-queue/{id}/skip
- POST /api/review-queue/{id}/done
- GET /api/archive

Requirements:
- home response must return:
  - todayQuestion
  - retryQuestions
  - learningMaterials
  - summaryStats
- separate main daily question from retry questions
- include linked learning materials when available
- review queue must return pending items ordered by scheduled time and priority
- archive API must return archived questions only
- add tests for home payload shape, archive filtering, and review queue state transitions

Out of scope:
- feed ranking
- trend logic
- community integration
- mock interview

When finished:
1. summarize implemented endpoints
2. explain home selection logic
3. explain archive and review queue behavior
4. list assumptions
