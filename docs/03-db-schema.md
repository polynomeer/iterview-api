# 03-db-schema

## Overview
This document reflects two layers:
- the current physical schema already implemented by Flyway
- additive schema extensions required to support the updated product direction

The current schema remains the source of truth. New product concepts should be introduced with new tables or backward-compatible columns.

## Current Reference Tables
### `users`
- `id`
- `email`
- `password_hash`
- `provider`
- `provider_user_id`
- `status`
- `created_at`
- `updated_at`

### `user_profiles`
- `user_id`
- `nickname`
- `job_role_id`
- `years_of_experience`
- `avg_score`
- `archived_question_count`
- `answer_visibility_default`
- `created_at`
- `updated_at`

### `user_settings`
- `user_id`
- `target_score_threshold`
- `pass_score_threshold`
- `retry_enabled`
- `daily_question_count`
- `created_at`
- `updated_at`

### `companies`
- `id`
- `name`
- `normalized_name`
- `domain`
- `created_at`
- `updated_at`

### `job_roles`
- `id`
- `name`
- `parent_role_id`
- `created_at`

### `user_target_companies`
- `user_id`
- `company_id`
- `priority_order`
- `created_at`

### `categories`
- `id`
- `name`
- `parent_id`
- `created_at`

### `tags`
- `id`
- `name`
- `tag_type`
- `created_at`

## Current Resume Tables
### `resumes`
- `id`
- `user_id`
- `title`
- `is_primary`
- `created_at`
- `updated_at`

### `resume_versions`
- `id`
- `resume_id`
- `version_no`
- `file_url`
- `raw_text`
- `parsed_json`
- `summary_text`
- `is_active`
- `uploaded_at`
- `created_at`

Current interpretation:
- `resume_versions` is the immutable historical anchor
- `parsed_json` is the current bridge field for structured extraction output
- future resume intelligence should hang off `resume_version_id`

## Current Question Tables
### `questions`
- `id`
- `author_user_id`
- `category_id`
- `title`
- `body`
- `question_type`
- `difficulty_level`
- `source_type`
- `quality_status`
- `visibility`
- `expected_answer_seconds`
- `is_active`
- `created_at`
- `updated_at`

### `question_tags`
- `question_id`
- `tag_id`
- `created_at`

### `question_companies`
- `question_id`
- `company_id`
- `relevance_score`
- `is_past_frequent`
- `is_trending_recent`
- `created_at`

### `question_roles`
- `question_id`
- `job_role_id`
- `relevance_score`
- `created_at`

### `learning_materials`
- `id`
- `title`
- `material_type`
- `content_text`
- `content_url`
- `source_name`
- `created_at`
- `updated_at`

### `question_learning_materials`
- `question_id`
- `learning_material_id`
- `relevance_score`
- `created_at`

## Current Answer and Progress Tables
### `daily_cards`
- `id`
- `user_id`
- `question_id`
- `card_date`
- `card_type`
- `source_reason`
- `status`
- `delivered_at`
- `opened_at`
- `created_at`

### `answer_attempts`
- `id`
- `user_id`
- `question_id`
- `resume_version_id`
- `source_daily_card_id`
- `attempt_no`
- `answer_mode`
- `content_text`
- `submitted_at`
- `created_at`

### `answer_scores`
- `answer_attempt_id`
- `total_score`
- `structure_score`
- `specificity_score`
- `technical_accuracy_score`
- `role_fit_score`
- `company_fit_score`
- `communication_score`
- `evaluation_result`
- `evaluated_at`

### `answer_feedback_items`
- `id`
- `answer_attempt_id`
- `feedback_type`
- `severity`
- `title`
- `body`
- `display_order`
- `created_at`

### `user_question_progress`
- `id`
- `user_id`
- `question_id`
- `latest_answer_attempt_id`
- `best_answer_attempt_id`
- `latest_score`
- `best_score`
- `total_attempt_count`
- `unanswered_count`
- `current_status`
- `archived_at`
- `last_answered_at`
- `next_review_at`
- `mastery_level`
- `created_at`
- `updated_at`

### `review_queue`
- `id`
- `user_id`
- `question_id`
- `trigger_answer_attempt_id`
- `reason_type`
- `priority`
- `scheduled_for`
- `status`
- `created_at`
- `updated_at`

## Additive Schema for Updated Product Direction
These are recommended next-step tables. They extend the current model without replacing existing data.

## Resume Intelligence Extensions
### `resume_skill_snapshots`
Purpose:
- store extracted or user-confirmed skills for a specific resume version

Columns:
- `id`
- `resume_version_id`
- `skill_name`
- `skill_category_code`
- `source_text`
- `confidence_score`
- `is_confirmed`
- `created_at`
- `updated_at`

