Read AGENTS.md, README.md, and the existing backend code first.

Add a minimal CI pipeline for iterview-api.

Scope:
- build verification
- test execution
- migration-safe validation as much as practical in CI

Requirements:
- add a CI workflow suitable for GitHub Actions
- run:
  - Gradle build
  - test
- configure Java and Gradle caching appropriately
- keep the workflow simple and maintainable
- update README if necessary with CI expectations

Out of scope:
- deployment
- release automation
- multi-environment promotion
- security scanning beyond basic dependency checks

When finished:
1. summarize the CI workflow
2. explain what is validated in CI
3. list assumptions and possible future improvements
