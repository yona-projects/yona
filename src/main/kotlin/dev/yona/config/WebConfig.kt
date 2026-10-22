package dev.yona.config

import dev.yona.web.access.AnonymousCheckInterceptor
import dev.yona.web.access.ProjectCheckInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 웹 계층의 공통 설정이다.
 *
 * v1.6 `conf/routes`는 `public/` 폴더를 `/assets/` 아래로 제공하고(`GET /assets/:file`),
 * `/favicon.ico`를 `public/images/favicon.ico`에 연결한다. `public/`의 내용은
 * `src/main/resources/static/assets/`에 그대로 복사해 두었으므로, Spring Boot의 기본 정적 자원 처리가
 * `/assets/` 경로를 제공한다. 여기서는 루트에 있어야 하는 favicon만 추가로 연결한다.
 */
@Configuration(proxyBeanMethods = false)
class WebConfig(
    private val anonymousCheckInterceptor: AnonymousCheckInterceptor,
    private val projectCheckInterceptor: ProjectCheckInterceptor
) : WebMvcConfigurer {

    /** 현재 시각을 알려 주는 시계이다. 시각에 의존하는 표시를 테스트에서 고정할 수 있게 주입해서 쓴다. */
    @Bean
    fun clock(): java.time.Clock = java.time.Clock.systemDefaultZone()

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(anonymousCheckInterceptor)
        registry.addInterceptor(projectCheckInterceptor)
    }


    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/favicon.ico")
            .addResourceLocations("classpath:/static/assets/images/")
    }
}
