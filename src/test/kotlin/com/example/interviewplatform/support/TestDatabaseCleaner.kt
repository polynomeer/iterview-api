package com.example.interviewplatform.support

import org.springframework.jdbc.core.JdbcTemplate

object TestDatabaseCleaner {
    fun reset(jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                answer_feedback_items,
                answer_scores,
                review_queue,
                user_question_progress,
                user_question_learning_materials,
                user_question_reference_answers,
                answer_attempts,
                daily_cards,
                question_learning_materials,
                question_roles,
                question_companies,
                question_tags,
                learning_materials,
                resume_versions,
                resumes,
                user_target_companies,
                user_settings,
                user_profiles,
                questions,
                users
            RESTART IDENTITY CASCADE
            """.trimIndent(),
        )
    }
}
