package com.example.interviewplatform.resume.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant

@Service
class ResumeAnalysisExportFileStorageService(
    @Value("\${app.storage.resume-analysis-export-dir:uploads/resume-analysis-exports}")
    private val exportDir: String,
) {
    fun store(userId: Long, analysisId: Long, fileName: String, content: ByteArray, now: Instant): StoredResumeAnalysisExportFile {
        val directory = Paths.get(exportDir, "user-$userId", "analysis-$analysisId").toAbsolutePath().normalize()
        Files.createDirectories(directory)
        val storageFileName = "resume-analysis-${analysisId}-${now.toEpochMilli()}.pdf"
        val targetPath = directory.resolve(storageFileName).normalize()
        Files.write(targetPath, content)
        return StoredResumeAnalysisExportFile(
            storageKey = Paths.get("user-$userId", "analysis-$analysisId", storageFileName).toString().replace('\\', '/'),
            absolutePath = targetPath,
            fileName = fileName,
            fileSizeBytes = Files.size(targetPath),
            checksumSha256 = sha256(targetPath),
        )
    }

    fun load(storageKey: String): Resource {
        val root = Paths.get(exportDir).toAbsolutePath().normalize()
        val candidate = root.resolve(storageKey).normalize()
        require(candidate.startsWith(root)) { "Invalid storage key" }
        return FileSystemResource(candidate)
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

data class StoredResumeAnalysisExportFile(
    val storageKey: String,
    val absolutePath: Path,
    val fileName: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
)
