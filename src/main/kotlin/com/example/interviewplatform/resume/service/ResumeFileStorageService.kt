package com.example.interviewplatform.resume.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant

@Service
class ResumeFileStorageService(
    @Value("\${app.storage.resume-dir:uploads/resume-files}")
    private val resumeDir: String,
) {
    fun store(userId: Long, resumeId: Long, versionNo: Int, file: MultipartFile, now: Instant): StoredResumeFile {
        val originalName = sanitizeFileName(file.originalFilename)
        val directory = Paths.get(resumeDir, "user-$userId", "resume-$resumeId").toAbsolutePath().normalize()
        Files.createDirectories(directory)

        val storageFileName = buildString {
            append("resume-v")
            append(versionNo)
            append("-")
            append(now.toEpochMilli())
            append(".pdf")
        }
        val targetPath = directory.resolve(storageFileName).normalize()
        file.transferTo(targetPath)

        return StoredResumeFile(
            storageKey = Paths.get("user-$userId", "resume-$resumeId", storageFileName).toString().replace('\\', '/'),
            absolutePath = targetPath,
            fileName = originalName,
            fileSizeBytes = Files.size(targetPath),
            checksumSha256 = sha256(targetPath),
        )
    }

    fun load(storageKey: String): Resource {
        val path = resolve(storageKey)
        return FileSystemResource(path)
    }

    private fun resolve(storageKey: String): Path {
        val root = Paths.get(resumeDir).toAbsolutePath().normalize()
        val candidate = root.resolve(storageKey).normalize()
        require(candidate.startsWith(root)) { "Invalid storage key" }
        return candidate
    }

    private fun sanitizeFileName(originalFilename: String?): String {
        val cleaned = StringUtils.cleanPath(originalFilename ?: "resume.pdf")
        return cleaned.substringAfterLast('/').substringAfterLast('\\').ifBlank { "resume.pdf" }
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
