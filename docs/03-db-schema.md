# 03-db-schema

## Reference Tables
### users
- id
- email
- password_hash
- provider
- provider_user_id
- status
- created_at
- updated_at

### user_profiles
- user_id
- nickname
- job_role_id
- years_of_experience
- avg_score
- archived_question_count
- answer_visibility_default
- created_at
- updated_at

### user_settings
- user_id
- target_score_threshold
- pass_score_threshold
- retry_enabled
- daily_question_count
- created_at
- updated_at

### companies
- id
- name
- normalized_name
- domain
- created_at
- updated_at

### job_roles
- id
- name
- parent_role_id
- created_at

### user_target_companies
- user_id
- company_id
- priority_order
- created_at

### resumes
- id
- user_id
- title
- is_primary
- created_at
- updated_at

### resume_versions
- id
- resume_id
- version_no
- file_url
- raw_text
- parsed_json
- summary_text
- is_active
- uploaded_at
- created_at

### categories
- id
- name
- parent_id
- created_at

### tags
- id
- name
- tag_type
- created_at

## Question Tables
### questions
- id
- author_user_id
- category_id
- title
- body
- question_type
- difficulty_level
- source_type
- quality_status
- visibility
- expected_answer_seconds
- is_active
- created_at
- updated_at

### question_tags
- question_id
- tag_id
- created_at

### question_companies
- question_id
- company_id
- relevance_score
- is_past_frequent
- is_trending_recent
- created_at

### question_roles
- question_id
- job_role_id
- relevance_score
- created_at

### learning_materials
- id
- title
- material_type
- content_text
- content_url
- source_name
- created_at
- updated_at

### question_learning_materials
- question_id
- learning_material_id
- relevance_score
- created_at

## Answer and Progress Tables
### daily_cards
- id
- user_id
- question_id
- card_date
- card_type
- source_reason
- status
- delivered_at
- opened_at
- created_at

### answer_attempts
- id
- user_id
- question_id
- resume_version_id
- source_daily_card_id
- attempt_no
- answer_mode
- content_text
- submitted_at
- created_at

### answer_scores
- answer_attempt_id
- total_score
- structure_score
- specificity_score
- technical_accuracy_score
- role_fit_score
- company_fit_score
- communication_score
- evaluation_result
- evaluated_at

### answer_feedback_items
- id
- answer_attempt_id
- feedback_type
- severity
- title
- body
- display_order
- created_at

### user_question_progress
- id
- user_id
- question_id
- latest_answer_attempt_id
- best_answer_attempt_id
- latest_score
- best_score
- total_attempt_count
- unanswered_count
- current_status
- archived_at
- last_answered_at
- next_review_at
- mastery_level
- created_at
- updated_at

### review_queue
- id
- user_id
- question_id
- trigger_answer_attempt_id
- reason_type
- priority
- scheduled_for
- status
- created_at
- updated_at

## Constraints
- unique(user_id, question_id) on user_question_progress
- unique(user_id, question_id, attempt_no) on answer_attempts
- primary key(question_id, tag_id) on question_tags
- primary key(question_id, company_id) on question_companies
- primary key(question_id, job_role_id) on question_roles
- primary key(question_id, learning_material_id) on question_learning_materials

## Required Indexes
- questions(category_id)
- questions(question_type)
- user_question_progress(user_id, current_status)
- user_question_progress(user_id, next_review_at)
- answer_attempts(user_id, question_id, submitted_at desc)
- review_queue(user_id, status, scheduled_for)
- daily_cards(user_id, card_date)
