Read AGENTS.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Implement authentication and current-user handling for iterview-api.

Scope:
- development-ready authentication foundation
- authenticated access for existing /api/me, resume, answer, review, archive, and home endpoints
- current-user resolution in a reusable way

Requirements:
- use Spring Security
- implement a simple JWT-based authentication flow or a clearly isolated temporary auth mechanism suitable for MVP
- provide a reusable current-user resolver abstraction
- ensure user-specific endpoints require authentication
- keep security configuration explicit and minimal
- avoid spreading security logic across controllers
- add integration tests for:
  - authenticated access success
  - unauthenticated access rejection
  - current-user resolution for protected endpoints

Recommended endpoint scope if needed:
- POST /api/auth/signup
- POST /api/auth/login
- GET /api/auth/me

Out of scope:
- OAuth social login
- refresh token rotation
- advanced account recovery
- production-grade hardening beyond MVP needs

When finished:
1. summarize security approach
2. summarize protected endpoints
3. explain current-user resolution design
4. list assumptions and security TODOs
