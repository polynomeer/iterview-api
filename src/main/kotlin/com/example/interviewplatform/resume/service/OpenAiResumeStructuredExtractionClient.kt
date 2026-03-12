package com.example.interviewplatform.resume.service

import com.example.interviewplatform.resume.entity.ResumeVersionEntity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

@Component
class OpenAiResumeStructuredExtractionClient(
    private val objectMapper: ObjectMapper,
    private val transport: ResumeLlmApiTransport,
    @Value("\${app.resume.llm.api-key:}")
    private val apiKey: String,
    @Value("\${app.resume.llm.base-url:https://api.openai.com/v1}")
    private val baseUrl: String,
    @Value("\${app.resume.llm.model:gpt-5-mini}")
    private val model: String,
    @Value("\${app.resume.llm.prompt-version:resume-extract-v1}")
    private val promptVersion: String,
    @Value("\${app.resume.llm.timeout-seconds:30}")
    private val timeoutSeconds: Long,
) : ResumeStructuredExtractionClient {
    override fun isEnabled(): Boolean = apiKey.isNotBlank()

    override fun extract(version: ResumeVersionEntity): ExtractedResumeSignals {
        require(isEnabled()) { "OpenAI extraction is not configured" }
        val body = objectMapper.writeValueAsString(buildRequest(version))
        val response = transport.postJson(
            url = "${baseUrl.trimEnd('/')}/responses",
            apiKey = apiKey,
            body = body,
            timeout = Duration.ofSeconds(timeoutSeconds),
        )
        return parseResponse(response)
    }

    private fun buildRequest(version: ResumeVersionEntity): Map<String, Any> = mapOf(
        "model" to model,
        "input" to listOf(
            mapOf(
                "role" to "system",
                "content" to listOf(mapOf("type" to "input_text", "text" to buildSystemPrompt())),
            ),
            mapOf(
                "role" to "user",
                "content" to listOf(mapOf("type" to "input_text", "text" to buildUserPrompt(version))),
            ),
        ),
        "text" to mapOf(
            "format" to mapOf(
                "type" to "json_schema",
                "name" to "resume_extraction",
                "strict" to true,
                "schema" to extractionSchema(),
            ),
        ),
    )

    private fun parseResponse(responseBody: String): ExtractedResumeSignals {
        val root = objectMapper.readTree(responseBody)
        val outputText = root.path("output_text").asText(null)
            ?: throw IllegalStateException("OpenAI extraction response did not include output_text")
        val extractedRoot = objectMapper.readTree(outputText)
        return ExtractedResumeSignals(
            profile = extractedRoot.path("profile").takeIf { !it.isMissingNode && !it.isNull }?.let(::toProfile),
            contacts = extractedRoot.path("contacts").mapIndexed { index, node -> toContact(node, index + 1) },
            competencies = extractedRoot.path("competencies").mapIndexed { index, node -> toCompetency(node, index + 1) },
            skills = extractedRoot.path("skills").map(::toSkill),
            experiences = extractedRoot.path("experiences").mapIndexed { index, node -> toExperience(node, index + 1) },
            projects = extractedRoot.path("projects").mapIndexed { index, node -> toProject(node, index + 1) },
            achievements = extractedRoot.path("achievements").mapIndexed { index, node -> toAchievement(node, index + 1) },
            educationItems = extractedRoot.path("educationItems").mapIndexed { index, node -> toEducation(node, index + 1) },
            certificationItems = extractedRoot.path("certificationItems").mapIndexed { index, node -> toCertification(node, index + 1) },
            awardItems = extractedRoot.path("awardItems").mapIndexed { index, node -> toAward(node, index + 1) },
            risks = extractedRoot.path("risks").map(::toRisk),
            sourceType = "openai",
            extractionStatus = "completed",
            extractionErrorMessage = null,
            extractionConfidence = extractedRoot.path("overallConfidence").takeIf(JsonNode::isNumber)?.asDouble(),
            llmModel = root.path("model").asText(model),
            llmPromptVersion = promptVersion,
            rawExtractionPayload = outputText,
        )
    }

    private fun buildSystemPrompt(): String = """
        You extract structured interview-preparation signals from a software engineer's resume.
        Preserve section boundaries and claims from the source document.
        Do not invent fields that the resume does not support.
        Normalize dates to YYYY-MM-DD when month-level data exists, using day 01.
        Normalize riskLevel to low, medium, or high.
        Normalize severity to LOW, MEDIUM, or HIGH.
        Return only schema-compliant JSON.
    """.trimIndent()

    private fun buildUserPrompt(version: ResumeVersionEntity): String = buildString {
        appendLine("Prompt version: $promptVersion")
        version.summaryText?.takeIf { it.isNotBlank() }?.let {
            appendLine("Resume summary:")
            appendLine(it.trim())
            appendLine()
        }
        appendLine("Resume raw text:")
        appendLine(version.rawText.orEmpty().trim())
    }

    private fun extractionSchema(): Map<String, Any> = mapOf(
        "type" to "object",
        "additionalProperties" to false,
        "properties" to mapOf(
            "profile" to nullableObject(
                "fullName" to nullableString(),
                "headline" to nullableString(),
                "summaryText" to nullableString(),
                "locationText" to nullableString(),
                "yearsOfExperienceText" to nullableString(),
                "sourceText" to nullableString(),
            ),
            "contacts" to arrayOfObjects(
                "contactType" to string(),
                "label" to nullableString(),
                "valueText" to nullableString(),
                "url" to nullableString(),
                "isPrimary" to boolean(),
            ),
            "competencies" to arrayOfObjects(
                "title" to string(),
                "description" to string(),
                "sourceText" to nullableString(),
            ),
            "skills" to arrayOfObjects(
                "skillName" to string(),
                "sourceText" to nullableString(),
                "confidenceScore" to nullableNumber(),
            ),
            "experiences" to arrayOfObjects(
                "projectName" to nullableString(),
                "companyName" to nullableString(),
                "roleName" to nullableString(),
                "employmentType" to nullableString(),
                "startedOn" to nullableString(),
                "endedOn" to nullableString(),
                "isCurrent" to boolean(),
                "summaryText" to string(),
                "impactText" to nullableString(),
                "sourceText" to string(),
                "riskLevel" to enumString("low", "medium", "high"),
            ),
            "projects" to arrayOfObjects(
                "title" to string(),
                "organizationName" to nullableString(),
                "roleName" to nullableString(),
                "summaryText" to string(),
                "techStackText" to nullableString(),
                "startedOn" to nullableString(),
                "endedOn" to nullableString(),
                "sourceText" to nullableString(),
                "experienceDisplayOrder" to nullableInteger(),
            ),
            "achievements" to arrayOfObjects(
                "title" to string(),
                "metricText" to nullableString(),
                "impactSummary" to string(),
                "sourceText" to nullableString(),
                "severityHint" to nullableString(),
                "experienceDisplayOrder" to nullableInteger(),
                "projectDisplayOrder" to nullableInteger(),
            ),
            "educationItems" to arrayOfObjects(
                "institutionName" to string(),
                "degreeName" to nullableString(),
                "fieldOfStudy" to nullableString(),
                "startedOn" to nullableString(),
                "endedOn" to nullableString(),
                "description" to nullableString(),
                "sourceText" to nullableString(),
            ),
            "certificationItems" to arrayOfObjects(
                "name" to string(),
                "issuerName" to nullableString(),
                "credentialCode" to nullableString(),
                "issuedOn" to nullableString(),
                "expiresOn" to nullableString(),
                "scoreText" to nullableString(),
                "sourceText" to nullableString(),
            ),
            "awardItems" to arrayOfObjects(
                "title" to string(),
                "issuerName" to nullableString(),
                "awardedOn" to nullableString(),
                "description" to nullableString(),
                "sourceText" to nullableString(),
            ),
            "risks" to arrayOfObjects(
                "riskType" to string(),
                "title" to string(),
                "description" to string(),
                "severity" to enumString("LOW", "MEDIUM", "HIGH"),
            ),
            "overallConfidence" to nullableNumber(),
        ),
        "required" to listOf(
            "profile",
            "contacts",
            "competencies",
            "skills",
            "experiences",
            "projects",
            "achievements",
            "educationItems",
            "certificationItems",
            "awardItems",
            "risks",
            "overallConfidence",
        ),
    )

    private fun toProfile(node: JsonNode): ExtractedResumeProfile = ExtractedResumeProfile(
        fullName = node.path("fullName").asText(null),
        headline = node.path("headline").asText(null),
        summaryText = node.path("summaryText").asText(null),
        locationText = node.path("locationText").asText(null),
        yearsOfExperienceText = node.path("yearsOfExperienceText").asText(null),
        sourceText = node.path("sourceText").asText(null),
    )

    private fun toContact(node: JsonNode, displayOrder: Int): ExtractedResumeContactPoint = ExtractedResumeContactPoint(
        contactType = node.path("contactType").asText(),
        label = node.path("label").asText(null),
        valueText = node.path("valueText").asText(null),
        url = node.path("url").asText(null),
        displayOrder = displayOrder,
        isPrimary = node.path("isPrimary").asBoolean(false),
    )

    private fun toCompetency(node: JsonNode, displayOrder: Int): ExtractedResumeCompetency = ExtractedResumeCompetency(
        title = node.path("title").asText(),
        description = node.path("description").asText(),
        sourceText = node.path("sourceText").asText(null),
        displayOrder = displayOrder,
    )

    private fun toSkill(node: JsonNode): ExtractedResumeSkill = ExtractedResumeSkill(
        skillName = node.path("skillName").asText(),
        sourceText = node.path("sourceText").asText(null),
        confidenceScore = node.path("confidenceScore").takeIf(JsonNode::isNumber)?.asDouble(),
    )

    private fun toExperience(node: JsonNode, displayOrder: Int): ExtractedResumeExperience = ExtractedResumeExperience(
        projectName = node.path("projectName").asText(null),
        companyName = node.path("companyName").asText(null),
        roleName = node.path("roleName").asText(null),
        employmentType = node.path("employmentType").asText(null),
        startedOn = node.path("startedOn").asText(null)?.let(LocalDate::parse),
        endedOn = node.path("endedOn").asText(null)?.let(LocalDate::parse),
        isCurrent = node.path("isCurrent").asBoolean(false),
        summaryText = node.path("summaryText").asText(),
        impactText = node.path("impactText").asText(null),
        sourceText = node.path("sourceText").asText(),
        riskLevel = node.path("riskLevel").asText("medium"),
        displayOrder = displayOrder,
    )

    private fun toProject(node: JsonNode, displayOrder: Int): ExtractedResumeProject = ExtractedResumeProject(
        title = node.path("title").asText(),
        organizationName = node.path("organizationName").asText(null),
        roleName = node.path("roleName").asText(null),
        summaryText = node.path("summaryText").asText(),
        techStackText = node.path("techStackText").asText(null),
        startedOn = node.path("startedOn").asText(null)?.let(LocalDate::parse),
        endedOn = node.path("endedOn").asText(null)?.let(LocalDate::parse),
        displayOrder = displayOrder,
        sourceText = node.path("sourceText").asText(null),
        experienceDisplayOrder = node.path("experienceDisplayOrder").takeIf(JsonNode::isInt)?.asInt(),
    )

    private fun toAchievement(node: JsonNode, displayOrder: Int): ExtractedResumeAchievement = ExtractedResumeAchievement(
        title = node.path("title").asText(),
        metricText = node.path("metricText").asText(null),
        impactSummary = node.path("impactSummary").asText(),
        sourceText = node.path("sourceText").asText(null),
        severityHint = node.path("severityHint").asText(null),
        displayOrder = displayOrder,
        experienceDisplayOrder = node.path("experienceDisplayOrder").takeIf(JsonNode::isInt)?.asInt(),
        projectDisplayOrder = node.path("projectDisplayOrder").takeIf(JsonNode::isInt)?.asInt(),
    )

    private fun toEducation(node: JsonNode, displayOrder: Int): ExtractedResumeEducation = ExtractedResumeEducation(
        institutionName = node.path("institutionName").asText(),
        degreeName = node.path("degreeName").asText(null),
        fieldOfStudy = node.path("fieldOfStudy").asText(null),
        startedOn = node.path("startedOn").asText(null)?.let(LocalDate::parse),
        endedOn = node.path("endedOn").asText(null)?.let(LocalDate::parse),
        description = node.path("description").asText(null),
        displayOrder = displayOrder,
        sourceText = node.path("sourceText").asText(null),
    )

    private fun toCertification(node: JsonNode, displayOrder: Int): ExtractedResumeCertification = ExtractedResumeCertification(
        name = node.path("name").asText(),
        issuerName = node.path("issuerName").asText(null),
        credentialCode = node.path("credentialCode").asText(null),
        issuedOn = node.path("issuedOn").asText(null)?.let(LocalDate::parse),
        expiresOn = node.path("expiresOn").asText(null)?.let(LocalDate::parse),
        scoreText = node.path("scoreText").asText(null),
        displayOrder = displayOrder,
        sourceText = node.path("sourceText").asText(null),
    )

    private fun toAward(node: JsonNode, displayOrder: Int): ExtractedResumeAward = ExtractedResumeAward(
        title = node.path("title").asText(),
        issuerName = node.path("issuerName").asText(null),
        awardedOn = node.path("awardedOn").asText(null)?.let(LocalDate::parse),
        description = node.path("description").asText(null),
        displayOrder = displayOrder,
        sourceText = node.path("sourceText").asText(null),
    )

    private fun toRisk(node: JsonNode): ExtractedResumeRisk = ExtractedResumeRisk(
        riskType = node.path("riskType").asText(),
        title = node.path("title").asText(),
        description = node.path("description").asText(),
        severity = node.path("severity").asText(),
    )

    private fun arrayOfObjects(vararg properties: Pair<String, Any>): Map<String, Any> = mapOf(
        "type" to "array",
        "items" to mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to properties.toMap(),
            "required" to properties.map { it.first },
        ),
    )

    private fun nullableObject(vararg properties: Pair<String, Any>): Map<String, Any> = mapOf(
        "type" to listOf("object", "null"),
        "additionalProperties" to false,
        "properties" to properties.toMap(),
        "required" to properties.map { it.first },
    )

    private fun string(): Map<String, Any> = mapOf("type" to "string")
    private fun nullableString(): Map<String, Any> = mapOf("type" to listOf("string", "null"))
    private fun nullableNumber(): Map<String, Any> = mapOf("type" to listOf("number", "null"))
    private fun nullableInteger(): Map<String, Any> = mapOf("type" to listOf("integer", "null"))
    private fun boolean(): Map<String, Any> = mapOf("type" to "boolean")
    private fun enumString(vararg values: String): Map<String, Any> = mapOf("type" to "string", "enum" to values.toList())
}
