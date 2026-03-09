Read AGENTS.md, README.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing code first.

Implement the profile and resume features for iterview-api.

Tech stack:
- Kotlin
- Spring Boot
- Gradle Kotlin DSL
- Spring Data JPA
- Flyway
- PostgreSQL

Scope:
- GET /api/me
- PATCH /api/me/profile
- PATCH /api/me/settings
- PUT /api/me/target-companies
- GET /api/resumes
- POST /api/resumes
- POST /api/resumes/{resumeId}/versions
- POST /api/resume-versions/{versionId}/activate

Requirements:
- use package-by-domain structure
- keep request/response DTOs separate from entities
- add validation for request payloads
- implement controller, service, repository, entity, dto, and mapper where needed
- use Flyway migration only if schema changes are required
- keep business logic in services, not controllers
- support a temporary development-only current-user strategy if authentication is not implemented yet
- resume versions must be immutable records
- only one resume version can be active per resume

Out of scope:
- authentication hardening
- file storage integration
- resume parsing
- AI extraction
- lounge
- mock interview
- GitHub sync
- public answer comparison

Deliverables:
1. working endpoints for profile and resume APIs
2. tests for main success cases and key validation failures
3. clear TODO comments for auth and file upload assumptions if needed

When finished:
1. summarize changed files
2. summarize implemented endpoints
3. explain any schema changes
4. list assumptions and TODOs
