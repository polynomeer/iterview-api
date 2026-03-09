Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Refine the daily card generation and home selection logic for iterview-api.

Scope:
- improve daily question selection
- improve retry question prioritization
- make daily card generation deterministic and testable

Requirements:
- prioritize retry questions when required
- exclude archived questions from normal retry selection unless explicitly reset
- use simple prioritization inputs:
  - pending review queue
  - target companies
  - active resume version presence
  - question activity status
- ensure the main todayQuestion is separated from retryQuestions
- avoid returning duplicate questions in home payload
- create or refine a dedicated daily card generation service
- make the selection logic testable and deterministic
- add tests for:
  - retry-first behavior
  - no duplicate cards
  - archived question exclusion
  - fallback selection when no retry items exist

Out of scope:
- ML recommendation
- trend-aware personalization
- batch scheduling infrastructure
- background job orchestration

When finished:
1. summarize daily card generation logic
2. explain retry prioritization
3. explain fallback logic
4. list assumptions and future improvement points
