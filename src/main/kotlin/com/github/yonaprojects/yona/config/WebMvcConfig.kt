package com.github.yonaprojects.yona.config

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.thymeleaf.spring6.view.ThymeleafViewResolver

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

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(bootstrapSetupInterceptor)
            .addPathPatterns("/**")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/")
    }
}
