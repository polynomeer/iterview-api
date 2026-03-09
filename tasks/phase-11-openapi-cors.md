Read AGENTS.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Prepare iterview-api for frontend integration with OpenAPI documentation, CORS, and development-friendly API configuration.

Scope:
- OpenAPI/Swagger setup
- CORS configuration
- clear API documentation for implemented endpoints

Requirements:
- add springdoc-openapi integration
- expose Swagger UI in development
- document implemented endpoints and major DTOs
- configure CORS for local frontend development
- ensure authentication-related endpoints and protected endpoints are documented clearly
- keep environment-specific settings configurable
- add a small integration test or startup verification where useful

Out of scope:
- production gateway integration
- API versioning strategy beyond current MVP
- public API developer portal

When finished:
1. summarize Swagger/OpenAPI setup
2. summarize CORS configuration
3. explain how frontend developers should use the API docs
4. list assumptions and environment variables
