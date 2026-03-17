# 04-api-contracts

## Overview
Base URL:
- local: `http://localhost:8080`

Formats:
- REST JSON
- timestamps use ISO-8601 strings
- numeric score fields are in the `0-100` range
- localized display text initially supports `ko` and `en`

Contract policy:
- endpoints listed under "Current API" match the existing backend implementation
- endpoints listed under "Planned Additive API" describe the next product slices and must not be treated as already implemented
- existing field names must remain backward compatible
- machine-readable fields remain locale-neutral even when human-readable display text is localized

## Authentication
Bearer token:
- `Authorization: Bearer <token>`

Locale negotiation:
- supported locales are `ko` and `en`
- clients may send `Accept-Language`
- authenticated requests may also rely on the stored user preference
- when both are present, explicit request locale wins over stored preference
- unsupported locales fall back to `ko`
- user-authored source content is still returned in its original language

Public endpoints:
- `GET /api/health`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/questions`
- `GET /api/questions/{questionId}`
- `GET /api/questions/{questionId}/reference-answers`
- `GET /api/questions/{questionId}/learning-materials`

Authenticated endpoints:
- `GET /api/auth/me`
- `GET /api/me`
- `PATCH /api/me/profile`
- `PATCH /api/me/settings`
- `PUT /api/me/target-companies`
- `GET /api/resumes`
- `POST /api/resumes`
- `GET /api/job-postings`
- `POST /api/job-postings`
- `GET /api/job-postings/{jobPostingId}`
- `POST /api/resumes/{resumeId}/versions`
- `POST /api/resumes/{resumeId}/versions/upload`
- `GET /api/resume-versions/{versionId}`
- `GET /api/resume-versions/{versionId}/extraction`
- `GET /api/resume-versions/{versionId}/file`
- `GET /api/resume-versions/{versionId}/profile`
- `GET /api/resume-versions/{versionId}/contacts`
- `GET /api/resume-versions/{versionId}/competencies`
- `POST /api/resume-versions/{versionId}/re-extract`
- `GET /api/resume-versions/{versionId}/projects`
- `GET /api/resume-versions/{versionId}/achievements`
- `GET /api/resume-versions/{versionId}/education`
- `GET /api/resume-versions/{versionId}/certifications`
- `GET /api/resume-versions/{versionId}/awards`
- `GET /api/resume-versions/{versionId}/analyses`
- `POST /api/resume-versions/{versionId}/analyses`
- `GET /api/resume-versions/{versionId}/analyses/{analysisId}`
- `PATCH /api/resume-versions/{versionId}/analyses/{analysisId}/suggestions/{suggestionId}`
- `GET /api/resume-versions/{versionId}/question-heatmap`
- `GET /api/resume-versions/{versionId}/question-heatmap/overlay-targets`
- `POST /api/resume-versions/{versionId}/question-heatmap/links`
- `PATCH /api/resume-versions/{versionId}/question-heatmap/links/{linkId}`
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
- `GET /api/interview-sessions`
- `POST /api/interview-sessions`
- `GET /api/interview-sessions/{sessionId}`
- `GET /api/interview-sessions/{sessionId}/coverage`
- `GET /api/interview-sessions/{sessionId}/resume-map`
- `POST /api/interview-sessions/{sessionId}/answers`
- `POST /api/interview-sessions/{sessionId}/skip-question`
- `POST /api/interview-sessions/{sessionId}/next-question`
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
    "timestamp": "2026-03-11T04:00:00Z",
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
- `VALIDATION_ERROR`
- `BAD_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `PAYLOAD_TOO_LARGE`
- `INTERNAL_SERVER_ERROR`

## Current API
### Health
#### `GET /api/health`
Auth:
- public

Response:
```json
{
  "status": "ok"
}
```

### Authentication
#### `POST /api/auth/signup`
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

#### `POST /api/auth/login`
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

#### `GET /api/auth/me`
Auth:
- required

Response:
```json
{
  "id": 1,
  "email": "candidate@example.com"
}
```

### Profile
#### `GET /api/me`
Auth:
- required

