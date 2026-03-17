package com.example.interviewplatform.common.bootstrap

import com.example.interviewplatform.resume.repository.ResumeRepository
import com.example.interviewplatform.resume.repository.ResumeVersionRepository
import com.example.interviewplatform.resume.service.ResumeQuestionHeatmapService
import com.example.interviewplatform.user.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("local", "test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LocalResumeHeatmapDemoDataInitializerTest {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var resumeRepository: ResumeRepository

    @Autowired
    private lateinit var resumeVersionRepository: ResumeVersionRepository

    @Autowired
    private lateinit var resumeQuestionHeatmapService: ResumeQuestionHeatmapService

    @Test
    fun `local profile seeds heatmap demo data`() {
        val user = userRepository.findByEmail("demo-heatmap@iterview.local")
        assertNotNull(user)

        val resume = resumeRepository.findByUserIdOrderByCreatedAtDesc(user!!.id)
            .firstOrNull { it.title == "Heatmap Overlay Demo Resume" }
        assertNotNull(resume)

        val version = resumeVersionRepository.findTopByResumeIdOrderByVersionNoDesc(resume!!.id)
        assertNotNull(version)

        val heatmap = resumeQuestionHeatmapService.getHeatmap(
            userId = user.id,
            versionId = version!!.id,
            scope = "all",
        )

        assertTrue(heatmap.items.isNotEmpty())
        assertTrue(heatmap.filterSummary.totalQuestions >= 4)
        assertTrue(heatmap.filterSummary.distinctCompanyCount >= 2)
        assertTrue(heatmap.filterSummary.availableTargetTypes.isNotEmpty())

        val sentenceOnly = resumeQuestionHeatmapService.getOverlayTargets(
            userId = user.id,
            versionId = version.id,
            scope = "all",
            targetType = "sentence",
        )
        assertEquals("sentence", sentenceOnly.appliedFilters.targetType)
        assertTrue(sentenceOnly.items.all { it.targetType == "sentence" })
    }
}
