# 06-acceptance-criteria

## Profile
- user can fetch current profile
- user can update role and years of experience
- user can replace target companies
- settings persist correctly

## Resume
- user can create a resume container
- user can upload multiple resume versions
- user can activate one version
- existing answer attempts remain tied to old versions

## Questions
- list endpoint supports filters
- detail endpoint returns metadata and learning materials
- inactive questions are excluded by default

## Answer Submission
- user can submit an answer attempt
- attempt_no increments correctly per user-question
- answer score row is created
- feedback items are created
- progress row is inserted or updated
- retry scheduling occurs when score is below threshold

## Review Queue
- low score creates pending retry item
- archived questions do not produce active retry items
- skip and done actions update queue item state

## Home
- home endpoint returns at least one daily question when available
- retry questions are separate from main daily question
- summary stats are included

## Archive
- archived items only are returned from archive endpoint

## Feed
- feed returns popular and trending sections
- API shape is stable even with limited data

## Quality
- no controller contains scoring logic
- every schema change uses Flyway
- DTOs are separate from entities
- core services have tests
