# 01-product-overview

## Product Summary
This backend supports an interview practice service modeled after algorithm problem-solving platforms.

The service helps users:
- practice one or more interview questions every day
- improve weak answers through repeated attempts
- archive mastered answers
- keep answer history over time
- personalize questions based on role, experience, target companies, and resume version

## MVP Backend Responsibilities
- persist user profile and settings
- manage target companies
- manage resume containers and resume versions
- serve question catalog and detail
- generate and serve daily cards
- accept answer attempts
- evaluate answers and store scores
- persist feedback items
- maintain user-question aggregate progress
- manage retry queue
- serve archive and feed data

## Core Product Rules
- questions are global
- answer attempts are append-only
- user progress is aggregate state
- retry scheduling is persisted
- archive status is explicit
- resume versions remain historically traceable

## Out of Scope
- social/community lounge
- mock interview sessions
- audio transcription pipeline
- public answer publishing
- GitHub sync
