package com.example.interviewplatform.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class StaticResourceConfig(
    @Value("\${app.storage.profile-image-dir:uploads/profile-images}")
    private val profileImageDir: String,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absoluteDir = Paths.get(profileImageDir).toAbsolutePath().normalize().toUri().toString()
        registry.addResourceHandler("/uploads/profile-images/**")
            .addResourceLocations(absoluteDir)
    }
}
