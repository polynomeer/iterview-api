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
- `profile_image_url`
- `profile_image_file_name`
- `profile_image_content_type`
- `profile_image_uploaded_at`
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
- `file_name`
- `file_type`
- `raw_text`
- `parsed_json`
- `summary_text`
- `parsing_status`
- `is_active`
- `uploaded_at`
- `created_at`

Current interpretation:
- `resume_versions` is the immutable historical anchor
- uploaded PDF metadata should continue to live on this table rather than introducing a parallel version record
- `file_name`, `file_type`, and `parsing_status` support a parser-ready lifecycle without breaking existing version reads
- `raw_text` is the authoritative parser output from the uploaded PDF
- `parsed_json` is currently a bridge field for structured extraction output and should evolve into a trace or debug artifact rather than the only structured contract
- future resume intelligence should hang off `resume_version_id`

Recommended additive columns for LLM-backed structured extraction:
- `llm_extraction_status`
- `llm_extraction_started_at`
- `llm_extraction_completed_at`
- `llm_extraction_error_message`
- `llm_model`
- `llm_prompt_version`
- `llm_extraction_confidence`

These should be introduced as backward-compatible nullable columns when provider-backed structured extraction is implemented. Raw PDF parsing metadata and structured extraction metadata should remain distinguishable.

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

Current interpretation:
- this is the shared editorial content table for articles, guides, notes, and references
- it can hold related study material today, but it is not a question-specific model-answer structure

### `question_learning_materials`
- `question_id`
- `learning_material_id`
- `relevance_score`
- `created_at`

Current interpretation:
- this supports many-to-many question-to-material linkage today
- it is sufficient for generic related resources, but not for model-answer ordering, visibility, or answer-style metadata

## Interview Session Snapshot Extensions
Current implemented direction:
- `interview_session_questions` already acts as the immutable turn snapshot for opener and follow-up questions
- AI-generated questions may not map cleanly to a reusable catalog row, so the session snapshot remains the frontend source of truth during interview playback

Recommended additive columns for resume-grounded evidence display:
- `resume_evidence_json`

Recommended shape for `resume_evidence_json`:
```json
[
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
]
```

Interpretation:
- each session question may keep one or more resume evidence snippets
- evidence is stored on the session-question snapshot, not recomputed at read time
- `snippet` should be a short quote or excerpt, not the full resume paragraph
- `section` supports frontend badges such as `Project`, `Experience`, `Award`, `Certification`, or `Education`
- `sourceRecordType` and `sourceRecordId` let the frontend deep-link into parsed resume sections later without changing the question payload contract
- offsets are optional future-facing metadata for raw-text highlighting and should not be required in the first implementation

Recommended additive interview-planning tables for richer interview modes such as `full_coverage`:

### `interview_session_evidence_items`
- `id`
- `interview_session_id`
- `section`
- `label`
- `snippet`
- `source_record_type`
- `source_record_id`
- `coverage_status`
- `coverage_priority`
- `display_order`
- `created_at`
- `updated_at`

Recommended `coverage_status` values:
- `unasked`
- `asked`
- `answered`
- `defended`
- `weak`
- `skipped`

Interpretation:
- this table is the session-scoped inventory of interviewable resume evidence units
- it is created when a planner-driven interview mode such as `full_coverage` starts
- coverage is measured against these evidence rows rather than raw resume text bytes

### `interview_session_question_evidence_links`
- `interview_session_question_id`
- `interview_session_evidence_item_id`
- `link_role`
- `created_at`

Recommended `link_role` values:
- `primary`
- `secondary`
- `inherited`

Interpretation:
- one asked question may cover more than one resume evidence item
- one resume evidence item may later be linked to several questions
- this link table is the stable source for building a result-time resume map with hover and click interactions

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

Recommended additive columns for archive source metadata:
- `source_type`
- `source_session_id`
- `source_session_question_id`
- `source_label`
- `is_follow_up`

Purpose:
- keep archive question-level while preserving whether the mastered item came from a normal practice flow or an interview session turn

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
### `resume_profile_snapshots`
Purpose:
- store the top-level identity and positioning information extracted from one resume version

Columns:
- `id`
- `resume_version_id`
- `full_name`
- `headline`
- `summary_text`
- `location_text`
- `years_of_experience_text`
- `source_text`
- `created_at`
- `updated_at`

Notes:
- one resume version usually maps to one profile snapshot row
- this should store what the document actually says, not inferred user-profile overrides

### `resume_contact_points`
Purpose:
- store typed contact methods and external portfolio links extracted from one resume version

Columns:
- `id`
- `resume_version_id`
- `contact_type`
- `label`
- `value_text`
- `url`
- `display_order`
- `is_primary`
- `created_at`
- `updated_at`

Notes:
- `contact_type` can cover `phone`, `email`, `blog`, `github`, `linkedin`, `portfolio`, or future variants
- ordered display matters because resumes often present preferred contact channels first

