Read AGENTS.md, README.md, and the existing backend code first.

Set up local development infrastructure for iterview-api.

Scope:
- Docker Compose for PostgreSQL
- Spring profiles for local development
- developer-friendly startup configuration
- seed and migration execution flow

Requirements:
- add docker-compose.yml for local PostgreSQL
- configure application-local.yml or equivalent profile-based configuration
- document required environment variables
- ensure Flyway migrations run cleanly on local startup
- ensure seed/reference data can be initialized safely in development
- keep production assumptions separate from local defaults
- update README with local startup instructions

Out of scope:
- Kubernetes
- cloud deployment
- production secret management
- full observability stack

When finished:
1. summarize local development setup
2. explain profile configuration
3. explain how migrations and seed data run
4. list developer startup steps
