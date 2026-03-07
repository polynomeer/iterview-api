package com.example.interviewplatform.dailycard.repository

import com.example.interviewplatform.dailycard.entity.DailyCardEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate

interface DailyCardRepository : JpaRepository<DailyCardEntity, Long> {
    fun findByUserIdAndCardDateOrderByCreatedAtAsc(userId: Long, cardDate: LocalDate): List<DailyCardEntity>

    fun findByIdAndUserId(id: Long, userId: Long): DailyCardEntity?

    @Modifying
    @Query(
        """
        update DailyCardEntity d
        set d.status = :status,
            d.openedAt = :openedAt
        where d.id = :id and d.userId = :userId
        """,
    )
    fun markOpened(id: Long, userId: Long, status: String, openedAt: Instant): Int
}
