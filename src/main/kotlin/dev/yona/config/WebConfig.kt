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
    private val optionsNotFoundInterceptor: dev.yona.web.OptionsNotFoundInterceptor,
    private val emptyWildcardInterceptor: dev.yona.web.EmptyWildcardInterceptor,
    private val pathBindingInterceptor: dev.yona.web.PathBindingInterceptor,
    private val accessChainInterceptor: AccessChainInterceptor,
    private val siteSecretInterceptor: SiteSecretInterceptor,
    private val preferredLanguageInterceptor: PreferredLanguageInterceptor,
    private val apiScopeInterceptor: dev.yona.security.ApiScopeInterceptor,
    private val defaultHeadersInterceptor: dev.yona.web.DefaultHeadersInterceptor,
    private val flashDisplayInterceptor: dev.yona.web.layout.FlashDisplayInterceptor
) : WebMvcConfigurer {

    /** 현재 시각을 알려 주는 시계이다. 시각에 의존하는 표시를 테스트에서 고정할 수 있게 주입해서 쓴다. */
    @Bean
    fun clock(): java.time.Clock = java.time.Clock.systemDefaultZone()

    /**
     * Thymeleaf 화면의 내용 형식을 늘 `text/html`로 고정한다. 고정하지 않으면 와일드카드 Accept 요청에서 Thymeleaf가 주소의 확장자로 형식을
     * 정해(`.../a.txt`는 `text/plain`, `.json`은 `application/json`) 코드 보기 같은 HTML 화면의 원문이 그대로 보인다.
     * v1.6의 `Html` 결과는 주소와 무관하게 `text/html`이다. HTML이 아닌 응답은 모두 `ResponseEntity`로 만들어 영향이 없다.
     */
    companion object {
        /**
         * Thymeleaf 화면의 내용 형식을 늘 `text/html`로 고정하는 후처리기이다(아래 설명). 후처리기를 만드는 `@Bean` 메서드는 **정적**이어야 한다. 정적이 아니면 이 설정
         * 클래스와 생성자 의존(인터셉터에서 감사 경계, 지표 레지스트리까지)이 후처리기보다 먼저 만들어져, 지표 바인더를 적용하는 후처리기가 레지스트리에 닿기 전에
         * 레지스트리가 만들어진다(X-10 C5d). 그러면 `MeterBinder` 빈이 레지스트리에 붙지 않는다.
         */
        @JvmStatic
        @Bean
        fun forceHtmlViews(): org.springframework.beans.factory.config.BeanPostProcessor = object : org.springframework.beans.factory.config.BeanPostProcessor {
            override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
                if (bean is org.thymeleaf.spring6.view.ThymeleafViewResolver) bean.forceContentType = true
                return bean
            }
        }
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        // Play는 라우트를 가장 먼저 고르므로 라우트가 없는 주소(끝 슬래시만 있는 와일드카드)는 모든 검사보다 먼저 가려낸다
        registry.addInterceptor(optionsNotFoundInterceptor)
        registry.addInterceptor(emptyWildcardInterceptor)
        registry.addInterceptor(pathBindingInterceptor)
        registry.addInterceptor(preferredLanguageInterceptor)
        // 외부 API(`/-_-api`)의 토큰 범위는 접근 검사보다 먼저 거른다(X-5, D-36)
        registry.addInterceptor(apiScopeInterceptor).addPathPatterns("/-_-api", "/-_-api/**")
        // 접근 검사 어노테이션은 v1.6의 Play 순서(메서드가 바깥, 클래스가 안쪽)로 한 인터셉터에서 실행한다
        registry.addInterceptor(accessChainInterceptor)
        // 비밀 키 설정 화면은 접근 검사 뒤에 둔다. Play는 애너테이션 액션을 Global.onRequest 액션보다 바깥에서 실행하므로, 익명이 열 수 없는 주소는 검사가
        // 먼저 막는다(로그인 안내 403)
        registry.addInterceptor(siteSecretInterceptor)
        // v1.6의 기본 헤더(Cache-Control)는 액션이 실행될 때만 붙는다. 접근 검사가 막은 응답(로그인 이동, 403)에는 붙지 않으므로 검사 뒤에 둔다.
        // 정적 자원(/assets/…, 파비콘)은 AssetFilter가 디스패처 앞에서 모두 응답하므로 여기에 오지 않는다. `/assets`(슬래시 없음)는 v1.6에서도 일반 라우트이다.
        registry.addInterceptor(defaultHeadersInterceptor)
        registry.addInterceptor(flashDisplayInterceptor)
    }
}
