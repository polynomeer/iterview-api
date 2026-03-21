# Feature: Practical Interview Replay Timestamps

## Goal
- turn imported practical interview audio into a replayable study asset
- connect one shared audio player to:
  - transcript segments
  - structured question cards
  - review timeline navigation
  - follow-up thread review
- avoid a separate replay-only storage model when the current transcript segment model already has stable millisecond offsets

## Current Product Fit
- this repository already stores:
  - uploaded interview audio file metadata on `interview_records`
  - ordered transcript segments on `interview_transcript_segments`
  - `start_ms` / `end_ms` on every transcript segment
  - question and answer segment linkage on `interview_record_questions` and `interview_record_answers`
- because of that, replay timestamps should be exposed as additive DTO metadata on existing practical interview APIs instead of introducing a second timeline domain

## Supported Backend Shape

### Transcript Read
- `GET /api/interview-records/{recordId}/transcript` now exposes:
  - `playback`
    - `playbackAvailable`
    - `sourceAudioFileUrl`
    - `sourceAudioFileName`
    - `audioDurationMs`
    - `sessionRange`
  - `segments[]`
    - `startMs`
    - `endMs`
    - `timestampLabel`

### Structured Questions Read
- `GET /api/interview-records/{recordId}/questions` now exposes:
  - root-level `playback`
  - per-question:
    - `questionRange`
    - `answerRange`
    - `questionAnswerRange`
  - per-answer:
    - `replayRange`

### Review Read
- `GET /api/interview-records/{recordId}/review` now exposes:
  - root-level `playback`
  - `questionSummaries[]`
    - `questionRange`
    - `answerRange`
    - `questionAnswerRange`
  - `followUpThreads[]`
    - `threadRange`
  - `timelineNavigation.items[]`
    - sequence anchors
    - millisecond replay ranges
  - `transcriptIssueSummary.segmentActions[]`
    - `seekRange`

## Range Contract

### Shared DTO
- `InterviewRecordReplayRangeDto`
  - `startMs`
  - `endMs`
  - `durationMs`
  - `startTimestampLabel`
  - `endTimestampLabel`

### Shared Playback DTO
- `InterviewRecordPlaybackDto`
  - `playbackAvailable`
  - `sourceAudioFileUrl`
  - `sourceAudioFileName`
  - `audioDurationMs`
  - `sessionRange`

### Why this shape
- frontend does not need to:
  - recalculate millisecond windows from segment ids
  - recalculate `mm:ss` labels
  - infer question-plus-answer clip boundaries
  - convert review timeline sequence anchors back into clip ranges

## Range Semantics
- `questionRange`
  - question segment start/end only
- `answerRange`
  - answer segment start/end only
- `questionAnswerRange`
  - merged question and answer window when both exist
- `threadRange`
  - merged replay window for all questions inside one follow-up chain
- `seekRange`
  - preferred playback window for one flagged transcript issue row
- `sessionRange`
  - first segment start to last segment end for one imported interview

## Duration Semantics
- `audioDurationMs` uses:
  1. persisted `sourceAudioDurationMs` if present
  2. otherwise `max(segment.endMs) + 1`
- `durationMs` uses inclusive segment boundaries:
  - `endMs - startMs + 1`

## Formatting Semantics
- timestamp labels are emitted by backend as:
  - `mm:ss` when under one hour
  - `h:mm:ss` when one hour or longer
- frontend may still render a custom visual timestamp, but should prefer backend labels for consistency across transcript and clip controls

## Session-Level UX Expectations
- one shared audio player per interview review/detail screen
- transcript row click or timestamp click should seek to the segment `startMs`
- question card replay buttons should use:
  - `questionRange` for `Question only`
  - `questionAnswerRange` for `Question + answer`
- thread replay preview can use `threadRange` for pre-listen before starting `replay_mock`

## Review-Level UX Expectations
- `timelineNavigation` should be the single source for transcript/question/thread synchronization
- `segmentActions[].seekRange` should drive transcript issue popovers or `jump and play` actions
- `questionSummaries[]` range fields should let the review table show:
  - question start time
  - answer start time
  - clip length
- `followUpThreads[].threadRange` should let thread cards surface:
  - `Play this chain`
  - `Open at first turn`

## Why no New Replay Endpoint
- current practical interview APIs already partition the data correctly:
  - transcript view
  - question view
  - review workspace
- the missing piece was timestamp metadata, not a missing aggregate root
- adding a replay-only endpoint would duplicate:
  - audio metadata
  - segment ordering
  - question-to-segment linkage
  - review navigation metadata

## Current Limitations
- no waveform data
- no speaker-level energy or pause markers
- no exact PDF-like visual waveform coordinates
- no true audio streaming/session sync protocol
- no separate `clip asset` table

These are intentionally deferred because the current product only needs reliable seek windows on existing transcript/question/review reads.

## Recommended Frontend Consumption
- transcript page:
  - use `transcript.playback`
  - use `segments[].timestampLabel`
- question cards:
  - use `questions.playback`
  - use question `questionRange` / `questionAnswerRange`
- review workspace:
  - use `review.playback`
  - use `timelineNavigation.items[]`
  - use `questionSummaries[]`
  - use `followUpThreads[]`
  - use `transcriptIssueSummary.segmentActions[]`

## Out of Scope
- waveform generation
- speaker diarization confidence visualization beyond current transcript segment metadata
- audio clipping/transcoding pipeline
- server-stored replay bookmarks
- session-synchronized collaborative playback

## Acceptance Criteria
- practical interview transcript reads expose one root playback object
- practical interview question reads expose question and answer replay windows
- practical interview review reads expose root playback, question ranges, thread ranges, issue seek ranges, and millisecond timeline navigation
- frontend can implement:
  - one shared player
  - transcript timestamp seek
  - question card clip playback
  - thread-level clip playback
  without reverse-engineering segment ids on the client
