# 04-api-contracts

## Overview
Base URL:
- local: `http://localhost:8080`

Formats:
- REST JSON
- timestamps use ISO-8601 strings
- numeric score fields are in the `0-100` range

Authentication:
- bearer token: `Authorization: Bearer <token>`
- public endpoints:
  - `GET /api/health`
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `GET /api/questions`
  - `GET /api/questions/{questionId}`
- authenticated endpoints:
  - `GET /api/auth/me`
  - `GET /api/me`
  - `PATCH /api/me/profile`
  - `PATCH /api/me/settings`
  - `PUT /api/me/target-companies`
  - `GET /api/resumes`
  - `POST /api/resumes`
  - `POST /api/resumes/{resumeId}/versions`
  - `POST /api/resume-versions/{versionId}/activate`
  - `GET /api/home`
  - `POST /api/daily-cards/{dailyCardId}/open`
  - `POST /api/questions/{questionId}/answers`
  - `GET /api/questions/{questionId}/answers`
  - `GET /api/answer-attempts/{answerAttemptId}`
  - `GET /api/review-queue`
  - `POST /api/review-queue/{queueId}/skip`
  - `POST /api/review-queue/{queueId}/done`
  - `GET /api/archive`
  - `GET /api/feed`

## Standard Error Response
All error responses use this shape:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "status": 400,
    "message": "Request validation failed",
    "path": "/api/auth/signup",
    "timestamp": "2026-03-09T04:00:00Z",
    "details": [
      {
        "field": "email",
        "message": "must be a well-formed email address"
      }
    ]
  }
}
```

Common error codes:
- `VALIDATION_ERROR` for bean validation failures
- `BAD_REQUEST` for domain validation failures
- `UNAUTHORIZED` for missing or invalid authentication
- `FORBIDDEN` for access denial
- `NOT_FOUND` for missing resources
- `CONFLICT` for state transition conflicts
- `INTERNAL_SERVER_ERROR` for unexpected server errors

## Health
### GET /api/health
Auth:
- public

Response:
```json
{
  "status": "ok"
}
```

## Authentication
### POST /api/auth/signup
Auth:
- public

Request:
```json
{
  "email": "candidate@example.com",
  "password": "password123"
}
```

Response:
```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "email": "candidate@example.com",
    "status": "ACTIVE"
  }
}
```

### POST /api/auth/login
Auth:
- public

Request:
```json
{
  "email": "candidate@example.com",
  "password": "password123"
}
```

Response:
- same shape as `POST /api/auth/signup`

### GET /api/auth/me
Auth:
- required

Response:
```json
{
  "id": 1,
  "email": "candidate@example.com"
}
```

## Profile
### GET /api/me
Auth:
- required

Response:
```json
{
  "profile": {
    "nickname": "hammac",
    "jobRoleId": 1,
    "yearsOfExperience": 5
  },
  "settings": {
    "targetScoreThreshold": 80,
    "passScoreThreshold": 60,
    "retryEnabled": true,
    "dailyQuestionCount": 1
  },
  "activeResumeVersionSummary": {
    "resumeId": 10,
    "resumeTitle": "Platform Resume",
    "versionId": 22,
    "versionNo": 2,
    "uploadedAt": "2026-03-09T04:00:00Z"
  },
  "targetCompanies": [
    {
      "companyId": 1,
      "companyName": "Amazon",
      "priorityOrder": 1
    }
  ]
}
```

### PATCH /api/me/profile
Auth:
- required

Request:
```json
{
  "nickname": "hammac",
  "jobRoleId": 1,
  "yearsOfExperience": 5
}
```

Response:
```json
{
  "nickname": "hammac",
  "jobRoleId": 1,
  "yearsOfExperience": 5
}
```

### PATCH /api/me/settings
Auth:
- required

Request:
```json
{
  "targetScoreThreshold": 85,
  "passScoreThreshold": 65,
  "retryEnabled": true,
  "dailyQuestionCount": 2
}
```

Response:
```json
{
  "targetScoreThreshold": 85,
  "passScoreThreshold": 65,
  "retryEnabled": true,
  "dailyQuestionCount": 2
}
```

Notes:
- `passScoreThreshold` must not exceed `targetScoreThreshold`

### PUT /api/me/target-companies
Auth:
- required

Request:
```json
{
  "companies": [
    {
      "companyId": 2,
      "priorityOrder": 1
    },
    {
      "companyId": 1,
      "priorityOrder": 2
    }
  ]
}
```

Response:
```json
{
  "companies": [
    {
      "companyId": 2,
      "companyName": "Google",
      "priorityOrder": 1
    },
    {
      "companyId": 1,
      "companyName": "Amazon",
      "priorityOrder": 2
    }
  ]
}
```

Notes:
- duplicate `companyId` values are rejected
- invalid `companyId` values are rejected

## Resume
### GET /api/resumes
Auth:
- required

Response:
```json
[
  {
    "id": 10,
    "title": "Platform Resume",
    "isPrimary": true,
    "versions": [
      {
        "id": 21,
        "versionNo": 1,
        "fileUrl": "https://files.example.com/resume-v1.pdf",
        "rawText": "Resume text",
        "parsedJson": "{\"skills\":[\"kotlin\"]}",
        "summaryText": "First version",
        "isActive": false,
        "uploadedAt": "2026-03-09T04:00:00Z"
      }
    ]
  }
]
```

### POST /api/resumes
Auth:
- required

Request:
```json
{
  "title": "Platform Resume",
  "isPrimary": true
}
```

Response:
- `ResumeDto` object, same shape as one list item from `GET /api/resumes`

### POST /api/resumes/{resumeId}/versions
Auth:
- required

Request:
```json
{
  "fileUrl": "https://files.example.com/resume-v2.pdf",
  "rawText": "Version two text",
  "parsedJson": "{\"skills\":[\"kotlin\",\"spring\"]}",
  "summaryText": "Second version"
}
```

Response:
```json
{
  "id": 22,
  "versionNo": 2,
  "fileUrl": "https://files.example.com/resume-v2.pdf",
  "rawText": "Version two text",
  "parsedJson": "{\"skills\":[\"kotlin\",\"spring\"]}",
  "summaryText": "Second version",
  "isActive": false,
  "uploadedAt": "2026-03-09T04:00:00Z"
}
```

### POST /api/resume-versions/{versionId}/activate
Auth:
- required

Response:
```json
{
  "resumeId": 10,
  "versionId": 22,
  "versionNo": 2,
  "activatedAt": "2026-03-09T04:00:00Z"
}
```

## Questions
### GET /api/questions
Auth:
- public

Query parameters:
- `categoryId`
- `tag`
- `companyId`
- `roleId`
- `difficulty`
- `status`
- `search`

Notes:
- inactive questions are excluded by default
- `tag`, `difficulty`, and `status` are matched case-insensitively

Response:
```json
[
  {
    "id": 100,
    "title": "Design a resilient queue",
    "category": "System Design",
    "difficulty": "HARD",
    "tags": [
      {
        "id": 1,
        "name": "scalability"
      }
    ],
    "companies": [
      {
        "id": 1,
        "name": "Amazon",
        "relevanceScore": 0.9,
        "pastFrequent": true,
        "trendingRecent": true
      }
    ],
    "learningMaterials": [
      {
        "id": 5,
        "title": "Queue Design Guide",
        "materialType": "article",
        "contentUrl": "https://example.com/queue-guide",
        "sourceName": "Eng Blog"
      }
    ]
  }
]
```

### GET /api/questions/{questionId}
Auth:
- public

Response:
```json
{
  "question": {
    "id": 100,
    "title": "Design a resilient queue",
    "body": "Explain throughput, durability, and backpressure",
    "categoryId": 1,
    "categoryName": "System Design",
    "questionType": "technical",
    "difficultyLevel": "HARD",
    "qualityStatus": "approved",
    "expectedAnswerSeconds": 300
  },
  "tags": [],
  "companies": [],
  "roles": [],
  "learningMaterials": [],
  "userProgressSummary": {
    "currentStatus": "in_progress",
    "latestScore": 72.5,
    "bestScore": 81.0,
    "totalAttemptCount": 3,
    "lastAnsweredAt": "2026-03-09T04:00:00Z",
    "nextReviewAt": "2026-03-11T04:00:00Z",
    "masteryLevel": "intermediate"
  }
}
```

Notes:
- `userProgressSummary` is only included when the request is authenticated

## Home
### GET /api/home
Auth:
- required

Response:
```json
{
  "todayQuestion": {
    "dailyCardId": 15,
    "questionId": 100,
    "title": "Design a resilient queue",
    "difficulty": "HARD",
    "cardDate": "2026-03-09",
    "cardType": "retry",
    "status": "new"
  },
  "retryQuestions": [
    {
      "reviewQueueId": 31,
      "questionId": 101,
      "title": "Explain cache invalidation",
      "difficulty": "MEDIUM",
      "priority": 80,
      "scheduledFor": "2026-03-09T04:00:00Z"
    }
  ],
  "learningMaterials": [],
  "summaryStats": {
    "dailyQuestionCount": 1,
    "retryQuestionCount": 1,
    "pendingReviewCount": 1,
    "archivedQuestionCount": 2
  }
}
```

### POST /api/daily-cards/{dailyCardId}/open
Auth:
- required

Response:
```json
{
  "id": 15,
  "status": "opened",
  "openedAt": "2026-03-09T04:00:00Z"
}
```

Notes:
- repeated open calls are idempotent

## Answer Attempts
### POST /api/questions/{questionId}/answers
Auth:
- required

Request:
```json
{
  "resumeVersionId": 22,
  "answerMode": "text",
  "contentText": "First, I would clarify the traffic pattern and durability constraints..."
}
```

Response:
```json
{
  "answerAttemptId": 200,
  "scoreSummary": {
    "totalScore": 74,
    "structureScore": 70,
    "specificityScore": 72,
    "technicalAccuracyScore": 76,
    "roleFitScore": 68,
    "companyFitScore": 66,
    "communicationScore": 73,
    "evaluationResult": "PASS"
  },
  "feedback": [
    {
      "id": 1,
      "feedbackType": "strength",
      "severity": "info",
      "title": "Good baseline answer",
      "body": "Your answer covers the question and keeps a coherent flow.",
      "displayOrder": 1
    }
  ],
  "progressStatus": "in_progress",
  "nextReviewAt": null,
  "archiveDecision": false
}
```

Notes:
- low-quality answers can create or update a retry queue item
- `answerMode` values like `skip` and `unanswered` trigger retry behavior

### GET /api/questions/{questionId}/answers
Auth:
- required

Response:
```json
[
  {
    "id": 200,
    "attemptNo": 2,
    "answerMode": "text",
    "submittedAt": "2026-03-09T04:00:00Z",
    "scoreSummary": {
      "totalScore": 74,
      "structureScore": 70,
      "specificityScore": 72,
      "technicalAccuracyScore": 76,
      "roleFitScore": 68,
      "companyFitScore": 66,
      "communicationScore": 73,
      "evaluationResult": "PASS"
    }
  }
]
```

### GET /api/answer-attempts/{answerAttemptId}
Auth:
- required

Response:
```json
{
  "answerAttempt": {
    "id": 200,
    "questionId": 100,
    "resumeVersionId": 22,
    "attemptNo": 2,
    "answerMode": "text",
    "contentText": "First, I would clarify the traffic pattern...",
    "submittedAt": "2026-03-09T04:00:00Z"
  },
  "score": {
    "totalScore": 74,
    "structureScore": 70,
    "specificityScore": 72,
    "technicalAccuracyScore": 76,
    "roleFitScore": 68,
    "companyFitScore": 66,
    "communicationScore": 73,
    "evaluationResult": "PASS"
  },
  "feedback": [],
  "progressSummary": {
    "currentStatus": "in_progress",
    "latestScore": 74,
    "bestScore": 81,
    "totalAttemptCount": 3,
    "lastAnsweredAt": "2026-03-09T04:00:00Z",
    "nextReviewAt": null,
    "masteryLevel": "intermediate"
  }
}
```

## Review Queue
### GET /api/review-queue
Auth:
- required

Response:
```json
[
  {
    "id": 31,
    "questionId": 101,
    "questionTitle": "Explain cache invalidation",
    "questionDifficulty": "MEDIUM",
    "reasonType": "low_total",
    "priority": 100,
    "scheduledFor": "2026-03-09T04:00:00Z",
    "status": "pending"
  }
]
```

### POST /api/review-queue/{queueId}/skip
Auth:
- required

Response:
```json
{
  "id": 31,
  "status": "skipped",
  "updatedAt": "2026-03-09T04:00:00Z"
}
```

### POST /api/review-queue/{queueId}/done
Auth:
- required

Response:
```json
{
  "id": 31,
  "status": "done",
  "updatedAt": "2026-03-09T04:00:00Z"
}
```

Notes:
- missing queue ids return `404`
- non-pending queue items return `409`

## Archive
### GET /api/archive
Auth:
- required

Response:
```json
[
  {
    "questionId": 100,
    "title": "Design a resilient queue",
    "difficulty": "HARD",
    "archivedAt": "2026-03-09T04:00:00Z",
    "bestScore": 92,
    "totalAttemptCount": 3
  }
]
```

## Feed
### GET /api/feed
Auth:
- required

Response:
```json
{
  "popular": [
    {
      "questionId": 100,
      "title": "Design a resilient queue",
      "category": "System Design",
      "difficulty": "HARD",
      "companies": [],
      "tags": [],
      "userProgressSummary": null
    }
  ],
  "trending": [],
  "companyRelated": []
}
```

Notes:
- `GET /api/feed` currently requires authentication
- `GET /api/home` currently requires authentication
- `GET /api/questions` and `GET /api/questions/{questionId}` remain public
