# 02-backend-architecture

## Package Layout
Use package-by-domain.

Example:
- common
- user
- resume
- question
- answer
- review
- dailycard
- feed

Each domain package should contain:
- controller
- service
- repository
- entity
- dto
- mapper
- enum

## Cross-Cutting Packages
### common.config
Spring configuration, Jackson config, WebMvc config, security config if added.

### common.exception
Global exception handling and domain exceptions.

### common.response
Standard API response wrapper only if the team chooses to use one consistently.

### common.util
Small reusable utilities only. Do not place domain logic here.

## Service Boundaries
### User/Profile
- get current profile
- update profile
- update settings
- replace target companies

### Resume
- create resume container
- upload resume version
- activate resume version
- list versions

### Question
- list questions with filters
- get question detail
- load linked materials and metadata

### Answer
- submit answer attempt
- calculate attempt_no
- persist score and feedback

### Review
- update user_question_progress
- schedule retry
- determine archive status

### Daily Card
- select today's question
- merge retry items and general recommendation
- expose home payload

### Feed
- serve simple popular/trending/company sections

## Transaction Boundaries
- answer submission should be transactional
- progress update and retry scheduling should happen in the same transaction as answer scoring persistence when possible
- read-only queries should use readOnly transactions where appropriate

## Implementation Guidance
- keep controllers thin
- map request DTO -> command object -> service
- repository queries should support main UI read paths
- centralize enums and status values
- do not leak entity objects into controllers

## Error Handling
At minimum define domain exceptions for:
- user not found
- resume not found
- resume version not found
- question not found
- invalid question state
- answer attempt not found
- invalid retry queue action
