package com.example.interviewplatform.resume.dto

import java.time.LocalDate

data class ResumeCertificationItemDto(
    val id: Long,
    val name: String,
    val issuerName: String?,
    val credentialCode: String?,
    val issuedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val scoreText: String?,
    val displayOrder: Int,
    val sourceText: String?,
)
