package com.example.interviewplatform.question.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.support.TestDatabaseCleaner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class QuestionApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var authHeader: String

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'question-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "question-user@example.com")}"
    }

    @Test
    fun `list questions supports filters and excludes inactive by default`() {
        val categoryId = idByName("categories", "name", "System Design")
        val tagId = idByName("tags", "name", "scalability")
        val companyId = idByName("companies", "normalized_name", "amazon")
        val roleId = idByName("job_roles", "name", "Backend Engineer")

        val matchingQuestionId = insertQuestion(
            categoryId = categoryId,
            title = "Design a high throughput queue",
            body = "How would you design queue durability and throughput?",
            difficulty = "HARD",
            qualityStatus = "approved",
            isActive = true,
        )
        insertQuestion(
            categoryId = categoryId,
            title = "Behavioral question",
            body = "Tell me about a conflict",
            difficulty = "EASY",
            qualityStatus = "approved",
            isActive = true,
        )
        insertQuestion(
            categoryId = categoryId,
            title = "Inactive queue design",
            body = "This question should stay hidden",
            difficulty = "HARD",
            qualityStatus = "approved",
            isActive = false,
        )

        jdbcTemplate.update(
            "INSERT INTO question_tags (question_id, tag_id, created_at) VALUES (?, ?, now())",
            matchingQuestionId,
            tagId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.90, true, true, now())
            """.trimIndent(),
            matchingQuestionId,
            companyId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_roles (question_id, job_role_id, relevance_score, created_at) VALUES (?, ?, 0.88, now())",
            matchingQuestionId,
            roleId,
        )

        val materialId = jdbcTemplate.queryForObject(
            """
            INSERT INTO learning_materials (title, material_type, content_text, content_url, source_name, created_at, updated_at)
            VALUES ('Queue Design Guide', 'article', NULL, 'https://example.com/queue-guide', 'Eng Blog', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )!!
        jdbcTemplate.update(
            "INSERT INTO question_learning_materials (question_id, learning_material_id, relevance_score, created_at) VALUES (?, ?, 0.95, now())",
            matchingQuestionId,
            materialId,
        )

        mockMvc.perform(
            get("/api/questions")
                .header("Authorization", authHeader)
                .param("categoryId", categoryId.toString())
                .param("tag", "scalability")
                .param("companyId", companyId.toString())
                .param("roleId", roleId.toString())
                .param("difficulty", "hard")
                .param("status", "approved")
                .param("search", "throughput"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(matchingQuestionId))
            .andExpect(jsonPath("$[0].tags[0].name").value("scalability"))
            .andExpect(jsonPath("$[0].companies[0].id").value(companyId))
            .andExpect(jsonPath("$[0].learningMaterials[0].title").value("Queue Design Guide"))
            .andExpect(jsonPath("$[1]").doesNotExist())
    }

    @Test
    fun `detail returns related metadata and user progress summary`() {
        val categoryId = idByName("categories", "name", "System Design")
        val tagId = idByName("tags", "name", "scalability")
        val companyId = idByName("companies", "normalized_name", "amazon")
        val roleId = idByName("job_roles", "name", "Backend Engineer")

        val questionId = insertQuestion(
            categoryId = categoryId,
            title = "Design a distributed cache",
            body = "Explain invalidation and consistency",
            difficulty = "MEDIUM",
            qualityStatus = "approved",
            isActive = true,
        )

        jdbcTemplate.update("INSERT INTO question_tags (question_id, tag_id, created_at) VALUES (?, ?, now())", questionId, tagId)
        jdbcTemplate.update(
            """
            INSERT INTO question_companies (question_id, company_id, relevance_score, is_past_frequent, is_trending_recent, created_at)
            VALUES (?, ?, 0.75, false, true, now())
            """.trimIndent(),
            questionId,
            companyId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_roles (question_id, job_role_id, relevance_score, created_at) VALUES (?, ?, 0.67, now())",
            questionId,
            roleId,
        )

        val materialId = jdbcTemplate.queryForObject(
            """
            INSERT INTO learning_materials (title, material_type, content_text, content_url, source_name, created_at, updated_at)
            VALUES ('Caching Patterns', 'video', NULL, 'https://example.com/cache-video', 'Tech Channel', now(), now())
            RETURNING id
            """.trimIndent(),
            Long::class.java,
        )!!
        jdbcTemplate.update(
            "INSERT INTO question_learning_materials (question_id, learning_material_id, relevance_score, created_at) VALUES (?, ?, 0.89, now())",
            questionId,
            materialId,
        )

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 72.5, 81.0,
                3, 0, 'in_progress', NULL, now(), now() + interval '2 days',
                'intermediate', now(), now()
            )
            """.trimIndent(),
            questionId,
        )

        mockMvc.perform(get("/api/questions/$questionId").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.question.id").value(questionId))
            .andExpect(jsonPath("$.tags[0].name").value("scalability"))
            .andExpect(jsonPath("$.companies[0].id").value(companyId))
            .andExpect(jsonPath("$.roles[0].id").value(roleId))
            .andExpect(jsonPath("$.learningMaterials[0].title").value("Caching Patterns"))
            .andExpect(jsonPath("$.userProgressSummary.currentStatus").value("in_progress"))
            .andExpect(jsonPath("$.userProgressSummary.totalAttemptCount").value(3))
    }

    @Test
    fun `detail excludes inactive questions`() {
        val categoryId = idByName("categories", "name", "System Design")
        val questionId = insertQuestion(
            categoryId = categoryId,
            title = "Inactive detail",
            body = "Do not show",
            difficulty = "MEDIUM",
            qualityStatus = "approved",
            isActive = false,
        )

        mockMvc.perform(get("/api/questions/$questionId").header("Authorization", authHeader))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `detail is public and omits user progress without auth`() {
        val categoryId = idByName("categories", "name", "System Design")
        val questionId = insertQuestion(
            categoryId = categoryId,
            title = "Public detail",
            body = "Visible to anonymous users",
            difficulty = "MEDIUM",
            qualityStatus = "approved",
            isActive = true,
        )

        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, 75, 80,
                2, 0, 'in_progress', NULL, now(), now() + interval '2 days',
                'intermediate', now(), now()
            )
            """.trimIndent(),
            questionId,
        )

        mockMvc.perform(get("/api/questions/$questionId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.question.id").value(questionId))
            .andExpect(jsonPath("$.userProgressSummary").doesNotExist())
    }

    @Test
    fun `tree endpoint returns follow up hierarchy with derived node statuses`() {
        val categoryId = idByName("categories", "name", "System Design")
        val rootQuestionId = insertQuestion(categoryId, "Root question", "Root body", "HARD", "approved", true)
        val answeredChildId = insertQuestion(categoryId, "Answered child", "Body", "MEDIUM", "approved", true)
        val weakChildId = insertQuestion(categoryId, "Weak child", "Body", "MEDIUM", "approved", true)
        val grandchildId = insertQuestion(categoryId, "Grandchild", "Body", "EASY", "approved", true)

        jdbcTemplate.update(
            "INSERT INTO question_relationships (parent_question_id, child_question_id, relationship_type, depth, display_order, created_at) VALUES (?, ?, 'follow_up', 1, 1, now())",
            rootQuestionId,
            answeredChildId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_relationships (parent_question_id, child_question_id, relationship_type, depth, display_order, created_at) VALUES (?, ?, 'follow_up', 1, 2, now())",
            rootQuestionId,
            weakChildId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_relationships (parent_question_id, child_question_id, relationship_type, depth, display_order, created_at) VALUES (?, ?, 'follow_up', 2, 1, now())",
            weakChildId,
            grandchildId,
        )

        insertProgress(answeredChildId, 82.0, "in_progress")
        insertProgress(weakChildId, 49.0, "retry_pending")

        mockMvc.perform(get("/api/questions/$rootQuestionId/tree").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.root.questionId").value(rootQuestionId))
            .andExpect(jsonPath("$.root.nodeStatus").value("unanswered"))
            .andExpect(jsonPath("$.root.children[0].questionId").value(answeredChildId))
            .andExpect(jsonPath("$.root.children[0].nodeStatus").value("answered"))
            .andExpect(jsonPath("$.root.children[1].questionId").value(weakChildId))
            .andExpect(jsonPath("$.root.children[1].nodeStatus").value("weak"))
            .andExpect(jsonPath("$.root.children[1].children[0].questionId").value(grandchildId))
            .andExpect(jsonPath("$.root.children[1].children[0].nodeStatus").value("unanswered"))
    }

    @Test
    fun `recommended followups and resume based questions use new intelligence structures`() {
        seedSkillCategory("BACKEND", "Backend", 1)
        seedSkill("Redis", "BACKEND")
        seedSkill("Kafka", "BACKEND")

        val categoryId = idByName("categories", "name", "System Design")
        val rootQuestionId = insertQuestion(categoryId, "Root followup question", "Body", "HARD", "approved", true)
        val followupId = insertQuestion(categoryId, "Resume followup", "Body", "MEDIUM", "approved", true)
        val resumeQuestionId = insertQuestion(categoryId, "Resume based root", "Body", "MEDIUM", "approved", true)

        jdbcTemplate.update(
            "INSERT INTO question_relationships (parent_question_id, child_question_id, relationship_type, depth, display_order, created_at) VALUES (?, ?, 'follow_up', 1, 1, now())",
            rootQuestionId,
            followupId,
        )
        insertProgress(followupId, 92.0, "archived")

        val kafkaSkillId = idByName("skills", "name", "Kafka")
        val redisSkillId = idByName("skills", "name", "Redis")
        jdbcTemplate.update(
            "INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at) VALUES (?, ?, 0.90, now())",
            followupId,
            kafkaSkillId,
        )
        jdbcTemplate.update(
            "INSERT INTO question_skill_mappings (question_id, skill_id, weight, created_at) VALUES (?, ?, 0.95, now())",
            resumeQuestionId,
            redisSkillId,
        )

        val resumeId = jdbcTemplate.queryForObject(
            "INSERT INTO resumes (user_id, title, is_primary, created_at, updated_at) VALUES (1, 'Current Resume', true, now(), now()) RETURNING id",
            Long::class.java,
        )!!
        val versionId = jdbcTemplate.queryForObject(
            """
            INSERT INTO resume_versions (
                resume_id, version_no, file_url, file_name, file_type, raw_text, parsed_json, summary_text, parsing_status, is_active, uploaded_at, created_at
            ) VALUES (
                ?, 1, 'https://files.example.com/resume.pdf', 'resume.pdf', 'pdf',
                'Worked with Redis and Kafka', '{"skills":["Redis","Kafka"]}', 'Summary', 'completed', true, now(), now()
            ) RETURNING id
            """.trimIndent(),
            Long::class.java,
            resumeId,
        )
        jdbcTemplate.update(
            "INSERT INTO resume_skill_snapshots (resume_version_id, skill_id, skill_name, source_text, confidence_score, is_confirmed, created_at, updated_at) VALUES (?, ?, 'Redis', 'Redis experience', 0.9, true, now(), now())",
            versionId,
            redisSkillId,
        )
        jdbcTemplate.update(
            "INSERT INTO resume_skill_snapshots (resume_version_id, skill_id, skill_name, source_text, confidence_score, is_confirmed, created_at, updated_at) VALUES (?, ?, 'Kafka', 'Kafka experience', 0.9, true, now(), now())",
            versionId,
            kafkaSkillId,
        )

        mockMvc.perform(get("/api/questions/$rootQuestionId/recommended-followups").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].questionId").value(followupId))
            .andExpect(jsonPath("$[0].nodeStatus").value("strong"))

        mockMvc.perform(get("/api/questions/resume-based").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].questionId").value(resumeQuestionId))
            .andExpect(jsonPath("$[0].matchedSkills[0]").isNotEmpty)
    }

    private fun idByName(table: String, column: String, value: String): Long = jdbcTemplate.queryForObject(
        "SELECT id FROM $table WHERE $column = ?",
        Long::class.java,
        value,
    )

    private fun insertQuestion(
        categoryId: Long,
        title: String,
        body: String,
        difficulty: String,
        qualityStatus: String,
        isActive: Boolean,
    ): Long = jdbcTemplate.queryForObject(
        """
        INSERT INTO questions (
            author_user_id, category_id, title, body, question_type, difficulty_level,
            source_type, quality_status, visibility, expected_answer_seconds, is_active, created_at, updated_at
        ) VALUES (
            NULL, ?, ?, ?, 'technical', ?,
            'catalog', ?, 'public', 300, ?, now(), now()
        ) RETURNING id
        """.trimIndent(),
        Long::class.java,
        categoryId,
        title,
        body,
        difficulty,
        qualityStatus,
        isActive,
    )

    private fun insertProgress(questionId: Long, latestScore: Double, currentStatus: String) {
        jdbcTemplate.update(
            """
            INSERT INTO user_question_progress (
                user_id, question_id, latest_answer_attempt_id, best_answer_attempt_id, latest_score, best_score,
                total_attempt_count, unanswered_count, current_status, archived_at, last_answered_at, next_review_at,
                mastery_level, created_at, updated_at
            ) VALUES (
                1, ?, NULL, NULL, ?, ?, 1, 0, ?, NULL, now(), now() + interval '1 day', 'intermediate', now(), now()
            )
            """.trimIndent(),
            questionId,
            latestScore,
            latestScore,
            currentStatus,
        )
    }

    private fun seedSkillCategory(code: String, name: String, displayOrder: Int) {
        jdbcTemplate.update(
            """
            INSERT INTO skill_categories (code, name, display_order, created_at, updated_at)
            VALUES (?, ?, ?, now(), now())
            ON CONFLICT (code) DO UPDATE
            SET name = EXCLUDED.name,
                display_order = EXCLUDED.display_order,
                updated_at = now()
            """.trimIndent(),
            code,
            name,
            displayOrder,
        )
    }

    private fun seedSkill(name: String, categoryCode: String) {
        jdbcTemplate.update(
            """
            INSERT INTO skills (skill_category_id, name, description, created_at, updated_at)
            SELECT sc.id, ?, ?, now(), now()
            FROM skill_categories sc
            WHERE sc.code = ?
            ON CONFLICT (name) DO UPDATE
            SET skill_category_id = EXCLUDED.skill_category_id,
                description = EXCLUDED.description,
                updated_at = now()
            """.trimIndent(),
            name,
            "$name description",
            categoryCode,
        )
    }
}
