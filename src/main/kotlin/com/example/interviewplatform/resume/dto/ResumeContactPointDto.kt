package com.example.interviewplatform.resume.dto

data class ResumeContactPointDto(
    val id: Long,
    val contactType: String,
    val label: String?,
    val valueText: String?,
    val url: String?,
    val displayOrder: Int,
    val primary: Boolean,
)
