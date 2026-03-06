# Interview Training Platform Backend

## Overview
This repository contains the backend for an interview training platform.

The product loop is:
1. user configures profile and resume
2. system selects a daily interview question
3. user submits an answer
4. backend scores the answer
5. low-scoring answers are scheduled for retry
6. strong answers are archived
7. progress accumulates over time

## MVP Scope
- user profile and settings
- target companies
- resume versioning
- question catalog
- daily card API
- answer attempt API
- scoring and feedback
- retry queue
- archive API
- feed API

## Source Documents
- docs/01-product-overview.md
- docs/02-backend-architecture.md
- docs/03-db-schema.md
- docs/04-api-contracts.md
- docs/05-implementation-plan.md
- docs/06-acceptance-criteria.md

## Suggested Stack
- Kotlin + Spring Boot
- PostgreSQL
- Flyway
- Spring Data JPA
- Gradle Kotlin DSL
