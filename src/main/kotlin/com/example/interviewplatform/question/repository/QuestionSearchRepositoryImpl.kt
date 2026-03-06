package com.example.interviewplatform.question.repository

import com.example.interviewplatform.question.dto.QuestionSearchFilter
import com.example.interviewplatform.question.entity.QuestionEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class QuestionSearchRepositoryImpl : QuestionSearchRepository {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun search(filter: QuestionSearchFilter): List<QuestionEntity> {
        val clauses = mutableListOf<String>()
        val params = linkedMapOf<String, Any>()

        clauses += "q.isActive = true"

        filter.categoryId?.let {
            clauses += "q.categoryId = :categoryId"
            params["categoryId"] = it
        }
        filter.difficulty?.trim()?.takeIf { it.isNotEmpty() }?.let {
            clauses += "LOWER(q.difficultyLevel) = :difficulty"
            params["difficulty"] = it.lowercase()
        }
        filter.status?.trim()?.takeIf { it.isNotEmpty() }?.let {
            clauses += "LOWER(q.qualityStatus) = :status"
            params["status"] = it.lowercase()
        }
        filter.search?.trim()?.takeIf { it.isNotEmpty() }?.let {
            clauses += "(LOWER(q.title) LIKE :search OR LOWER(q.body) LIKE :search)"
            params["search"] = "%${it.lowercase()}%"
        }
        filter.companyId?.let {
            clauses += "EXISTS (SELECT qc.id.questionId FROM QuestionCompanyEntity qc WHERE qc.id.questionId = q.id AND qc.id.companyId = :companyId)"
            params["companyId"] = it
        }
        filter.roleId?.let {
            clauses += "EXISTS (SELECT qr.id.questionId FROM QuestionRoleEntity qr WHERE qr.id.questionId = q.id AND qr.id.jobRoleId = :roleId)"
            params["roleId"] = it
        }
        filter.tag?.trim()?.takeIf { it.isNotEmpty() }?.let {
            clauses += """
                EXISTS (
                    SELECT qt.id.questionId
                    FROM QuestionTagEntity qt, TagEntity t
                    WHERE qt.id.questionId = q.id
                      AND t.id = qt.id.tagId
                      AND LOWER(t.name) = :tag
                )
            """.trimIndent()
            params["tag"] = it.lowercase()
        }

        val whereClause = clauses.joinToString(" AND ")
        val query = entityManager.createQuery(
            "SELECT q FROM QuestionEntity q WHERE $whereClause ORDER BY q.createdAt DESC",
            QuestionEntity::class.java,
        )
        params.forEach(query::setParameter)
        return query.resultList
    }
}
