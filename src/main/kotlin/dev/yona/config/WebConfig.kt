package dev.yona.config

import dev.yona.security.PreferredLanguageInterceptor
import dev.yona.web.access.AccessChainInterceptor
import dev.yona.web.welcome.SiteSecretInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 웹 계층의 공통 설정이다.
 *
 * v1.6 `conf/routes`는 `public/` 폴더를 `/assets/` 아래로 제공하고(`GET /assets/:file`),
 * `/favicon.ico`를 `public/images/favicon.ico`에 연결한다. `public/`의 내용은
 * `src/main/resources/static/assets/`에 그대로 복사해 두었으므로, Spring Boot의 기본 정적 자원 처리가
 * `/assets/` 경로를 제공한다. 루트에 있어야 하는 favicon은 `FaviconController`가 제공한다.
 * (자원 처리기로 연결하면 `POST /{user}` 같은 한 구간 경로 매핑이 먼저 걸려 405로 처리되어 제공되지 않는다.)
 */
@Configuration(proxyBeanMethods = false)
class WebConfig(
    private val accessChainInterceptor: AccessChainInterceptor,
    private val siteSecretInterceptor: SiteSecretInterceptor,
    private val preferredLanguageInterceptor: PreferredLanguageInterceptor,
    private val defaultHeadersInterceptor: dev.yona.web.DefaultHeadersInterceptor
) : WebMvcConfigurer {

    /** 현재 시각을 알려 주는 시계이다. 시각에 의존하는 표시를 테스트에서 고정할 수 있게 주입해서 쓴다. */
    @Bean
    fun clock(): java.time.Clock = java.time.Clock.systemDefaultZone()

    /**
     * Thymeleaf 화면의 내용 형식을 늘 `text/html`로 고정한다. 고정하지 않으면 와일드카드 Accept 요청에서 Thymeleaf가 주소의 확장자로 형식을
     * 정해(`.../a.txt`는 `text/plain`, `.json`은 `application/json`) 코드 보기 같은 HTML 화면의 원문이 그대로 보인다.
     * v1.6의 `Html` 결과는 주소와 무관하게 `text/html`이다. HTML이 아닌 응답은 모두 `ResponseEntity`로 만들어 영향이 없다.
     */
    @Bean
    fun forceHtmlViews(): org.springframework.beans.factory.config.BeanPostProcessor = object : org.springframework.beans.factory.config.BeanPostProcessor {
        override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
            if (bean is org.thymeleaf.spring6.view.ThymeleafViewResolver) bean.forceContentType = true
            return bean
        }
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(preferredLanguageInterceptor)
        registry.addInterceptor(siteSecretInterceptor)
        // 접근 검사 어노테이션은 v1.6의 Play 순서(메서드가 바깥, 클래스가 안쪽)로 한 인터셉터에서 실행한다
        registry.addInterceptor(accessChainInterceptor)
        // v1.6의 기본 헤더(Cache-Control)는 액션이 실행될 때만 붙는다. 접근 검사가 막은 응답(로그인 이동, 403)에는 붙지 않으므로 검사 뒤에 둔다.
        // 정적 자원(/assets, 파비콘)은 자원 처리기가 자기 캐시 규칙을 정한다.
        registry.addInterceptor(defaultHeadersInterceptor).excludePathPatterns("/assets/**", "/favicon.ico")
    }
}
