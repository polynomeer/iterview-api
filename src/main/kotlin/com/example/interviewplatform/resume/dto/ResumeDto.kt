package com.example.interviewplatform.resume.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Resume container with immutable resume versions")
data class ResumeDto(
    @field:Schema(description = "Resume container id")
    val id: Long,
    @field:Schema(description = "User-defined resume title", example = "Platform Resume")
    val title: String,
    @field:Schema(description = "Whether this resume is marked as the primary resume")
    val isPrimary: Boolean,
    @field:Schema(description = "Immutable resume versions associated with this resume")
    val versions: List<ResumeVersionDto>,
)
