package com.example.interviewplatform.common.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class OpenApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `openapi docs endpoint exposes implemented auth and protected paths`() {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
            .andExpect(jsonPath("$.paths['/api/me']").exists())
            .andExpect(jsonPath("$.paths['/api/me/profile-image']").exists())
            .andExpect(jsonPath("$.paths['/api/home']").exists())
            .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
            .andExpect(jsonPath("$.components.schemas.AuthTokenResponse.description").value("Authentication response containing the bearer token and the authenticated user snapshot"))
            .andExpect(jsonPath("$.components.schemas.HomeResponseDto.description").value("Home payload with the primary daily card, retry queue preview, and learning materials"))
            .andExpect(jsonPath("$.components.schemas.SubmitAnswerRequest.description").value("Answer submission payload for a question attempt"))
    }
}