Response:
```json
{
  "profile": {
    "nickname": "hammac",
    "jobRoleId": 1,
    "yearsOfExperience": 5,
    "profileImageUrl": "/uploads/profile-images/user-1-abc123.png",
    "profileImageFileName": "user-1-abc123.png",
    "profileImageContentType": "image/png",
    "profileImageUploadedAt": "2026-03-12T01:00:00Z"
  },
  "settings": {
    "targetScoreThreshold": 80,
    "passScoreThreshold": 60,
    "retryEnabled": true,
    "dailyQuestionCount": 1,
    "preferredLanguage": "ko"
  },
  "activeResumeVersionSummary": {
    "resumeId": 10,
    "resumeTitle": "Platform Resume",
    "versionId": 22,
    "versionNo": 2,
    "uploadedAt": "2026-03-11T04:00:00Z"
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

### Job Posting
#### `POST /api/job-postings`
Auth:
- required

Request:
```json
{
  "inputType": "text",
  "companyName": "Example Corp",
  "roleName": "Backend Platform Engineer",
  "rawText": "Responsibilities\n- Build backend APIs with Spring Boot\n- Improve cache throughput with Redis and Kafka"
}
```

Response:
```json
{
  "id": 1,
  "inputType": "text",
  "sourceUrl": null,
  "rawText": "Responsibilities\n- Build backend APIs with Spring Boot\n- Improve cache throughput with Redis and Kafka",
  "companyName": "Example Corp",
  "roleName": "Backend Platform Engineer",
  "parsedRequirements": [
    "- Build backend APIs with Spring Boot",
    "- Improve cache throughput with Redis and Kafka"
  ],
  "parsedNiceToHave": [],
  "parsedKeywords": ["Spring Boot", "Redis", "Kafka"],
  "parsedResponsibilities": ["Responsibilities"],
  "parsedSummary": "Backend Platform Engineer focused on Spring Boot, Redis, Kafka.",
  "createdAt": "2026-03-17T10:00:00Z",
  "updatedAt": "2026-03-17T10:00:00Z"
}
```

#### `GET /api/job-postings`
- returns the current user's saved job postings ordered by `createdAt desc`

#### `GET /api/job-postings/{jobPostingId}`
- returns one saved job posting with parsed requirements, responsibilities, and keywords
- link-based inputs now also return fetch metadata:
  - `fetchStatus`
  - `fetchedTitle`
  - `fetchErrorMessage`
  - `fetchedAt`

Notes:
- this remains the primary current-user aggregate
- future readiness or radar summaries should be additive fields, not a replacement payload

#### `PATCH /api/me/profile`
Auth:
- required

Request:
```json
{
  "nickname": "hammac",
  "jobRoleId": 1,
  "yearsOfExperience": 5,
  "profileImageUrl": "/uploads/profile-images/user-1-abc123.png",
  "profileImageFileName": "user-1-abc123.png",
  "profileImageContentType": "image/png",
  "profileImageUploadedAt": "2026-03-12T01:00:00Z"
}
```

#### `POST /api/me/profile-image`
Auth:
- required

Content type:
- `multipart/form-data`

Request:
- form field `file`

Response:
```json
{
  "imageUrl": "/uploads/profile-images/user-1-abc123.png",
  "fileName": "user-1-abc123.png",
  "contentType": "image/png",
  "uploadedAt": "2026-03-12T01:00:00Z"
}
```

### Resume Analysis
#### `POST /api/resume-versions/{versionId}/analyses`
Auth:
- required

Request:
```json
{
  "jobPostingId": 1,
  "preferredFormatType": "technical_focused"
}
```

Response highlights:
- `overallScore`
- `matchSummary`
- `strongMatches`
- `missingKeywords`
- `weakSignals`
- `recommendedFocusAreas`
- `suggestedHeadline`
- `suggestedSummary`
- `recommendedFormatType`
- `generationSource`
- `llmModel`
- `analysisNotes`
- `tailoredDocument`
- `suggestions[]`
- `exports[]`

Notes:
- analyses are persisted separately from immutable `resume_versions`
- one resume version may have multiple analyses for different job postings
- if OpenAI resume analysis generation is configured, rewritten suggestions and tailored document content may be AI-generated
- if not configured, deterministic fallback generation still persists a tailored document and rewrite suggestions

#### `GET /api/resume-versions/{versionId}/analyses`
- returns persisted analysis summaries ordered by `createdAt desc`

#### `GET /api/resume-versions/{versionId}/analyses/{analysisId}`
- returns one persisted analysis with section-level suggestion rows

#### `PATCH /api/resume-versions/{versionId}/analyses/{analysisId}/suggestions/{suggestionId}`
Auth:
- required

Request:
```json
{
  "accepted": true
}
```

Notes:
- toggles suggestion acceptance state only
- does not mutate the source `resume_versions` row or extracted resume snapshots
- the backend refreshes the persisted tailored document view after acceptance changes

#### `POST /api/resume-versions/{versionId}/analyses/{analysisId}/exports`
Auth:
- required

Request:
```json
{
  "exportType": "pdf"
}
```

Notes:
- generates a server-side PDF from the persisted tailored document
- persists export history for later download and audit

#### `GET /api/resume-versions/{versionId}/analyses/{analysisId}/exports`
- returns export history for one analysis ordered by `createdAt desc`

#### `GET /api/resume-versions/{versionId}/analyses/{analysisId}/exports/{exportId}/file`
- returns the generated export file stream

### Resume Question Heatmap
#### `GET /api/resume-versions/{versionId}/question-heatmap`
Auth:
- required

Query params:
- `scope`
  - `all`
  - `main`
  - `follow_up`

Response highlights:
- `summary.totalAnchors`
- `summary.totalLinkedQuestions`
- `summary.hottestAnchorLabel`
- `summary.mostFollowedUpAnchorLabel`
- `summary.weakestAnchorLabel`
- `items[].anchorType`
- `items[].anchorRecordId`
- `items[].anchorKey`
- `items[].label`
- `items[].snippet`
- `items[].heatScore`
- `items[].normalizedHeatLevel`
- `items[].directQuestionCount`
- `items[].followUpCount`
- `items[].distinctInterviewCount`
- `items[].pressureQuestionCount`
- `items[].weaknessCount`
- `items[].recentQuestionAt`
- `items[].overlayTargets[]`
- `items[].linkedQuestions[]`

Notes:
- this is an additive read model layered on top of immutable `resume_versions`
- the heatmap aggregates linked practical interview questions by parsed resume anchor
- active manual override links take precedence over inferred or heuristic anchor resolution
- the current backend intentionally skips questions that cannot be mapped to a stable resume anchor
- each anchor item still represents one stable parsed resume anchor such as a project, experience, skill, competency, or summary block
- `overlayTargets[]` now adds a second layer inside each anchor:
  - `block` targets for project-wide or anchor-wide questions
  - `sentence` targets for sentence-specific hover overlays
- if a question does not match one sentence strongly, it falls back to the anchor-wide `block` target
- follow-up questions can inherit the selected sentence target from their parent question

#### `GET /api/resume-versions/{versionId}/question-heatmap/overlay-targets`
Auth:
- required

Query params:
- `scope`
  - `all`
  - `main`
  - `follow_up`

Response highlights:
- `items[].anchorType`
- `items[].anchorRecordId`
- `items[].anchorKey`
- `items[].targetType`
- `items[].fieldPath`
- `items[].textSnippet`
- `items[].textStartOffset`
- `items[].textEndOffset`
- `items[].sentenceIndex`
- `items[].paragraphIndex`
- `items[].heatScore`
- `items[].normalizedHeatLevel`
- `items[].questionCount`
- `items[].followUpCount`
- `items[].pressureQuestionCount`
- `items[].weaknessCount`
- `items[].linkedQuestions[]`

Notes:
- this is the flattened overlay read for resume viewer hover states
- `targetType = block` means the question belongs to the whole anchor, not one sentence
- `targetType = sentence` means the question should be shown when that sentence is hovered or focused
- the same `linkedQuestions[]` shape is reused so the frontend can share question-card rendering between anchor cards and sentence overlays

#### `POST /api/resume-versions/{versionId}/question-heatmap/links`
Auth:
- required

Request:
```json
{
  "interviewRecordQuestionId": 100,
  "anchorType": "project",
  "anchorRecordId": 12,
  "confidenceScore": 0.97
}
```

Notes:
- creates or replaces one manual heatmap link for one practical interview question
- the linked question must belong to a practical interview record already connected to the same `resumeVersionId`

#### `PATCH /api/resume-versions/{versionId}/question-heatmap/links/{linkId}`
Auth:
- required

Request:
```json
{
  "active": false
}
```

Notes:
- can move the effective anchor or deactivate one manual override
- deactivating one manual override falls back to inferred or heuristic anchor resolution on subsequent reads

### Sentence Overlay Heatmap
Implemented:

- `GET /api/resume-versions/{versionId}/question-heatmap` now includes nested `overlayTargets[]`
- `GET /api/resume-versions/{versionId}/question-heatmap/overlay-targets` exposes the same overlay targets as a flattened list
- each overlay target currently represents:
  - `block`
  - `sentence`

Current behavior:
- project-wide questions remain linked to a `block` target
- sentence-specific questions link to a `sentence` target when the text match is strong enough
- follow-up questions can stay attached to the parent sentence target when appropriate
- the frontend can render both layers at once:
  - whole-project or whole-anchor tint
  - sentence hover popovers

Notes:
- supported file types are PNG, JPEG, WEBP, and GIF
- max file size is 5 MB
- uploaded files are exposed under `/uploads/profile-images/**`

Response:
```json
{
  "nickname": "hammac",
  "jobRoleId": 1,
  "yearsOfExperience": 5
}
```

#### `PATCH /api/me/settings`
Auth:
- required

Request:
```json
{
  "targetScoreThreshold": 85,
  "passScoreThreshold": 65,
  "retryEnabled": true,
  "dailyQuestionCount": 2,
  "preferredLanguage": "en"
}
```

Response:
```json
{
  "targetScoreThreshold": 85,
  "passScoreThreshold": 65,
  "retryEnabled": true,
  "dailyQuestionCount": 2,
  "preferredLanguage": "en"
}
```

Notes:
- `passScoreThreshold` must not exceed `targetScoreThreshold`
- `preferredLanguage` should use one of the supported locale codes, initially `ko` or `en`

#### `PUT /api/me/target-companies`
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

### Resume
#### `GET /api/resumes`
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
        "fileName": "resume-v1.pdf",
        "fileType": "application/pdf",
        "summaryText": "First version",
        "parsingStatus": "completed",
        "isActive": false,
        "uploadedAt": "2026-03-11T04:00:00Z"
      }
    ]
  }
]
```

Notes:
- each version is immutable and remains the anchor for downstream extracted skills, experiences, and risks
- `parsingStatus` is currently `pending` or `completed` and is the forward-compatible hook for PDF parsing lifecycle
- future resume intelligence APIs should reference `resumeVersionId`

#### `POST /api/resumes`
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
- `ResumeDto`, same shape as one list item from `GET /api/resumes`

#### `POST /api/resumes/{resumeId}/versions`
Auth:
- required

Request:
```json
{
  "fileUrl": "https://files.example.com/resume-v2.pdf",
  "fileName": "resume-v2.pdf",
  "fileType": "application/pdf",
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
  "fileName": "resume-v2.pdf",
  "fileType": "application/pdf",
  "summaryText": "Second version",
  "parsingStatus": "completed",
  "isActive": false,
  "uploadedAt": "2026-03-11T04:00:00Z"
}
```

Notes:
- current implementation accepts a JSON body and can persist imported text or pre-parsed metadata
- this contract remains valid for admin import, tests, and parser-bypass flows
- product direction is to keep the same version concept while allowing either imported metadata or uploaded PDF files

#### `POST /api/resumes/{resumeId}/versions/upload`
Auth:
- required

Content type:
- `multipart/form-data`

Request:
- form field `file` containing a PDF resume
- optional field `summaryText`

Response:
```json
{
  "id": 23,
  "versionNo": 3,
  "fileUrl": "/api/resume-versions/23/file",
  "fileName": "resume-v3.pdf",
  "fileType": "application/pdf",
  "fileSizeBytes": 182341,
  "summaryText": null,
  "parsingStatus": "pending",
  "parseStartedAt": "2026-03-12T05:00:00Z",
  "parseCompletedAt": null,
  "parseErrorMessage": null,
  "isActive": false,
  "uploadedAt": "2026-03-12T05:00:00Z"
}
```

Behavior:
- uploading a PDF creates a new immutable resume version immediately
- the backend stores the original file and attempts PDF text extraction
- current implementation persists `rawText` and deterministic extraction snapshots from that parsed content
- planned additive behavior will run an LLM-backed structured extraction step after raw PDF parsing
- extracted skills, experiences, and risks stay tied to the uploaded `resumeVersionId`
- parse failure marks the new version as failed without mutating older active versions
- oversized uploads return `413 PAYLOAD_TOO_LARGE`

#### `GET /api/resume-versions/{versionId}`
Auth:
- required

Response:
- `ResumeVersionDto`, same shape as the items inside `GET /api/resumes`

Notes:
- use this endpoint to poll upload status after a PDF version is created
- `parsingStatus` is currently one of `pending`, `completed`, or `failed`
- `llmExtractionStatus` is currently one of `pending`, `completed`, `skipped`, `fallback`, or `failed`

#### `GET /api/resume-versions/{versionId}/extraction`
Auth:
- required

Purpose:
- inspect structured extraction status and metadata separately from raw file parsing

Response:
```json
{
  "resumeVersionId": 22,
  "rawParsingStatus": "completed",
  "llmExtractionStatus": "skipped",
  "llmModel": null,
  "llmPromptVersion": null,
  "startedAt": "2026-03-12T03:00:00Z",
  "completedAt": "2026-03-12T03:00:00Z",
  "errorMessage": null
}
```

#### `GET /api/resume-versions/{versionId}/file`
Auth:
- required

Response:
- binary file stream for the stored resume version
- current implementation returns `Content-Type: application/pdf` for uploaded PDF versions

### Rich Resume Structure API
These endpoints are implemented and return version-scoped structured resume snapshots.

#### `GET /api/resume-versions/{versionId}/profile`
Purpose:
- return top-level resume identity and summary information

#### `GET /api/resume-versions/{versionId}/contacts`
Purpose:
- return typed contact channels and external links in stable display order

#### `GET /api/resume-versions/{versionId}/competencies`
Purpose:
- return long-form competency statements that should not be flattened into simple skills

#### `GET /api/resume-versions/{versionId}/projects`
Purpose:
- return project or initiative records nested under work experience when available

Current implementation:
- returns project records scoped to one resume version
- each project can include:
  - `title`
  - `summaryText`
  - `contentText`
  - `projectCategoryCode`
  - `projectCategoryName`
  - `tags`

Current tag shape:
```json
[
  {
    "id": 1,
    "tagName": "backend",
    "tagType": "domain"
  },
  {
    "id": 2,
    "tagName": "payments",
    "tagType": "business"
  }
]
```

Intent:
- frontend can render a project list with title, content, tag chips, and category labels

#### `GET /api/resume-versions/{versionId}/achievements`
Purpose:
- return measurable outcome claims and interview-defense-worthy metrics

#### `GET /api/resume-versions/{versionId}/education`
Purpose:
- return education history for one immutable resume version

#### `GET /api/resume-versions/{versionId}/certifications`
Purpose:
- return certifications, licenses, or score-style credentials

#### `GET /api/resume-versions/{versionId}/awards`
Purpose:
- return awards, honors, and competition results

#### `POST /api/resume-versions/{versionId}/activate`
Auth:
- required

Response:
```json
{
  "resumeId": 10,
  "versionId": 22,
  "versionNo": 2,
  "activatedAt": "2026-03-11T04:00:00Z"
}
```

### Questions
#### `GET /api/questions`
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

Notes:
- inactive questions are excluded by default
- `tag`, `difficulty`, and `status` are matched case-insensitively

#### `GET /api/questions/{questionId}`
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
  "referenceAnswers": [],
  "userProgressSummary": {
    "currentStatus": "in_progress",
    "latestScore": 72.5,
    "bestScore": 81.0,
    "totalAttemptCount": 3,
    "lastAnsweredAt": "2026-03-11T04:00:00Z",
    "nextReviewAt": "2026-03-13T04:00:00Z",
    "masteryLevel": "intermediate"
  }
}
```

Notes:
- `userProgressSummary` is only included when the request is authenticated
- current `learningMaterials` remains the generic question-linked study resource list
- future tree, model-answer, or skill metadata should be additive or exposed via dedicated endpoints

### Home and Daily Cards
#### `GET /api/home`
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
    "cardDate": "2026-03-11",
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
      "scheduledFor": "2026-03-11T04:00:00Z"
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

Notes:
- this is the current home contract
- future radar or resume-risk previews should be added as optional fields

#### `POST /api/daily-cards/{dailyCardId}/open`
Auth:
- required

Response:
```json
{
  "id": 15,
  "status": "opened",
  "openedAt": "2026-03-11T04:00:00Z"
}
```

### Answers
#### `POST /api/questions/{questionId}/answers`
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
  "analysis": {
    "answerAttemptId": 200,
    "detailedFeedback": "The answer is directionally strong, but it still needs clearer tradeoff framing and stronger measurable evidence.",
    "strengthPoints": ["Stays on the core topic", "Shows a usable answer flow"],
    "improvementPoints": ["Add metrics", "Explain tradeoffs explicitly"],
    "missedPoints": ["Alternative comparison", "Validation criteria"],
    "modelAnswer": {
      "sourceType": "ai_generated",
      "contentLocale": "ko",
      "text": "A stronger answer would define the context, explain the decision criteria, describe the implementation, and close with measurable validation."
    }
  },
  "progressStatus": "in_progress",
  "nextReviewAt": null,
  "archiveDecision": false
}
```

Notes:
- low-quality answers can create or update a retry queue item
- `answerMode` values like `skip` and `unanswered` trigger retry behavior
- future analysis depth should not remove or rename `scoreSummary`
- additive `analysis` may now include richer AI-style feedback and a model answer for the submitted attempt

#### `GET /api/questions/{questionId}/answers`
Auth:
- required

Response:
```json
[
  {
    "id": 200,
    "attemptNo": 2,
    "answerMode": "text",
    "submittedAt": "2026-03-11T04:00:00Z",
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

#### `GET /api/answer-attempts/{answerAttemptId}`
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
    "submittedAt": "2026-03-11T04:00:00Z"
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
    "lastAnsweredAt": "2026-03-11T04:00:00Z",
    "nextReviewAt": null,
    "masteryLevel": "intermediate"
  }
}
```

### Review Queue
#### `GET /api/review-queue`
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
    "scheduledFor": "2026-03-11T04:00:00Z",
    "status": "pending"
  }
]
```

#### `POST /api/review-queue/{queueId}/skip`
Auth:
- required

Response:
```json
{
  "id": 31,
  "status": "skipped",
  "updatedAt": "2026-03-11T04:00:00Z"
}
```

#### `POST /api/review-queue/{queueId}/done`
Auth:
- required

Response:
```json
{
  "id": 31,
  "status": "done",
  "updatedAt": "2026-03-11T04:00:00Z"
}
```

### Archive
#### `GET /api/archive`
Auth:
- required

Response:
```json
[
  {
    "questionId": 100,
    "title": "Design a resilient queue",
    "difficulty": "HARD",
    "archivedAt": "2026-03-11T04:00:00Z",
    "bestScore": 92,
    "totalAttemptCount": 3,
    "sourceType": "practice",
    "sourceLabel": "Practice",
    "sourceSessionId": null,
    "sourceSessionQuestionId": null,
    "isFollowUp": false
  }
]
```

Planned additive behavior:
- archived questions that originated inside interview sessions return:
  - `sourceType = "interview"`
  - `sourceLabel = "Interview"`
  - `sourceSessionId`
  - `sourceSessionQuestionId`
  - `isFollowUp`
- archive remains question-level even when the source is an interview session

### Feed
#### `GET /api/feed`
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

## Implemented Additive API
These contracts extend the current system without replacing the baseline endpoints above.

### Resume Intelligence
#### `GET /api/resumes/latest`
Purpose:
- return the user's primary or most recent resume with its versions

Planned richer extraction coverage for real resumes:
- top-level headline and summary
- contact channels and external profile links
- competency/strength statements
- employment timeline with company and role
- project timeline under each employment section
- quantified achievements and outcome claims
- education, awards, and certifications

#### `GET /api/resume-versions/{versionId}/skills`
Purpose:
- return extracted or confirmed skills for one immutable resume version

Response:
```json
{
  "resumeVersionId": 22,
  "items": [
    {
      "skillName": "Spring Boot",
      "skillCategory": "BACKEND",
      "sourceText": "Built REST APIs with Spring Boot",
      "confidenceScore": 0.94,
      "confirmed": true
    }
  ],
  "generatedAt": "2026-03-11T04:00:00Z"
}
```

Notes:
- future additive fields may include `sourceType`, `confidenceScore`, and extraction rationale for traceability

#### `GET /api/resume-versions/{versionId}/experiences`
Purpose:
- return structured experience claims extracted from the active or selected resume version

#### `GET /api/resume-versions/{versionId}/risks`
Purpose:
- expose resume claims that likely require deeper interview defense

Response:
```json
{
  "resumeVersionId": 22,
  "items": [
    {
      "id": 901,
      "title": "Performance improvement claim",
      "description": "Response time improved by 40 percent",
      "severity": "HIGH",
      "riskType": "impact_claim"
    }
  ]
}
```

#### `POST /api/resume-versions/{versionId}/re-extract`
Auth:
- required

Purpose:
- re-run structured extraction for an existing immutable resume version without uploading a new PDF

Response:
```json
{
  "resumeVersionId": 22,
  "parsingStatus": "completed",
  "llmExtractionStatus": "skipped"
}
```

### Question Tree and Follow-Up
#### `GET /api/questions/{questionId}/tree`
Purpose:
- expose the current question as a rooted follow-up tree with user progress-aware node states

Response:
```json
{
  "root": {
    "questionId": 100,
    "title": "What is a transaction?",
    "nodeStatus": "answered",
    "children": [
      {
        "questionId": 101,
        "title": "Explain ACID.",
        "nodeStatus": "answered",
        "children": []
      },
      {
        "questionId": 102,
        "title": "Explain isolation levels.",
        "nodeStatus": "weak",
        "children": []
      }
    ]
  }
}
```

#### `GET /api/questions/{questionId}/recommended-followups`
Purpose:
- return the next best child or related questions based on progress, resume signals, and weak skill coverage

#### `GET /api/questions/resume-based`
Purpose:
- return questions ranked from the user's latest resume-derived skills and linked resume risks

### Question Reference Content
Current support:
- `GET /api/questions/{questionId}` includes `learningMaterials` and additive `referenceAnswers`

Implemented additive endpoints:
#### `GET /api/questions/{questionId}/reference-answers`
Purpose:
- return model answers or answer outlines for a question in stable display order
- if no locale-matching shared reference answers exist yet, the backend may lazily generate and persist default AI reference answers
- if the current user added personal reference answers, append them after shared content
- if the question is a private imported practical interview asset owned by the current user, the response may also append one additive imported answer row with:
  - `sourceType = real_interview_import`
  - `title = Imported real interview answer summary`
  - `answerFormat = summary` or `transcript_excerpt`

#### `POST /api/questions/{questionId}/reference-answers`
Purpose:
- add a user-owned reference answer or answer outline for the authenticated user
- user-added answers are private to that user and returned on subsequent detail/reference-answer reads

Response:
```json
[
  {
    "id": 3001,
    "title": "Concise strong answer",
    "answerText": "I would start by clarifying throughput and durability requirements...",
    "answerFormat": "full_answer",
    "sourceType": "ai_generated",
    "sourceLabel": "AI generated",
    "contentLocale": "ko",
    "isUserGenerated": false,
    "isOfficial": true,
    "displayOrder": 1
  }
]
```

#### `GET /api/questions/{questionId}/learning-materials`
Purpose:
- return related learning materials for a question in stable display order
- if no locale-matching shared learning materials exist yet, the backend may lazily generate and persist default AI learning materials
- if the current user added personal learning materials, append them after shared content

#### `POST /api/questions/{questionId}/learning-materials`
Purpose:
- add a user-owned learning material or note for the authenticated user
- user-added learning materials are private to that user and returned on subsequent detail/material reads

Response:
```json
[
  {
    "id": 501,
    "title": "Isolation Levels Explained",
    "materialType": "article",
    "sourceType": "ai_generated",
    "sourceLabel": "AI generated",
    "description": "Short refresher on read phenomena and tradeoffs.",
    "contentUrl": "https://example.com/isolation-levels",
    "sourceName": "DB Guide",
    "contentLocale": "ko",
    "isUserGenerated": false,
    "difficultyLevel": "intermediate",
    "estimatedMinutes": 12,
    "isOfficial": true,
    "displayOrder": 1
  }
]
```

### Imported Real Interview Question Assets
Implemented additive behavior:
- imported practical interview questions are now backed by private generated `questions` assets
- `interview_record_questions.linked_question_id` points to the generated question asset
- `GET /api/interview-records/{recordId}/questions` now exposes `linkedQuestionId`
- archive rows for `sourceType = real_interview` now use that linked question asset id in `questionId`
- `replay_mock` seed turns may also use the linked question asset id as `questionId`

Question detail behavior:
- `GET /api/questions/{questionId}` now supports imported real interview question assets when the authenticated user owns the source interview record
- imported assets stay private and are not intended for anonymous question detail reads or public catalog listing
- detail payload now includes additive `practicalInterviewContext` with:
  - `sourceInterviewRecordId`
  - `sourceInterviewQuestionId`
  - `companyName`
  - `roleName`
  - `interviewDate`
  - `interviewType`
  - `questionType`
  - `topicTags`
  - `intentTags`
  - `interviewerProfileId`
  - `importedAnswerSummary`
  - `importedAnswerText`
  - `isFollowUp`

### Answer Analysis
#### `GET /api/answer-attempts/{answerAttemptId}/analysis`
Purpose:
- return the persisted deterministic answer-analysis record associated with an answer attempt

Response:
```json
{
  "answerAttemptId": 501,
  "overallScore": 72,
  "depthScore": 68,
  "clarityScore": 74,
  "accuracyScore": 70,
  "exampleScore": 71,
  "tradeoffScore": 69,
  "confidenceScore": 63,
  "strengthSummary": "The answer is coherent and includes a concrete engineering decision.",
  "weaknessSummary": "Tradeoff discussion and depth are still light.",
  "recommendedNextStep": "Add a concrete failure mode and explain the tradeoff."
}
```

### Skill Radar and Gap Analysis
#### `GET /api/skills/radar`
Purpose:
- return current skill-category scores derived from answer history

Response:
```json
{
  "categories": [
    {
      "categoryCode": "DATABASE",
      "label": "Database",
      "score": 44,
      "benchmarkScore": 61,
      "gapScore": 17
    }
  ],
  "updatedAt": "2026-03-11T04:00:00Z"
}
```

#### `GET /api/skills/gaps`
Purpose:
- return categories or skills ranked by readiness gap

#### `GET /api/skills/progress`
Purpose:
- return trend data for category improvements over time

### Home Extensions
#### Future additive fields for `GET /api/home`
These should be optional and backward compatible:

```json
{
  "skillRadarPreview": [
    {
      "categoryCode": "DATABASE",
      "score": 44,
      "gapScore": 17
    }
  ],
  "weakSkillHighlights": [
    {
      "categoryCode": "DATABASE",
      "label": "Database",
      "gapScore": 17
    }
  ],
  "resumeRiskPreview": [
    {
      "questionId": 210,
      "title": "How did you measure the 40 percent latency improvement?",
      "severity": "HIGH"
    }
  ]
}
```

### Interview Sessions
#### `GET /api/interview-sessions`
Purpose:
- return interview history as session-level records ordered by most recent first

Response:
```json
[
  {
    "id": 71,
    "sessionType": "resume_mock",
    "status": "completed",
    "resumeVersionId": 22,
    "startedAt": "2026-03-13T03:00:00Z",
    "endedAt": "2026-03-13T03:18:00Z",
    "questionCount": 5,
    "answeredCount": 5,
    "averageScore": 81
  }
]
```

#### `POST /api/interview-sessions`
Purpose:
- create a minimal mock-interview session that reuses the existing question, answer, and review model

Request:
```json
{
  "sessionType": "resume_mock",
  "interviewMode": "full_coverage",
  "questionCount": 3,
  "resumeVersionId": 22,
  "seedQuestionIds": [100, 101]
}
```

Notes:
- supported `sessionType` values are `resume_mock`, `review_mock`, and `topic_mock`
- `resume_mock` now requires an explicit `resumeVersionId`
- `seedQuestionIds` are optional and are used as a starting pool or fallback hint, not a replacement for server-side selection
- for `resume_mock`, the backend now tries to generate the opening question from the selected resume version
- if opener generation is unavailable or fails validation, the backend falls back to deterministic server-side question selection
- implemented `interviewMode` values:
  - `quick_screen`
  - `mock_30`
  - `mock_60`
  - `free_interview`
  - `full_coverage`
- `full_coverage` creates a planner-driven interview session that tries to cover all interviewable resume evidence units, not just a random subset of resume-linked questions
- opener generation now persists one or more `resumeEvidence` snippets that explain which project or experience record triggered the question
- current resume-grounded interview generation is intentionally scoped to project and experience evidence only

#### `GET /api/interview-sessions/{sessionId}`
Purpose:
- return current session status, ordered questions, current question, and summary counts
- for `full_coverage`, session `summary` should also include additive `facetSummaries`, `weakFacetSummaries`, and `skippedFacetSummaries` so in-session UI can surface weak or skipped resume facets without calling the result endpoints first

Implemented additive fields on session questions:
- `promptText`
- `bodyText`
- `sourceType`
- `parentSessionQuestionId`
- `isFollowUp`
- `depth`
- `categoryName`
- `tags`
- `focusSkillNames`
- `resumeContextSummary`
- `generationRationale`
- `generationStatus`
- `llmModel`
- `llmPromptVersion`

Implemented additive fields for resume-grounded question evidence:
- `resumeEvidence`

Recommended `resumeEvidence` item shape:
```json
{
  "type": "resume_sentence",
  "section": "project",
  "label": "Payments migration",
  "snippet": "Led phased rollout of the payments migration with rollback safeguards.",
  "sourceRecordType": "resume_project_snapshot",
  "sourceRecordId": 123,
  "confidence": 0.92,
  "startOffset": null,
  "endOffset": null
}
```

Current semantics:
- the opening session question may be AI-generated from the selected resume version
- session questions should be rendered from snapshot fields first and should not depend on catalog `questionId`
- `resumeEvidence` is additive metadata and should be used as a compact `Based on your resume` explanation block, not as the main prompt
- clients must not assume evidence is always present; an empty array or missing field remains valid
- evidence snippets should be short excerpts, not full resume paragraphs

#### `POST /api/interview-sessions/{sessionId}/answers`
Purpose:
- submit an answer for a specific session question while preserving the standard answer scoring and retry side effects

Behavior:
- `resume_mock` sessions should try to generate the next follow-up through the interview LLM client when configured
- if LLM generation is unavailable or fails validation, the backend may fall back to catalog follow-ups or the remaining queued questions
- the inserted follow-up must still be persisted as an immutable session question snapshot
- follow-up generation now persists fresh `resumeEvidence` snippets when the next question is anchored to a project or experience record from the resume

#### `POST /api/interview-sessions/{sessionId}/next-question`
Purpose:
- advance only after the current question has already been answered or skipped

Current `full_coverage` behavior:
- choose the next question based on uncovered resume evidence items first
- avoid relying on unconstrained AI generation alone when coverage completion is the goal
- one project or experience record may contribute multiple coverage evidence items when the resume contains multiple distinct claims, actions, or outcomes
- follow-up generation should prefer drilling into a narrower unresolved claim, metric, trade-off, or STAR facet from the same project instead of repeating broad overview questions
- follow-up generation should use session memory so that, within the same project or experience record, already-covered facets are deprioritized in favor of unresolved facets and different supporting snippets
- when multiple unresolved facets exist for the same record, the planner should prefer a practical interview path such as `problem -> action -> result -> metric -> tradeoff` before falling back to less structured ordering
- once no `unasked` evidence remains, the planner should recover weak facets before skipped facets, and revisit skipped facets before falling back to already-defended evidence
- deterministic coverage questions and AI-generated prompts should also vary their interviewer angle by facet, for example context/constraints for `problem`, implementation/ownership for `action`, validation/impact for `result`, measurement/trustworthiness for `metric`, and alternatives/downside for `tradeoff`
- when revisiting `weak` facets, deterministic and AI-generated planner questions should switch into a stronger re-validation / evidence-challenge tone instead of asking another broad overview prompt
- AI follow-up generation should also bias its style by recent answer quality, for example weaker answers toward evidence challenge / STAR completion and stronger answers toward scenario extension or trade-off pressure-testing
- reaching `overallCoveragePercent = 100` does not automatically end the session
- after all evidence items have been covered, the backend may continue generating additional deep-dive questions against previously covered evidence until the user ends the interview or the session is otherwise completed
- deterministic extra questions generated after 100% coverage may expose `generationStatus = coverage_extended`
- `GET /api/interview-sessions/{sessionId}/coverage` and `/resume-map` should expose additive record-level facet summaries so the result screen can highlight which project or experience still has weak, skipped, defended, or unasked facets without recomputing from raw evidence items

Advance semantics:
- if the current question is still unanswered, the backend returns `409 CONFLICT`
- if the current question has already been answered or skipped, the backend returns the next queued question
- if no queued question exists, planner-driven modes such as `full_coverage` may generate the next question lazily
- if no remaining question can be resolved, the session is completed

#### `POST /api/interview-sessions/{sessionId}/skip-question`
Purpose:
- mark the current question as skipped and then advance the session to the next question

Request shape:
```json
{
  "sessionQuestionId": 301
}
```

Behavior:
- the target session question must belong to the active session
- answered questions cannot be skipped
- skipped questions remain in session history with `status = skipped`
- in `full_coverage`, linked resume evidence items move to `coverageStatus = skipped`
- the response shape matches the advance response and returns the next current question when available

#### `GET /api/interview-sessions/{sessionId}/coverage`
Purpose:
- return session-level resume coverage progress for planner-driven interview modes such as `full_coverage`

Current response shape:
```json
{
  "sessionId": 71,
  "interviewMode": "full_coverage",
  "overallCoveragePercent": 84,
  "defendedCoveragePercent": 61,
  "evidenceItems": [
    {
      "id": 9001,
      "section": "project",
      "label": "Payments migration",
      "snippet": "Led phased rollout of the payments migration with rollback safeguards.",
      "facet": "action",
      "sourceRecordType": "resume_project_snapshot",
      "sourceRecordId": 12,
      "displayOrder": 1,
      "coverageStatus": "defended",
      "linkedQuestionIds": [3001, 3004]
    }
  ]
}
```

Coverage status semantics:
- `unasked`: no interview turn has been linked to this evidence item yet
- `asked`: a question has been asked from this evidence item, but the linked answer has not yet been evaluated
- `defended`: the latest linked answer met the current defended threshold
- `weak`: the latest linked answer did not meet the defended threshold
- `skipped`: the linked session question was explicitly skipped by the user

#### `GET /api/interview-sessions/{sessionId}/resume-map`
Purpose:
- return a result-time resume-to-question map so the frontend can show related questions when hovering or clicking one resume sentence or structured evidence record

Current response shape:
```json
{
  "sessionId": 71,
  "resumeVersionId": 22,
  "evidenceItems": [
    {
      "section": "award",
      "label": "Engineering Excellence Award",
      "snippet": "Received the Engineering Excellence Award for the payments migration.",
      "facet": "result",
      "sourceRecordType": "resume_award_item",
      "sourceRecordId": 41,
      "displayOrder": 3,
      "coverageStatus": "defended",
      "primaryQuestionCount": 1,
      "followUpQuestionCount": 1,
      "relatedQuestions": [
        {
          "sessionQuestionId": 3007,
          "title": "What specific outcome led to that award?",
          "sourceType": "ai_follow_up",
          "orderIndex": 4,
          "status": "answered",
          "isFollowUp": true
        }
      ]
    }
  ]
}
```

Recommended result-view semantics:
- the first implementation should use a structured resume viewer backed by parsed resume sections such as experiences and projects rather than PDF coordinate overlays
- `sourceRecordType` and `sourceRecordId` should be treated as the stable join key between resume section APIs and the session result map
- `displayOrder` should be used to keep the result-time resume viewer aligned with the original parsed resume section ordering
- `facet` can be used as additive metadata for debugging, richer badges, or planner visualization of whether the question targeted a problem, action, result, trade-off, or metric slice
- one resume evidence block may map to multiple related interview turns
- hover should show a lightweight related-question preview, not necessarily a fully expanded question card
- click should pin the related questions and navigate or scroll to the linked session question card when practical
- `primaryQuestionCount` and `followUpQuestionCount` can drive badges or preview density without forcing the client to pre-expand the full question list
- clients should visually distinguish `coverageStatus` values such as `defended`, `weak`, `skipped`, and `unasked` in the result viewer
- current resume-grounded result mapping is intentionally scoped to project and experience evidence only

#### `GET /api/archive`
Current additive behavior for interview-originated records:
- archive remains question-level
- asked interview turns now also appear in archive even when they were not archived through the classic mastery flow
- interview-originated archive rows carry:
  - `sourceType = interview`
  - `sourceLabel = Interview`
  - `sourceSessionId`
  - `sourceSessionQuestionId`
  - `isFollowUp`
- queued future interview questions that were not yet shown to the user should not appear in archive

### Still Planned
The following interview features are still intentionally deferred:
- live or streaming mock interview sessions
- AI realtime interactions that bypass the standard answer-attempt model

### Implemented Practical Interview Record Foundation

Purpose:
- ingest one real interview recording, structure it into reviewable transcript/question assets, and expose the interviewer-style summary needed for later replay simulation work

Implemented resources:
- `POST /api/interview-records`
- `GET /api/interview-records`
- `GET /api/interview-records/{recordId}`
- `GET /api/interview-records/{recordId}/transcript`
- `PATCH /api/interview-records/{recordId}/transcript/segments/{segmentId}`
- `POST /api/interview-records/{recordId}/retry-transcription`
- `GET /api/interview-records/{recordId}/questions`
- `GET /api/interview-records/{recordId}/review`
- `PATCH /api/interview-records/{recordId}/review`
- `GET /api/interview-records/{recordId}/analysis`
- `GET /api/interview-records/{recordId}/interviewer-profile`
- `POST /api/interview-records/{recordId}/confirm`

Current request and response semantics:
- `POST /api/interview-records` is `multipart/form-data`
- current required upload field:
  - `file`
- currently supported optional create fields:
  - `companyName`
  - `roleName`
  - `interviewDate`
  - `interviewType`
  - `linkedResumeVersionId`
  - `linkedJobPostingId`
  - `transcriptText`
- the uploaded audio asset is stored without mutating the original file name
- when `transcriptText` is provided, the backend performs deterministic structuring immediately, optionally applies AI refinement when interview LLM settings are configured, and returns:
  - `transcriptStatus = confirmed`
  - `analysisStatus = completed`
- when `transcriptText` is omitted, the backend now attempts automatic transcript extraction from the uploaded audio
- automatic extraction now uses an explicit lifecycle:
  - `pending`
  - `processing`
  - `failed`
  - `confirmed`
- when automatic extraction succeeds, the backend runs the same structuring pipeline and moves the record to:
  - `transcriptStatus = confirmed`
  - `analysisStatus = completed`
- when automatic extraction is not configured, create currently returns:
  - `transcriptStatus = failed`
  - `analysisStatus = failed`
  - `transcriptErrorCode = transcription_not_configured`
- when automatic extraction fails after a queued or in-flight attempt, the record moves to:
  - `transcriptStatus = failed`
  - `analysisStatus = failed`
  - `transcriptErrorCode` such as `transcription_failed`, `empty_transcript`, or `processing_timeout`
- detail and transcript resources now also expose:
  - `transcriptErrorCode`
  - `transcriptErrorMessage`
  - `transcriptRetryCount`
  - `transcriptLastAttemptAt`
  - `transcriptProcessingStartedAt`
  - `transcriptNextRetryAt`
- `POST /api/interview-records/{recordId}/retry-transcription` re-queues extraction for non-confirmed records unless a non-timed-out extraction is already running

Current transcript semantics:
- transcript resources keep three staged values on the record:
  - `rawTranscript`
  - `cleanedTranscript`
  - `confirmedTranscript`
- transcript detail also exposes ordered `segments`
- record detail now also exposes structuring provenance:
  - `deterministicSummary`
  - `aiEnrichedSummary`
  - `overallSummary`
  - `structuringStage`
- each segment includes:
  - `speakerType`
  - `rawText`
  - `cleanedText`
  - `confirmedText`
  - `confidenceScore`
  - `sequence`
- `PATCH /api/interview-records/{recordId}/transcript/segments/{segmentId}` currently accepts additive edits for:
  - `speakerType`
  - `cleanedText`
  - `confirmedText`
- patching one segment rebuilds the confirmed transcript and re-derives structured questions, answers, follow-up edges, and interviewer profile in the same transaction

Current structured extraction semantics:
- `GET /api/interview-records/{recordId}/questions` returns ordered imported question assets
- each question may include:
  - `questionType`
  - `topicTags`
  - `intentTags`
  - `structuringSource`
  - optional derived resume linkage fields
  - one structured answer snapshot
- answer snapshots currently expose:
  - `summary`
  - `confidenceMarkers`
  - `weaknessTags`
  - `strengthTags`
  - `structuringSource`
- `GET /api/interview-records/{recordId}/analysis` currently returns:
  - `totalQuestions`
  - `totalAnswers`
  - `followUpCount`
  - `questionTypeDistribution`
  - `weakAnswerQuestionIds`
  - `topicTags`
  - `structuringStage`
  - `overallSummary`
- `GET /api/interview-records/{recordId}/review` currently returns review provenance for the practical-interview workflow:
  - `structuringStage`
  - `requiresConfirmation`
  - `deterministicSummary`
  - `aiEnrichedSummary`
  - `overallSummary`
  - `confirmedAt`
  - `totalSegmentCount`
  - `editedSegmentCount`
    - currently means pending segment-level diff count where `confirmedText` still differs from the cleaned baseline
  - `totalQuestionCount`
  - `changedQuestionCount`
  - `weakAnswerCount`
  - `followUpQuestionCount`
  - `questionSourceCounts`
  - `answerSourceCounts`
  - `interviewerProfileSource`
  - `questionFilterSummary`
    - additive filter counts for practical interview review tables
    - includes:
      - `allQuestions`
      - `primaryQuestions`
      - `followUpQuestions`
      - `weakAnswerQuestions`
      - `weakFollowUpQuestions`
      - `confirmedQuestions`
  - `questionDistributionSummary`
    - additive aggregate for review filter chips and distribution panels
    - includes:
      - `questionTypeCounts`
      - `topicTagCounts`
  - `questionOriginSummary`
    - additive aggregate for question-origin badges and filters
    - includes:
      - `resumeLinkedQuestions`
      - `jobPostingLinkedQuestions`
      - `hybridLinkedQuestions`
      - `generalQuestions`
  - `replayReadiness`
    - additive aggregate for replay CTA state
    - includes:
      - `ready`
      - `replayableQuestionCount`
      - `linkedQuestionCount`
      - `unlinkedQuestionCount`
      - `followUpThreadCount`
      - `hasInterviewerProfile`
      - `recommendedReplayMode`
      - `recommendedReplayModeLabel`
      - `statusBadgeText`
      - `statusVariant`
      - `statusSummary`
      - `primaryCtaLabel`
      - `blockedCtaLabel`
      - `blockers[]`
      - `blockerDetails[]`
        - `code`
        - `label`
        - `description`
        - `severity`
        - `priority`
        - `highlightVariant`
        - `sortOrder`
        - `recommendedAction`
        - `recommendedActionLabel`
        - `recommendedActionTarget`
        - `recommendedActionTargetPayload`
  - `reviewLaneSummary`
    - additive dashboard aggregate for transcript/question/thread review lanes
    - includes:
      - `transcript`
      - `question`
      - `thread`
    - each lane item includes:
      - `sortOrder`
      - `highlightVariant`
      - `badgeText`
      - `summaryText`
      - `recommendedTab`
      - `defaultExpanded`
      - `analyticsKey`
      - `trackingContext`
      - `helpText`
      - `whyItMatters`
      - `accessibilityLabel`
      - `screenReaderSummary`
      - `emptyStateCtaAction`
      - `emptyStateCtaLabel`
      - `emptyStateCtaTarget`
      - `emptyStateCtaTargetPayload`
      - `totalCount`
      - `readyCount`
      - `needsReviewCount`
      - `readiness`
      - `severity`
      - `highestPriority`
      - `primaryAction`
      - `primaryActionLabel`
      - `primaryActionTarget`
      - `primaryActionTargetPayload`
      - `secondaryAction`
      - `secondaryActionLabel`
      - `secondaryActionTarget`
      - `secondaryActionTargetPayload`
      - `emptyStateMessage`
      - `completionCtaAction`
      - `completionCtaLabel`
      - `completionCtaTarget`
      - `completionCtaTargetPayload`
      - `completionMessage`
      - `blockingReasons[]`
      - `blockingReasonDetails[]`
        - `code`
        - `label`
        - `description`
        - `severity`
        - `priority`
        - `highlightVariant`
        - `sortOrder`
        - `recommendedAction`
        - `recommendedActionLabel`
        - `recommendedActionTarget`
        - `recommendedActionTargetPayload`
  - `transcriptIssueSummary`
    - additive aggregate for review-priority panels
    - includes:
      - `lowConfidenceSegmentCount`
      - `lowConfidenceSegmentSequences[]`
      - `speakerOverrideSegmentCount`
      - `speakerOverrideSegmentSequences[]`
      - `confirmedTextOverrideCount`
      - `editedSegmentSequences[]`
      - `resolvedIssueCount`
      - `unresolvedIssueCount`
      - `confirmationReadiness`
      - `reviewerLaneCounts`
      - `topPrioritySegmentActions[]`
      - `segmentActions[]`
        - `sequence`
        - `issueTypes[]`
        - `recommendedAction`
        - `triageReason`
        - `ctaLabel`
        - `severity`
        - `priority`
        - `reviewerLane`
        - `linkedQuestionId`
        - `threadRootQuestionId`
        - `deepLink`
        - `replayLaunchPreset`
  - `answerQualitySummary`
    - additive aggregate for answer-quality overview panels
    - includes:
      - `answeredQuestionCount`
      - `weakAnswerCount`
      - `strengthTaggedAnswerCount`
      - `quantifiedAnswerCount`
      - `structuredAnswerCount`
      - `tradeoffAwareAnswerCount`
      - `uncertainAnswerCount`
      - `detailedAnswerCount`
  - `timelineNavigation`
    - additive navigation anchors for transcript/question/thread cross-linking
    - `items[]` includes:
      - `questionId`
      - `orderIndex`
      - `parentQuestionId`
      - `threadRootQuestionId`
      - `questionSegmentStartSequence`
      - `questionSegmentEndSequence`
      - `answerSegmentStartSequence`
      - `answerSegmentEndSequence`
  - `actionRecommendations`
    - additive CTA guidance for review UIs
    - includes:
      - `primaryAction`
      - `primaryActionLabel`
      - `primaryActionTarget`
      - `primaryActionTargetPayload`
      - `availableActions[]`
      - `availableActionLabels`
      - `availableActionTargets`
      - `availableActionTargetPayloads`
      - `blockingReasons[]`
      - `blockingReasonDetails[]`
        - `code`
        - `label`
        - `description`
        - `severity`
        - `priority`
        - `highlightVariant`
        - `sortOrder`
        - `recommendedAction`
        - `recommendedActionLabel`
        - `recommendedActionTarget`
        - `recommendedActionTargetPayload`
      - `canConfirm`
      - `canReplay`
  - `replayLaunchPreset`
    - additive preset for `POST /api/interview-sessions` with `sessionType = replay_mock`
    - includes:
      - `sessionType`
      - `sourceInterviewRecordId`
      - `replayMode`
      - `recommendedReplayModeLabel`
      - `recommendedQuestionCount`
      - `seedQuestionIds[]`
      - `availableReplayModes[]`
      - `availableReplayModeLabels`
      - `presetTitle`
      - `presetDescription`
      - `launchButtonLabel`
  - `provenanceComparisonSummary`
    - additive comparison summary for deterministic vs ai_enriched vs confirmed review panels
    - includes:
      - `aiRefinementApplied`
      - `confirmedVersionAvailable`
      - `summaryChangedFromDeterministic`
      - `changedQuestionCountFromDeterministic`
      - `changedAnswerCountFromDeterministic`
      - `currentQuestionSource`
      - `currentAnswerSource`
      - `currentInterviewerProfileSource`
  - `questionSummaries[]`
    - each summary also includes `deepLink`
      - `questionDetailQuestionId`
      - `archiveSourceType`
      - `sourceInterviewRecordId`
      - `sourceInterviewQuestionId`
      - `canStartReplayMock`
      - `replaySessionType`
    - `questionId`
    - `linkedQuestionId`
    - `topicTags`
    - `originType`
    - `derivedFromResumeSection`
    - `derivedFromJobPostingSection`
    - `orderIndex`
    - `text`
    - `questionType`
    - `confidenceMarkers`
    - `isFollowUp`
    - `parentQuestionId`
    - `hasWeakAnswer`
    - `answerSummary`
    - `weaknessTags`
    - `strengthTags`
    - `questionStructuringSource`
    - `answerStructuringSource`
  - `followUpThreads[]`
    - additive thread/group summary for practical interview review UIs
    - each item includes:
      - `rootQuestionId`
      - `rootLinkedQuestionId`
      - `rootOrderIndex`
      - `rootText`
      - `questionIds[]`
      - `linkedQuestionIds[]`
      - `followUpQuestionIds[]`
      - `followUpCount`
      - `weakQuestionCount`
      - `answeredQuestionCount`
      - `quantifiedQuestionCount`
      - `structuredQuestionCount`
      - `tradeoffAwareQuestionCount`
      - `uncertainQuestionCount`
      - `recommendedAction`
      - `replayLaunchPreset`
      - `structuringSources[]`
- `PATCH /api/interview-records/{recordId}/review` applies bulk transcript review edits:
  - request body:
    - `edits[]`
    - each edit supports `segmentId`, `speakerType`, `cleanedText`, `confirmedText`
    - optional `confirmAfterApply`
  - response currently returns the same review provenance payload as `GET /review`
  - when `confirmAfterApply = true`, the backend rebuilds structure and immediately promotes the result to `confirmed`
- `GET /api/interview-records/{recordId}/interviewer-profile` currently returns a reusable interviewer-style summary including:
  - `styleTags`
  - `toneProfile`
  - `pressureLevel`
  - `depthPreference`
  - `followUpPatterns`
  - `favoriteTopics`
  - `openingPattern`
  - `closingPattern`
  - `structuringSource`

Current implementation notes:
- the current structuring pipeline is deterministic-first and text-based, with optional LLM refinement for summaries/tags/follow-up linkage/interviewer-profile when interview LLM settings are configured
- `structuringStage` values currently used by the backend:
  - `deterministic`
  - `ai_enriched`
  - `confirmed`
- `POST /api/interview-records/{recordId}/confirm` promotes the current practical-interview structuring result to `confirmed` without re-parsing transcript text
- current speaker detection uses prefixes and question-mark heuristics
- imported real-interview questions are not yet mirrored into archive

### Implemented Replay Mock Session Seeding

Purpose:
- start a standard interview session from an imported practical interview record instead of the global catalog or resume opener flow

Implemented additive contract:
- `POST /api/interview-sessions` now supports `sessionType = replay_mock`
- current additive create fields:
  - `sourceInterviewRecordId`
  - `replayMode`

Current semantics:
- `sourceInterviewRecordId` is required for `replay_mock`
- `replayMode` currently supports:
  - `original_replay`
  - `pattern_similar`
  - `pressure_variant`
- `resumeVersionId` remains optional for `replay_mock`
- the created session stores:
  - `sourceInterviewRecordId`
  - `replayMode`
- list and detail responses for interview sessions now also expose:
  - `sourceInterviewRecordId`
  - `replayMode`
- current replay seeding behavior:
  - loads ordered imported questions from the selected interview record
  - seeds those question texts directly into `interview_session_questions`
  - does not require a catalog `questionId`
  - carries replay-specific snapshot metadata in `bodyText`, `generationRationale`, and `generationStatus = replay_imported`
- current replay seed turns use:
  - `sourceType = replay_seed`
  - `generationStatus = replay_imported`

Current implemented follow-up behavior:
- `replay_mock` now reuses the standard turn-based session engine and can generate interviewer-style AI follow-up questions from the stored interviewer profile and imported question/answer examples
- replay AI follow-up turns use:
  - `sourceType = replay_ai_follow_up`
  - `generationStatus = replay_ai_generated`
- replay-seeded rows may start with `questionId = null`
- when the user submits an answer for a replay-seeded row, the backend binds that turn to a private generated question record before persisting the answer attempt so the existing answer/archive pipeline can still be reused
- imported real-interview questions are now exposed from archive as study assets with:
  - `sourceType = real_interview`
  - `sourceLabel = Real Interview`
  - `sourceInterviewRecordId`
  - `sourceInterviewQuestionId`
