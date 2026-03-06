# 05-implementation-plan

## Phase 1 - Foundation
1. initialize Spring Boot project with Kotlin and Gradle Kotlin DSL
2. configure PostgreSQL connection
3. configure Flyway
4. set up package-by-domain structure
5. add common exception handling and validation
6. add base test setup

## Phase 2 - Migrations and Seeds
1. create Flyway migrations for MVP tables
2. add indexes and constraints
3. add reference seeds for:
   - job_roles
   - categories
   - tags
   - companies

## Phase 3 - User and Resume
1. implement profile read/update API
2. implement settings update API
3. implement target company replace API
4. implement resume CRUD-lite API
5. implement resume version upload and activation

## Phase 4 - Question Catalog
1. implement question list API
2. implement question detail API
3. load tag/company/role/material relationships
4. add filters and paging

## Phase 5 - Answer Loop
1. implement answer submission API
2. calculate attempt number
3. score answer
4. create feedback items
5. update user_question_progress
6. create review_queue item if needed

## Phase 6 - Home and Archive
1. implement daily card selector
2. implement home API
3. implement archive API
4. implement review queue APIs

## Phase 7 - Feed
1. implement simple feed API
2. use stable sort logic for popular and trending sections

## Phase 8 - Tests
1. unit tests for scoring
2. unit tests for retry scheduling
3. unit tests for archive decision
4. integration tests for repositories
5. API tests for core endpoints
