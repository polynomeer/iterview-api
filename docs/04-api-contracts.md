# 04-api-contracts

## Conventions
- REST JSON
- authenticated endpoints use bearer auth
- timestamps use ISO-8601 strings
- score values are numeric 0-100

## Profile
### GET /api/me
Return:
- profile
- settings
- activeResumeVersionSummary
- targetCompanies

### PATCH /api/me/profile
Request:
- nickname
- jobRoleId
- yearsOfExperience

### PATCH /api/me/settings
Request:
- targetScoreThreshold
- passScoreThreshold
- retryEnabled
- dailyQuestionCount

### PUT /api/me/target-companies
Request:
- companies: array of { companyId, priorityOrder }

## Resume
### GET /api/resumes
Return all resume containers with versions.

### POST /api/resumes
Request:
- title
- isPrimary

### POST /api/resumes/{resumeId}/versions
Request:
- fileUrl
- rawText
- parsedJson
- summaryText

### POST /api/resume-versions/{versionId}/activate
Activate one version.

## Questions
### GET /api/questions
Filters:
- categoryId
- tag
- companyId
- roleId
- difficulty
- search

### GET /api/questions/{questionId}
Return:
- question
- tags
- companies
- roles
- learningMaterials
- userProgressSummary

## Home
### GET /api/home
Return:
- todayQuestion
- retryQuestions
- learningMaterials
- summaryStats

### POST /api/daily-cards/{dailyCardId}/open
Marks card as opened.

## Answer Attempts
### POST /api/questions/{questionId}/answers
Request:
- resumeVersionId
- answerMode
- contentText

Response:
- answerAttemptId
- scoreSummary
- feedback
- progressStatus
- nextReviewAt
- archiveDecision

### GET /api/questions/{questionId}/answers
Return current user's answer attempt history for that question.

### GET /api/answer-attempts/{answerAttemptId}
Return:
- answerAttempt
- score
- feedback
- progressSummary

## Review Queue
### GET /api/review-queue
Return pending retry questions for current user.

### POST /api/review-queue/{queueId}/skip
Skip one item.

### POST /api/review-queue/{queueId}/done
Mark queue item handled.

## Archive
### GET /api/archive
Return archived question summaries.

## Feed
### GET /api/feed
Return:
- popular
- trending
- companyRelated
