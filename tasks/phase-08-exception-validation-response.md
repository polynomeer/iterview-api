Read AGENTS.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Standardize global API response, validation, and exception handling for iterview-api.

Scope:
- global exception handling
- validation error responses
- standardized success/error response format
- consistent not-found, bad-request, unauthorized, and forbidden handling

Requirements:
- use Spring Boot global exception handling with @RestControllerAdvice
- define a consistent API error response structure
- define a consistent success response strategy where appropriate
- keep controller code minimal by relying on validation annotations and centralized handlers
- handle at least:
  - MethodArgumentNotValidException
  - ConstraintViolationException
  - EntityNotFound or equivalent domain not-found exceptions
  - IllegalArgumentException or domain validation exceptions
  - access denied / authentication exceptions
- make responses predictable for frontend integration
- add tests for:
  - validation error response shape
  - not-found response shape
  - unauthorized response shape
  - domain exception mapping

Out of scope:
- localization of error messages
- advanced error cataloging
- distributed tracing correlation design

When finished:
1. summarize response/error format
2. summarize handled exception types
3. explain validation strategy
4. list any controller or service changes made
