Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Implement the question catalog for iterview-api.

Scope:
- GET /api/questions
- GET /api/questions/{questionId}

Requirements:
- support filtering by categoryId, tag, companyId, roleId, difficulty, status, and search
- return question metadata, related tags, related companies, related learning materials
- include user progress summary when the current user has progress for the question
- exclude inactive questions by default
- keep controllers thin
- keep query logic in service/repository layers
- create DTOs for list item and detail responses
- add repository query methods or custom queries where needed
- add tests for filtering and detail retrieval

Out of scope:
- question creation
- moderation
- public comparison
- lounge integration
- advanced ranking logic

When finished:
1. summarize endpoints and filters
2. summarize repository/query approach
3. list assumptions
