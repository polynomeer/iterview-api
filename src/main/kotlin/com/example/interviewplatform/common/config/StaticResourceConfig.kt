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
    @Value("\${app.storage.interview-audio-dir:uploads/interview-audio}")
    private val interviewAudioDir: String,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absoluteProfileDir = Paths.get(profileImageDir).toAbsolutePath().normalize().toUri().toString()
        val absoluteInterviewAudioDir = Paths.get(interviewAudioDir).toAbsolutePath().normalize().toUri().toString()
        registry.addResourceHandler("/uploads/profile-images/**")
            .addResourceLocations(absoluteProfileDir)
        registry.addResourceHandler("/uploads/interview-audio/**")
            .addResourceLocations(absoluteInterviewAudioDir)
    }
}
