package com.example.interviewplatform.review.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArchiveDecisionServiceTest {
    private val archiveDecisionService = ArchiveDecisionService()

    @Test
    fun `archives when score and attempts pass thresholds`() {
        assertTrue(archiveDecisionService.shouldArchive(score = 85, attemptCount = 3))
    }

    @Test
    fun `does not archive when attempts are low`() {
        assertFalse(archiveDecisionService.shouldArchive(score = 90, attemptCount = 1))
    }
}
