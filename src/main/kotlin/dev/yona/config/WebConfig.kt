package dev.yona.config

import org.springframework.context.annotation.Configuration
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
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/favicon.ico")
            .addResourceLocations("classpath:/static/assets/images/")
    }
}
