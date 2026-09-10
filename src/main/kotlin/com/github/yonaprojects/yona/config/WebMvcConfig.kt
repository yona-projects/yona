package com.github.yonaprojects.yona.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import java.util.Locale

@Configuration
class WebMvcConfig(
    private val bootstrapSetupInterceptor: BootstrapSetupInterceptor,
    private val thymeleafViewResolver: ThymeleafViewResolver,
    @Value("\${yona.feedback-url}")
    private val feedbackUrl: String
) : WebMvcConfigurer {

    @PostConstruct
    fun init() {
        // Thymeleaf 뷰가 렌더링될 때 항상 참조 가능한 전역 변수로 피드백 주소 추가
        thymeleafViewResolver.addStaticVariable("feedbackUrl", feedbackUrl)
    }

    // P3-51: Spring Boot 기본 AcceptHeaderLocaleResolver는 defaultLocale이 없으면
    // Accept-Language 헤더가 없는 요청의 로케일을 HttpServletRequest.getLocale()(서버 JVM/OS
    // 시스템 기본 로케일)로 되돌린다 — 배포 환경마다 달라지는 비결정적 동작이다. legacy
    // `application.langs="en-US, ko-KR, ..."`의 첫 값(en-US)이 진짜 기본값이었던 것과 동치가
    // 되도록 영어를 명시적 기본값으로 고정한다(spring.messages.fallback-to-system-locale:
    // false와 짝을 이루는 조치 — 그것만으로는 "헤더 자체가 없는" 요청까지 결정적으로 만들지
    // 못한다, 로케일 결정 자체가 다른 메커니즘(LocaleResolver)이기 때문).
    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = AcceptHeaderLocaleResolver()
        resolver.setDefaultLocale(Locale.ENGLISH)
        return resolver
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(bootstrapSetupInterceptor)
            .addPathPatterns("/**")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/")
    }
}
