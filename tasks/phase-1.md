# phase-1

## Goal
Implement the backend foundation for the MVP.

## Tasks
1. initialize Spring Boot project with Kotlin
2. configure Gradle Kotlin DSL
3. configure PostgreSQL and Flyway
4. create domain package structure
5. add Flyway migrations for all MVP tables
6. add reference seed scripts
7. create repository interfaces
8. add base service skeletons
9. add schema and service tests

## Constraints
- use docs/03-db-schema.md as source of truth
- keep business logic out of controllers
- do not implement community, mock interview, GitHub sync, or public answer comparison

## Done When
- project builds
- migrations run successfully
- reference seeds work
- tests pass
