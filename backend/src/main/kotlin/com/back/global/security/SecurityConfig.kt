package com.back.global.security

import com.back.global.appConfig.SiteProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
    private val customOAuth2LoginSuccessHandler: CustomOAuth2LoginSuccessHandler,
    private val customOAuth2AuthorizationRequestResolver: CustomOAuth2AuthorizationRequestResolver,
    private val siteProperties: SiteProperties
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            // URL 권한 설정
            authorizeHttpRequests {
                authorize("/favicon.ico", permitAll)
                authorize("/h2-console/**", permitAll)
                authorize(HttpMethod.GET, "/api/*/posts", permitAll)
                authorize(HttpMethod.GET, "/api/*/posts/{id:\\d+}", permitAll)
                authorize(HttpMethod.GET, "/api/*/posts/{postId:\\d+}/comments", permitAll)
                authorize(HttpMethod.GET, "/api/*/posts/{postId:\\d+}/comments/{commentId:\\d+}", permitAll)
                authorize(HttpMethod.POST, "/api/v1/members/login", permitAll)
                authorize(HttpMethod.POST, "/api/v1/members/join", permitAll)
                authorize(HttpMethod.DELETE, "/api/v1/members/logout", permitAll)
                authorize("/api/*/adm/**", hasRole("ADMIN"))
                authorize("/api/*/**", authenticated)
                authorize(anyRequest, permitAll)
            }

            // CSRF 비활성화
            csrf { disable() }

            // X-Frame-Options
            headers {
                frameOptions { sameOrigin = true }
            }

            // 커스텀 필터
            addFilterBefore<UsernamePasswordAuthenticationFilter>(customAuthenticationFilter)

            // 세션 Stateless
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }

            // OAuth2 Login
            oauth2Login {
                authorizationEndpoint {
                    authorizationRequestResolver = customOAuth2AuthorizationRequestResolver
                }
                redirectionEndpoint {
                    // siteProperties.backUrl 기반으로 dev/prod 구분
                    baseUri = "${siteProperties.backUrl}/login/oauth2/code/*"
                }
                authenticationSuccessHandler = customOAuth2LoginSuccessHandler
            }

            // 예외 처리
            exceptionHandling {
                authenticationEntryPoint = AuthenticationEntryPoint { _, response, _ ->
                    response.contentType = "application/json; charset=UTF-8"
                    response.status = 401
                    response.writer.write(
                        """
                        {
                            "resultCode": "401-1",
                            "msg": "로그인 후 이용해주세요."
                        }
                        """.trimIndent()
                    )
                }
                accessDeniedHandler = AccessDeniedHandler { _, response, _ ->
                    response.contentType = "application/json; charset=UTF-8"
                    response.status = 403
                    response.writer.write(
                        """
                        {
                            "resultCode": "403-1",
                            "msg": "권한이 없습니다."
                        }
                        """.trimIndent()
                    )
                }
            }
        }
        return http.build()
    }

    // CORS 설정
    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(siteProperties.frontUrl)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }
}
