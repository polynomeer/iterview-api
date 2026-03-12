package com.example.interviewplatform.user.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class ProfileImageStorageService(
    @Value("\${app.storage.profile-image-dir:uploads/profile-images}")
    private val profileImageDir: String,
) {
    fun store(userId: Long, file: MultipartFile): StoredProfileImage {
        validate(file)
        val uploadDir = Paths.get(profileImageDir).toAbsolutePath().normalize()
        Files.createDirectories(uploadDir)

        val extension = extensionOf(file.originalFilename)
        val fileName = "user-$userId-${UUID.randomUUID()}$extension"
        val targetPath = uploadDir.resolve(fileName)
        file.inputStream.use { input ->
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
        return StoredProfileImage(
            fileName = fileName,
            contentType = file.contentType!!,
            publicUrl = "/uploads/profile-images/$fileName",
            absolutePath = targetPath,
        )
    }

    private fun validate(file: MultipartFile) {
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image file is required")
        }
        val contentType = file.contentType?.lowercase()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image content type is required")
        if (contentType !in SUPPORTED_CONTENT_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported profile image content type: $contentType")
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile image must be 5 MB or smaller")
        }
    }

    private fun extensionOf(originalFileName: String?): String {
        val sanitized = originalFileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (sanitized.isBlank()) "" else ".$sanitized"
    }

    data class StoredProfileImage(
        val fileName: String,
        val contentType: String,
        val publicUrl: String,
        val absolutePath: Path,
    )

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L
        val SUPPORTED_CONTENT_TYPES = setOf(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/gif",
        )
    }
}
