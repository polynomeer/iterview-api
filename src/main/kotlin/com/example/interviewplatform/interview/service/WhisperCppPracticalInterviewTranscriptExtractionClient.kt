package com.example.interviewplatform.interview.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(
    name = ["app.interview.transcription.provider"],
    havingValue = "whisper_cpp",
    matchIfMissing = true,
)
class WhisperCppPracticalInterviewTranscriptExtractionClient(
    @Value("\${app.interview.transcription.whisper.command:whisper-cli}")
    private val whisperCommand: String,
    @Value("\${app.interview.transcription.whisper.model-path:}")
    private val whisperModelPath: String,
    @Value("\${app.interview.transcription.whisper.language:auto}")
    private val whisperLanguage: String,
    @Value("\${app.interview.transcription.whisper.timeout-seconds:1800}")
    private val whisperTimeoutSeconds: Long,
    @Value("\${app.interview.transcription.whisper.ffmpeg-command:ffmpeg}")
    private val ffmpegCommand: String,
    @Value("\${app.interview.transcription.whisper.ffmpeg-timeout-seconds:300}")
    private val ffmpegTimeoutSeconds: Long,
) : PracticalInterviewTranscriptExtractionClient {
    override fun isEnabled(): Boolean = whisperModelPath.isNotBlank() && Files.isRegularFile(Path.of(whisperModelPath))

    override fun extract(input: PracticalInterviewTranscriptExtractionInput): ExtractedPracticalInterviewTranscript {
        require(isEnabled()) {
            "Whisper.cpp practical interview transcription is not configured. " +
                "Set app.interview.transcription.whisper.model-path."
        }
        val normalizedAudioPath = convertToWhisperCompatibleWav(input.audioFilePath)
        try {
            val output = runWhisper(
                audioPath = normalizedAudioPath,
                languageHint = input.languageHint,
            )
            val transcript = parseWhisperOutput(output)
            if (transcript.isBlank()) {
                throw IllegalStateException("Whisper.cpp transcription returned empty output.")
            }
            return ExtractedPracticalInterviewTranscript(
                transcriptText = transcript,
                llmModel = "whisper.cpp",
            )
        } finally {
            Files.deleteIfExists(normalizedAudioPath)
        }
    }

    private fun convertToWhisperCompatibleWav(sourceAudioPath: Path): Path {
        val targetPath = Files.createTempFile("iterview-whisper-input-", ".wav")
        val command = listOf(
            ffmpegCommand,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            sourceAudioPath.toString(),
            "-vn",
            "-ac",
            "1",
            "-ar",
            "16000",
            "-c:a",
            "pcm_s16le",
            targetPath.toString(),
        )
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val finished = process.waitFor(ffmpegTimeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            Files.deleteIfExists(targetPath)
            throw IllegalStateException("ffmpeg audio conversion timed out after ${ffmpegTimeoutSeconds}s.")
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(targetPath)
            throw IllegalStateException("ffmpeg audio conversion failed with exit code ${process.exitValue()}: $output")
        }
        return targetPath
    }

    private fun runWhisper(audioPath: Path, languageHint: String?): String {
        val requestedLanguage = resolveWhisperLanguageCode(inputLanguageHint = languageHint)
        val command = mutableListOf(
            whisperCommand,
            "-m",
            whisperModelPath,
            "-f",
            audioPath.toString(),
        )
        if (requestedLanguage != null) {
            command += listOf("-l", requestedLanguage)
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val finished = process.waitFor(whisperTimeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Whisper.cpp transcription timed out after ${whisperTimeoutSeconds}s.")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("Whisper.cpp transcription failed with exit code ${process.exitValue()}: $output")
        }
        return output
    }

    private fun resolveWhisperLanguageCode(inputLanguageHint: String?): String? {
        val normalizedHint = inputLanguageHint?.trim()?.lowercase()
        if (normalizedHint == "ko" || normalizedHint == "en") {
            return normalizedHint
        }
        val configured = whisperLanguage.trim().lowercase()
        if (configured == "auto" || configured.isBlank()) {
            return null
        }
        return configured
    }

    private fun parseWhisperOutput(output: String): String {
        val timestampPattern = Regex(
            """^\s*\[(\d{2}:\d{2}(?::\d{2})?\.\d{3})\s*-->\s*(\d{2}:\d{2}(?::\d{2})?\.\d{3})]\s*(.+?)\s*$""",
        )
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val match = timestampPattern.matchEntire(line)
                if (match != null) {
                    val start = match.groupValues[1]
                    val text = match.groupValues[3]
                    "[${start}] ${heuristicSpeakerLabel(text)}"
                } else if (!line.startsWith("whisper_") && !line.startsWith("system_info:")) {
                    heuristicSpeakerLabel(line)
                } else {
                    null
                }
            }
            .toList()
        return lines.joinToString("\n").trim()
    }

    private fun heuristicSpeakerLabel(text: String): String {
        val normalized = text.trim()
        val lowered = normalized.lowercase()
        val alreadyLabeled = lowered.startsWith("interviewer:") ||
            lowered.startsWith("candidate:") ||
            lowered.startsWith("면접관:") ||
            lowered.startsWith("지원자:") ||
            lowered.startsWith("q:") ||
            lowered.startsWith("a:")
        if (alreadyLabeled) {
            return normalized
        }
        return if (normalized.endsWith("?")) {
            "interviewer: $normalized"
        } else {
            "candidate: $normalized"
        }
    }
}
