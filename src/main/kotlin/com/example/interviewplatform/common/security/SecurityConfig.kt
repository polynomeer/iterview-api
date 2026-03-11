package com.example.interviewplatform.common.security

import com.example.interviewplatform.auth.security.AuthTokenFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val authTokenFilter: AuthTokenFilter,
    private val authenticationEntryPoint: ApiAuthenticationEntryPoint,
    private val accessDeniedHandler: ApiAccessDeniedHandler,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/api/health",
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                    .requestMatchers("/api/me/**").authenticated()
                    .requestMatchers("/api/resumes/**", "/api/resume-versions/**").authenticated()
                    .requestMatchers("/api/questions/resume-based").authenticated()
                    .requestMatchers("/api/skills/**").authenticated()
                    .requestMatchers("/api/questions/*/answers/**", "/api/answer-attempts/**").authenticated()
                    .requestMatchers("/api/home/**", "/api/daily-cards/**").authenticated()
                    .requestMatchers("/api/review-queue/**", "/api/archive/**").authenticated()
                    .requestMatchers("/api/feed/**", "/api/auth/me").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
