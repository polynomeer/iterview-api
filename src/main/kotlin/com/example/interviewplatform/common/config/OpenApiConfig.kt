package com.example.interviewplatform.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Interview Platform API")
                .version("v1")
                .description("MVP backend APIs for interview training platform"),
        )
        .components(
            Components().addSecuritySchemes(
                SECURITY_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("Token"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(SECURITY_SCHEME))

    companion object {
        const val SECURITY_SCHEME = "bearerAuth"
    }
}
