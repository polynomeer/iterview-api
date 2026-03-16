package com.example.interviewplatform.interview.service

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface InterviewLlmApiTransport {
    fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String
    fun postMultipart(url: String, apiKey: String, parts: Map<String, InterviewLlmMultipartPart>, timeout: Duration): String
}

@Component
class HttpInterviewLlmApiTransport : InterviewLlmApiTransport {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("OpenAI interview follow-up request failed with status ${response.statusCode()}")
        }
        return response.body()
    }

    override fun postMultipart(
        url: String,
        apiKey: String,
        parts: Map<String, InterviewLlmMultipartPart>,
        timeout: Duration,
    ): String {
        val boundary = "----iterview-${System.currentTimeMillis()}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, parts)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("OpenAI multipart request failed with status ${response.statusCode()}")
        }
        return response.body()
    }

    private fun buildMultipartBody(boundary: String, parts: Map<String, InterviewLlmMultipartPart>): ByteArray {
        val lineBreak = "\r\n"
        val bytes = mutableListOf<Byte>()
        parts.forEach { (name, part) ->
            append(bytes, "--$boundary$lineBreak")
            when (part) {
                is InterviewLlmMultipartPart.TextPart -> {
                    append(bytes, "Content-Disposition: form-data; name=\"$name\"$lineBreak$lineBreak")
                    append(bytes, part.value)
                    append(bytes, lineBreak)
                }

                is InterviewLlmMultipartPart.FilePart -> {
                    append(
                        bytes,
                        "Content-Disposition: form-data; name=\"$name\"; filename=\"${part.fileName}\"$lineBreak",
                    )
                    append(bytes, "Content-Type: ${part.contentType}$lineBreak$lineBreak")
                    bytes.addAll(part.bytes.toList())
                    append(bytes, lineBreak)
                }
            }
        }
        append(bytes, "--$boundary--$lineBreak")
        return bytes.toByteArray()
    }

    private fun append(target: MutableList<Byte>, text: String) {
        target.addAll(text.toByteArray(Charsets.UTF_8).toList())
    }
}

sealed interface InterviewLlmMultipartPart {
    data class TextPart(val value: String) : InterviewLlmMultipartPart
    data class FilePart(val fileName: String, val contentType: String, val bytes: ByteArray) : InterviewLlmMultipartPart
}
