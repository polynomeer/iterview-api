package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity

interface ResumeStructuredExtractionClient {
    fun isEnabled(): Boolean

    fun extract(version: ResumeVersionEntity): ExtractedResumeSignals
}

