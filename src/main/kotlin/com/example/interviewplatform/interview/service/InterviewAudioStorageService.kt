package com.example.interviewplatform.interview.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.nio.file.Path

@Service
class InterviewAudioStorageService(
    @Value("\${app.storage.interview-audio-dir:uploads/interview-audio}")
    private val interviewAudioDir: String,
) {
    fun store(userId: Long, file: MultipartFile, now: Instant): StoredInterviewAudioFile {
        val originalName = sanitizeFileName(file.originalFilename)
        val directory = Paths.get(interviewAudioDir, "user-$userId").toAbsolutePath().normalize()
        Files.createDirectories(directory)

        val extension = originalName.substringAfterLast('.', "").lowercase().ifBlank { "bin" }
        val storageFileName = "interview-${now.toEpochMilli()}.$extension"
        val targetPath = directory.resolve(storageFileName).normalize()
        file.transferTo(targetPath)

        return StoredInterviewAudioFile(
            storageKey = Paths.get("user-$userId", storageFileName).toString().replace('\\', '/'),
            fileName = originalName,
            fileSizeBytes = Files.size(targetPath),
            checksumSha256 = sha256(targetPath),
            absolutePath = targetPath,
        )
    }

    private fun sanitizeFileName(originalFilename: String?): String {
        val cleaned = StringUtils.cleanPath(originalFilename ?: "interview-audio.bin")
        return cleaned.substringAfterLast('/').substringAfterLast('\\').ifBlank { "interview-audio.bin" }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class StoredInterviewAudioFile(
    val storageKey: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val absolutePath: Path,
)
