package com.example.interviewplatform.interview.controller

import com.example.interviewplatform.auth.service.TokenService
import com.example.interviewplatform.interview.service.ExtractedPracticalInterviewTranscript
import com.example.interviewplatform.interview.service.PracticalInterviewTranscriptExtractionClient
import com.example.interviewplatform.interview.service.PracticalInterviewTranscriptExtractionInput
import com.example.interviewplatform.support.TestDatabaseCleaner
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class InterviewRecordTranscriptionApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var authHeader: String

    @BeforeEach
    fun setUp() {
        TestDatabaseCleaner.reset(jdbcTemplate)
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, provider, provider_user_id, status, created_at, updated_at)
            VALUES (1, 'record-user@example.com', NULL, 'local', NULL, 'ACTIVE', now(), now())
            """.trimIndent(),
        )
        authHeader = "Bearer ${tokenService.issueToken(1, "record-user@example.com")}"
    }

    @Test
    fun `uploading interview audio without transcript extracts transcript automatically`() {
        val audio = MockMultipartFile("file", "real-interview.wav", "audio/wav", "fake-audio".toByteArray())

        val created = mockMvc.perform(
            multipart("/api/interview-records")
                .file(audio)
                .param("companyName", "Example Corp")
                .header("Authorization", authHeader),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transcriptStatus").value(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.`is`("pending"), org.hamcrest.Matchers.`is`("processing"), org.hamcrest.Matchers.`is`("confirmed"))))
            .andReturn()
            .response
            .contentAsString
            .let(objectMapper::readTree)

        val recordId = created["id"].asLong()

        var finalizedTranscriptStatus = created["transcriptStatus"].asText()
        repeat(20) {
            if (finalizedTranscriptStatus == "confirmed") {
                return@repeat
            }
            Thread.sleep(100)
            val detail = mockMvc.perform(get("/api/interview-records/$recordId").header("Authorization", authHeader))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
                .let(objectMapper::readTree)
            finalizedTranscriptStatus = detail["transcriptStatus"].asText()
        }
        assertEquals("confirmed", finalizedTranscriptStatus)

        mockMvc.perform(get("/api/interview-records/$recordId/transcript").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.segments.length()").value(4))
            .andExpect(jsonPath("$.segments[0].speakerType").value("interviewer"))
            .andExpect(jsonPath("$.segments[1].speakerType").value("candidate"))
            .andExpect(jsonPath("$.confirmedTranscript").value(org.hamcrest.Matchers.containsString("interviewer: Why did you introduce Redis caching?")))

        mockMvc.perform(get("/api/interview-records/$recordId/questions").header("Authorization", authHeader))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].text").value("Why did you introduce Redis caching?"))
    }

    @TestConfiguration
    class FakeTranscriptionConfig {
        @Bean
        @Primary
        fun practicalInterviewTranscriptExtractionClient(): PracticalInterviewTranscriptExtractionClient =
            object : PracticalInterviewTranscriptExtractionClient {
                override fun isEnabled(): Boolean = true

                override fun extract(input: PracticalInterviewTranscriptExtractionInput): ExtractedPracticalInterviewTranscript =
                    ExtractedPracticalInterviewTranscript(
                        transcriptText = """
                            interviewer: Why did you introduce Redis caching?
                            candidate: To reduce database load and stabilize latency.
                            interviewer: How did you handle cache invalidation?
                            candidate: We used TTL and explicit eviction on critical writes.
                        """.trimIndent(),
                        llmModel = "fake-transcriber",
                    )
            }
    }
}
