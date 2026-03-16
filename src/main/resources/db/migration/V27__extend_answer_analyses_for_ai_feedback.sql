ALTER TABLE answer_analyses
ADD COLUMN detailed_feedback TEXT,
ADD COLUMN model_answer_text TEXT,
ADD COLUMN strength_points_json TEXT,
ADD COLUMN improvement_points_json TEXT,
ADD COLUMN missed_points_json TEXT,
ADD COLUMN llm_model VARCHAR(120),
ADD COLUMN content_locale VARCHAR(10);
