package com.example.interviewplatform.resume.service

import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface ResumeLlmApiTransport {
    fun postJson(url: String, apiKey: String, body: String, timeout: Duration): String
}

@Component
class HttpResumeLlmApiTransport : ResumeLlmApiTransport {
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
            throw IllegalStateException("OpenAI extraction request failed with status ${response.statusCode()}")
        }
        return response.body()
    }
}

