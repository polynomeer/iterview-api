package com.example.interviewplatform.resume.service

import java.nio.file.Path

data class StoredResumeFile(
    val storageKey: String,
    val absolutePath: Path,
    val fileName: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
)