Notes:
- one resume version can produce many skill rows
- this is better than overloading `parsed_json` once the feature becomes queryable

### `resume_experience_snapshots`
Purpose:
- store structured project and impact claims extracted from a resume version

Columns:
- `id`
- `resume_version_id`
- `project_name`
- `summary_text`
- `impact_text`
- `source_text`
- `risk_level`
- `display_order`
- `is_confirmed`
- `created_at`
- `updated_at`

### `resume_risk_items`
Purpose:
- track resume statements that likely need follow-up defense

Columns:
- `id`
- `resume_version_id`
- `resume_experience_snapshot_id`
- `risk_type`
- `title`
- `description`
- `severity`
- `created_at`
- `updated_at`

## Question Structure Extensions
### `question_relationships`
Purpose:
- represent follow-up trees or lightweight graphs while keeping `questions` as the canonical content table

Columns:
- `id`
- `parent_question_id`
- `child_question_id`
- `relationship_type`
- `depth`
- `display_order`
- `created_at`

Rules:
- adjacency-list style is sufficient for MVP
- `relationship_type` can distinguish `follow_up`, `resume_probe`, `related`, or future variants

### `question_skill_mappings`
Purpose:
- connect questions to skill categories or named skills for radar and gap analysis

Columns:
- `id`
- `question_id`
- `skill_name`
- `skill_category_code`
- `weight`
- `created_at`

## Answer Analysis Extensions
### `answer_analyses`
Purpose:
- persist richer analysis dimensions that are too detailed for `answer_scores`

Columns:
- `id`
- `answer_attempt_id`
- `overall_score`
- `depth_score`
- `clarity_score`
- `accuracy_score`
- `example_score`
- `tradeoff_score`
- `confidence_score`
- `strength_summary`
- `weakness_summary`
- `recommended_next_step`
- `created_at`

Notes:
- `answer_scores` remains the current source for existing APIs
- `answer_analyses` should be introduced only when the product needs stable radar and follow-up analytics

## Skill and Benchmark Extensions
### `skill_category_scores`
Purpose:
- store the latest per-user per-category readiness score

Columns:
- `id`
- `user_id`
- `skill_category_code`
- `score`
- `answered_question_count`
- `weak_question_count`
- `benchmark_score`
- `gap_score`
- `calculated_at`
- `created_at`
- `updated_at`

### `career_benchmarks`
Purpose:
- store role and experience-based comparison baselines

Columns:
- `id`
- `job_role_id`
- `experience_band_code`
- `skill_category_code`
- `benchmark_score`
- `created_at`
- `updated_at`

## Constraints
### Existing Constraints
- unique(`user_id`, `question_id`) on `user_question_progress`
- unique(`user_id`, `question_id`, `attempt_no`) on `answer_attempts`
- unique(`resume_id`, `version_no`) on `resume_versions`
- primary key(`question_id`, `tag_id`) on `question_tags`
- primary key(`question_id`, `company_id`) on `question_companies`
- primary key(`question_id`, `job_role_id`) on `question_roles`
- primary key(`question_id`, `learning_material_id`) on `question_learning_materials`

### Recommended New Constraints
- unique(`resume_version_id`, `skill_name`, `source_text`) on `resume_skill_snapshots` when deduplication is needed
- unique(`parent_question_id`, `child_question_id`, `relationship_type`) on `question_relationships`
- unique(`question_id`, `skill_name`, `skill_category_code`) on `question_skill_mappings`
- unique(`user_id`, `skill_category_code`) on the latest logical score row if the design keeps one active score row per category

## Required Indexes
### Existing Read Paths
- `questions(category_id)`
- `questions(question_type)`
- `user_question_progress(user_id, current_status)`
- `user_question_progress(user_id, next_review_at)`
- `answer_attempts(user_id, question_id, submitted_at desc)`
- `review_queue(user_id, status, scheduled_for)`
- `daily_cards(user_id, card_date)`

### Recommended New Read Paths
- `resume_skill_snapshots(resume_version_id, skill_category_code)`
- `resume_experience_snapshots(resume_version_id, risk_level)`
- `resume_risk_items(resume_version_id, severity)`
- `question_relationships(parent_question_id, display_order)`
- `question_relationships(child_question_id)`
- `question_skill_mappings(question_id)`
- `question_skill_mappings(skill_category_code)`
- `answer_analyses(answer_attempt_id)`
- `skill_category_scores(user_id, skill_category_code)`
- `career_benchmarks(job_role_id, experience_band_code, skill_category_code)`

## Migration Guidance
- every schema change must use Flyway
- do not repurpose current columns with incompatible meaning
- introduce new tables before adding API contracts that depend on them
- keep snake_case naming for all tables and columns
- prefer additive migrations that allow existing APIs to remain operational during rollout