### `resume_competency_items`
Purpose:
- store core strength or competency statements that are broader than a single skill keyword

Columns:
- `id`
- `resume_version_id`
- `title`
- `description`
- `source_text`
- `display_order`
- `created_at`
- `updated_at`

Notes:
- these rows help preserve “보유 역량” or “core strengths” sections from the original document

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
- `source_type`
- `llm_rationale`
- `is_confirmed`
- `created_at`
- `updated_at`

Notes:
- one resume version can produce many skill rows
- this is better than overloading `parsed_json` once the feature becomes queryable
- rows are regenerated per version so older PDFs keep their own extracted skill history

### `resume_experience_snapshots`
Purpose:
- store structured employment or major experience entries extracted from a resume version

Columns:
- `id`
- `resume_version_id`
- `company_name`
- `role_name`
- `employment_type`
- `started_on`
- `ended_on`
- `is_current`
- `project_name`
- `summary_text`
- `impact_text`
- `source_text`
- `risk_level`
- `source_type`
- `confidence_score`
- `llm_rationale`
- `display_order`
- `is_confirmed`
- `created_at`
- `updated_at`

Notes:
- keep `display_order` stable for frontend timeline rendering
- every experience claim remains attributable to one immutable uploaded version

### `resume_project_snapshots`
Purpose:
- store project- or initiative-level records nested under a resume experience when a company section contains multiple projects
- support resume-derived project cards that the frontend can browse independently from the employment timeline

Columns:
- `id`
- `resume_version_id`
- `resume_experience_snapshot_id`
- `title`
- `organization_name`
- `role_name`
- `summary_text`
- `content_text`
- `project_category_code`
- `project_category_name`
- `tech_stack_text`
- `started_on`
- `ended_on`
- `display_order`
- `source_text`
- `created_at`
- `updated_at`

Notes:
- this table captures long-form project sections separately from the higher-level employment timeline
- one employment experience may own multiple project rows
- `summary_text` should remain short-display friendly, while `content_text` can preserve fuller extracted project narrative
- category fields should remain nullable additive extensions so current rows stay compatible

### `resume_project_tags`
Recommended additive table:
- `id`
- `resume_project_snapshot_id`
- `tag_name`
- `tag_type`
- `display_order`
- `source_text`
- `created_at`
- `updated_at`

Purpose:
- store version-scoped tags extracted for a project, such as backend, payments, search, performance, infra, or AI

Notes:
- tags should remain scoped through `resume_project_snapshot_id`
- do not force reuse of the global question/catalog tag taxonomy unless cross-domain taxonomy coupling is explicitly desired

### `resume_achievement_items`
Purpose:
- store measurable outcomes, improvements, and operational results extracted from experiences or projects

Columns:
- `id`
- `resume_version_id`
- `resume_experience_snapshot_id`
- `resume_project_snapshot_id`
- `title`
- `metric_text`
- `impact_summary`
- `source_text`
- `severity_hint`
- `display_order`
- `created_at`
- `updated_at`

Notes:
- these rows are the best anchor for later follow-up defense questions
- high-confidence metric claims can feed `resume_risk_items`

### `resume_education_items`
Purpose:
- store academic history and formal training extracted from a resume version

Columns:
- `id`
- `resume_version_id`
- `institution_name`
- `degree_name`
- `field_of_study`
- `started_on`
- `ended_on`
- `description`
- `display_order`
- `source_text`
- `created_at`
- `updated_at`

### `resume_certification_items`
Purpose:
- store certifications, test scores, and other formal credentials extracted from a resume version

Columns:
- `id`
- `resume_version_id`
- `name`
- `issuer_name`
- `credential_code`
- `issued_on`
- `expires_on`
- `score_text`
- `display_order`
- `source_text`
- `created_at`
- `updated_at`

### `resume_award_items`
Purpose:
- store awards, contest results, and honors extracted from a resume version

Columns:
- `id`
- `resume_version_id`
- `title`
- `issuer_name`
- `awarded_on`
- `description`
- `display_order`
- `source_text`
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
- `source_text`
- `source_type`
- `confidence_score`
- `llm_rationale`
- `created_at`
- `updated_at`

Notes:
- optionally map risks back to a specific question later
- parse failure for a new version must not delete prior risk items for older versions

## Question Structure Extensions
### `question_reference_answers`
Purpose:
- store curated model answers or answer outlines for a question without mixing them into user answer attempts

Columns:
- `id`
- `question_id`
- `title`
- `answer_text`
- `answer_format`
- `source_type`
- `target_role_id`
- `company_id`
- `is_official`
- `display_order`
- `created_at`
- `updated_at`

Rules:
- model answers are global editorial assets shared across users
- keep them separate from `answer_attempts` so user history remains immutable and user-owned
- support multiple exemplars per question, such as `concise`, `detailed`, or `tradeoff-focused`

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

### Recommended additive columns for `learning_materials`
Purpose:
- improve curation and frontend rendering for related study content

