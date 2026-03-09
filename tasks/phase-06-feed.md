Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Implement the basic feed API for iterview-api.

Scope:
- GET /api/feed

Requirements:
- return sections:
  - popular
  - trending
  - companyRelated
- use existing question-related data only
- keep the initial ranking logic simple and explicit
- exclude inactive questions by default
- support a minimal response DTO structure that can be used directly by the frontend
- include question metadata needed for feed cards:
  - questionId
  - title
  - category
  - difficulty
  - related companies
  - tags
  - optional user progress summary when available
- keep ranking logic inside a dedicated service
- add tests for:
  - feed response shape
  - exclusion of inactive questions
  - stable section responses with limited data

Out of scope:
- sophisticated trend scoring
- real-time ranking
- community-based ranking
- lounge integration
- personalized ranking beyond simple company matching

When finished:
1. summarize implemented endpoints
2. explain feed section selection logic
3. explain ranking assumptions
4. list follow-up improvements
