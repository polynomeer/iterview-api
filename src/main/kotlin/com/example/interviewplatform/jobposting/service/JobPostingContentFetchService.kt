package com.example.interviewplatform.jobposting.service

import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class JobPostingContentFetchService {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetch(sourceUrl: String): FetchedJobPostingContent {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(sourceUrl))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "IterviewBot/1.0")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Job posting fetch failed with status ${response.statusCode()}")
        }
        val body = response.body()
        val title = TITLE_REGEX.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedText = htmlToText(body)
        if (normalizedText.isBlank()) {
            throw IllegalStateException("Fetched job posting did not contain readable text")
        }
        return FetchedJobPostingContent(
            title = title,
            rawText = normalizedText,
        )
    }

    private fun htmlToText(html: String): String {
        val withoutScripts = SCRIPT_REGEX.replace(html, " ")
        val withoutStyles = STYLE_REGEX.replace(withoutScripts, " ")
        return TAG_REGEX.replace(withoutStyles, "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private companion object {
        private val TITLE_REGEX = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val SCRIPT_REGEX = Regex("""<script\b[^<]*(?:(?!</script>)<[^<]*)*</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val STYLE_REGEX = Regex("""<style\b[^<]*(?:(?!</style>)<[^<]*)*</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val TAG_REGEX = Regex("""<[^>]+>""")
    }
}

data class FetchedJobPostingContent(
    val title: String?,
    val rawText: String,
)
