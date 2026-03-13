package com.example.interviewplatform.common.config

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource

@Configuration
class LocalizationConfig {
    @Bean
    fun messageSource(): MessageSource = ReloadableResourceBundleMessageSource().apply {
        setBasenames("classpath:i18n/messages")
        setDefaultEncoding("UTF-8")
        setFallbackToSystemLocale(false)
        setUseCodeAsDefaultMessage(true)
    }
}
