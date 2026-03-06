package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.dto.ResumeDto
import com.example.interviewplatform.resume.repository.ResumeRepository
import org.springframework.stereotype.Service

@Service
class ResumeService(
    private val resumeRepository: ResumeRepository,
) {
    fun listUserResumes(userId: Long): List<ResumeDto> =
        resumeRepository.findByUserId(userId).map { ResumeDto(it.id, it.title, it.isPrimary) }
}
