package com.example.interviewplatform.dailycard.repository

import com.example.interviewplatform.dailycard.entity.DailyCardEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyCardRepository : JpaRepository<DailyCardEntity, Long> {
    fun findByUserIdAndCardDate(userId: Long, cardDate: LocalDate): List<DailyCardEntity>
}
