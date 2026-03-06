package com.example.interviewplatform.review.service

import org.springframework.stereotype.Service

@Service
class ArchiveDecisionService {
    fun shouldArchive(score: Int, attemptCount: Int): Boolean = score >= 80 && attemptCount >= 2
}