Columns:
- `description`
- `difficulty_level`
- `estimated_minutes`
- `is_official`
- `display_order_hint`

Notes:
- these can be added directly to `learning_materials` if global metadata is enough
- if per-question ordering or labels diverge by question, prefer additive columns on `question_learning_materials` instead

### Recommended additive columns for `question_learning_materials`
Purpose:
- support question-specific presentation of related materials

Columns:
- `display_order`
- `relationship_type`
- `label_override`

Notes:
- `relationship_type` can distinguish `prerequisite`, `deep_dive`, `example`, or `reference_answer_support`

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

## Interview Session Extensions
### `interview_sessions`
Purpose:
- store one history record for an AI-driven mock interview session

Columns:
- `id`
- `user_id`
- `resume_version_id`
- `session_type`
- `status`
- `started_at`
- `completed_at`
- `question_count`
- `answered_count`
- `average_score`
- `summary_text`
- `created_at`
- `updated_at`

Notes:
- one row represents one full interview run
- this is the anchor for interview history screens
- for `resume_mock`, `resume_version_id` is the pinned interview grounding context selected at session start
- this row should remain stable even when many question-level archive items are later derived from the session

### `interview_session_questions`
Purpose:
- store question snapshots for main interview questions and follow-up questions generated within a session

Columns:
- `id`
- `interview_session_id`
- `question_id`
- `parent_session_question_id`
- `prompt_text`
- `body_text`
- `question_source_type`
- `is_follow_up`
- `display_order`
- `depth`
- `category_name`
- `tags_json`
- `focus_skill_names_json`
- `resume_context_summary`
- `generation_rationale`
- `generation_status`
- `llm_model`
- `llm_prompt_version`
- `created_at`
- `updated_at`

Notes:
- `question_id` stays nullable so AI-generated follow-up prompts can still be stored even when they do not map to the global catalog
- `parent_session_question_id` links a follow-up question back to its parent within the same session
- `prompt_text` must be stored as a snapshot because follow-up prompts may not exist in the global `questions` table
- `body_text` stores richer interviewer framing or constraints when the follow-up is generated outside the global catalog
- `focus_skill_names_json` and `resume_context_summary` preserve why a follow-up was asked
- `generation_status` should distinguish `seeded`, `catalog_follow_up`, `ai_generated`, and `fallback`
- opening questions should be able to use `question_source_type = ai_opening` when they were generated from resume context instead of selected from the catalog
- every row in this table is expected to be representable as one archive-visible question turn, even though the canonical interview history remains session-level

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
- unique(`resume_version_id`) on `resume_profile_snapshots` if one top-level profile row is enforced
- unique(`resume_version_id`, `skill_name`, `source_text`) on `resume_skill_snapshots` when deduplication is needed
- unique(`resume_version_id`, `risk_type`, `source_text`) on `resume_risk_items` when LLM extraction retries are introduced
- unique(`parent_question_id`, `child_question_id`, `relationship_type`) on `question_relationships`
- unique(`question_id`, `skill_name`, `skill_category_code`) on `question_skill_mappings`
- unique(`user_id`, `skill_category_code`) on the latest logical score row if the design keeps one active score row per category
- unique(`interview_session_id`, `display_order`) on `interview_session_questions`

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
- `resume_contact_points(resume_version_id, display_order)`
- `resume_competency_items(resume_version_id, display_order)`
- `resume_skill_snapshots(resume_version_id, skill_category_code)`
- `resume_experience_snapshots(resume_version_id, risk_level)`
- `resume_project_snapshots(resume_version_id, resume_experience_snapshot_id, display_order)`
- `resume_project_snapshots(resume_version_id, project_category_code, display_order)`
- `resume_project_tags(resume_project_snapshot_id, display_order)`
- `resume_achievement_items(resume_version_id, resume_project_snapshot_id, display_order)`
- `resume_education_items(resume_version_id, display_order)`
- `resume_certification_items(resume_version_id, display_order)`
- `resume_award_items(resume_version_id, display_order)`
- `resume_risk_items(resume_version_id, severity)`
- `resume_versions(resume_id, llm_extraction_status)`
- `question_relationships(parent_question_id, display_order)`
- `question_relationships(child_question_id)`
- `question_skill_mappings(question_id)`
- `question_skill_mappings(skill_category_code)`
- `answer_analyses(answer_attempt_id)`
- `skill_category_scores(user_id, skill_category_code)`
- `career_benchmarks(job_role_id, experience_band_code, skill_category_code)`
- `interview_sessions(user_id, started_at desc)`
- `interview_session_questions(interview_session_id, display_order)`
- `interview_session_questions(parent_session_question_id)`

## Migration Guidance
- every schema change must use Flyway
- do not repurpose current columns with incompatible meaning
- introduce new tables before adding API contracts that depend on them
- keep snake_case naming for all tables and columns
- prefer additive migrations that allow existing APIs to remain operational during rollout
