Read AGENTS.md, README.md, docs/03-db-schema.md, docs/04-api-contracts.md, and tasks/phase-1.md first.

Implement phase-1 for iterview-api.

Tech stack:
- Kotlin
- Spring Boot
- Gradle Kotlin DSL
- Spring Data JPA
- Flyway
- PostgreSQL

Requirements:
- initialize the backend project structure
- configure application startup
- add Flyway migrations for all MVP backend tables
- add minimal seed data for companies, job_roles, categories, and tags
- create domain-based package structure
- create repository interfaces and basic service skeletons
- keep business logic out of controllers
- keep DTOs separate from entities

Do not implement:
- lounge
- mock interview
- GitHub sync
- public answer comparison
- full scoring logic beyond placeholders

When finished:
1. summarize changed files
2. explain package structure
3. explain migration order
4. list assumptions
