package com.example.interviewplatform.common.service

import com.example.interviewplatform.user.repository.UserSettingsRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.Locale

@Service
class AppLocaleService(
    private val currentUserProvider: CurrentUserProvider,
    private val userSettingsRepository: UserSettingsRepository,
    private val messageSource: MessageSource,
) {
    fun resolveLanguage(): String = resolveLanguage(currentRequest())

    fun resolveLanguage(request: HttpServletRequest?): String {
        val explicit = request?.getHeader(HEADER_APP_LOCALE)?.trim()?.lowercase()?.takeIf { it in SUPPORTED_LANGUAGES }
        if (explicit != null) {
            return explicit
        }

        val preferred = currentUserProvider.currentUserIdOrNull()
            ?.let { userId -> userSettingsRepository.findById(userId).orElse(null)?.preferredLanguage }
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in SUPPORTED_LANGUAGES }
        if (preferred != null) {
            return preferred
        }

        val acceptLanguage = request?.getHeader("Accept-Language")
            ?.split(",")
            ?.asSequence()
            ?.map { it.substringBefore(";").trim().lowercase() }
            ?.map { Locale.forLanguageTag(it).language.lowercase() }
            ?.firstOrNull { it in SUPPORTED_LANGUAGES }
        if (acceptLanguage != null) {
            return acceptLanguage
        }

        return DEFAULT_LANGUAGE
    }

    fun resolveLocale(request: HttpServletRequest?): Locale = Locale.forLanguageTag(resolveLanguage(request))

    fun resolveLocale(): Locale = Locale.forLanguageTag(resolveLanguage())

    fun getMessage(code: String, request: HttpServletRequest?, vararg args: Any): String =
        messageSource.getMessage(code, args, resolveLocale(request))

    private fun currentRequest(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    companion object {
        const val HEADER_APP_LOCALE = "X-App-Locale"
        const val DEFAULT_LANGUAGE = "ko"
        val SUPPORTED_LANGUAGES = setOf("ko", "en")
    }
}
