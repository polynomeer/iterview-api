package com.example.interviewplatform.common.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Health")
@RestController
@RequestMapping("/api/health")
class HealthController {
    @GetMapping
    @Operation(summary = "Health check")
    fun health(): Map<String, String> = mapOf("status" to "ok")
}
