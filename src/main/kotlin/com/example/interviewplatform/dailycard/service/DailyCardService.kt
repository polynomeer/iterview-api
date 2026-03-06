package com.example.interviewplatform.dailycard.service

import com.example.interviewplatform.dailycard.dto.DailyCardDto
import com.example.interviewplatform.dailycard.repository.DailyCardRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DailyCardService(
    private val dailyCardRepository: DailyCardRepository,
) {
    fun getTodayCards(userId: Long): List<DailyCardDto> =
        dailyCardRepository.findByUserIdAndCardDate(userId, LocalDate.now()).map {
            DailyCardDto(it.id, it.questionId, it.cardDate, it.status)
        }
}
