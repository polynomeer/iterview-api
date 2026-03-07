package com.example.interviewplatform.dailycard.service

import com.example.interviewplatform.common.service.ClockService
import com.example.interviewplatform.dailycard.dto.OpenDailyCardResponseDto
import com.example.interviewplatform.dailycard.repository.DailyCardRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class DailyCardService(
    private val dailyCardRepository: DailyCardRepository,
    private val clockService: ClockService,
) {
    @Transactional
    fun openCard(userId: Long, dailyCardId: Long): OpenDailyCardResponseDto {
        val card = dailyCardRepository.findByIdAndUserId(dailyCardId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Daily card not found: $dailyCardId")

        if (card.openedAt != null && card.status.equals(STATUS_OPENED, ignoreCase = true)) {
            return OpenDailyCardResponseDto(
                id = card.id,
                status = card.status,
                openedAt = card.openedAt,
            )
        }

        val now = clockService.now()
        dailyCardRepository.markOpened(
            id = dailyCardId,
            userId = userId,
            status = STATUS_OPENED,
            openedAt = now,
        )

        return OpenDailyCardResponseDto(
            id = dailyCardId,
            status = STATUS_OPENED,
            openedAt = now,
        )
    }

    private companion object {
        const val STATUS_OPENED = "opened"
    }
}
