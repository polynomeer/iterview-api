Read AGENTS.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Harden tests and improve build reliability for iterview-api.

Scope:
- strengthen service and integration tests
- remove duplication
- improve reliability of the current MVP backend

Requirements:
- review current tests and fill major coverage gaps
- add focused tests for:
  - profile and resume flows
  - question filtering and detail retrieval
  - answer submission
  - scoring and retry logic
  - archive logic
  - home payload
  - review queue transitions
  - authentication access rules
- refactor duplicated service or mapping code where it reduces maintenance risk
- keep refactors safe and incremental
- ensure the project builds and tests cleanly from scratch

Out of scope:
- performance benchmarking
- large architectural rewrites
- test coverage vanity metrics

When finished:
1. summarize added and improved tests
2. summarize refactors performed
3. identify remaining weak spots
4. list recommendations before frontend integration
