Read AGENTS.md, docs/03-db-schema.md, docs/04-api-contracts.md, docs/07-acceptance-criteria.md, and the existing backend code first.

Refine answer scoring and retry policy for iterview-api.

Scope:
- improve the placeholder scoring logic into a clear rule-based MVP scoring service
- refine archive and retry decisions

Requirements:
- keep scoring logic in a dedicated scoring service
- keep retry/archive decision logic in a dedicated policy service
- scoring must produce:
  - totalScore
  - structureScore
  - specificityScore
  - technicalAccuracyScore
  - roleFitScore
  - companyFitScore
  - communicationScore
  - evaluationResult
- use simple deterministic heuristics for MVP
- archive decision must consider:
  - total score threshold
  - minimum dimension thresholds where appropriate
- retry scheduling must consider:
  - low total score
  - weak dimension score
  - unanswered or skipped state if relevant
- ensure services are easy to replace later with AI-based scoring
- add tests for:
  - archive path
  - retry path
  - pass-but-not-archive path
  - weak-dimension-triggered retry

Out of scope:
- LLM integration
- semantic grading
- transcript scoring
- voice analysis

When finished:
1. summarize scoring heuristics
2. summarize retry/archive rules
3. explain extension points for future AI scoring
4. list assumptions and limitations
