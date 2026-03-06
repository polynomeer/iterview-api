package com.example.interviewplatform.user.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CompanyRepositoryIntegrationTest {
    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Test
    fun `findAllByOrderByNameAsc returns seeded reference companies`() {
        val companies = companyRepository.findAllByOrderByNameAsc()

        assertTrue(companies.size >= 4)
        assertEquals("Amazon", companies.first().name)
    }
}
