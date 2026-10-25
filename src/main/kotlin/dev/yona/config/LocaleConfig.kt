package dev.yona.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

/**
 * 요청의 언어를 정하는 설정이다.
 *
 * v1.6(Play 2.3)은 `application.langs`에 적은 언어 중에서 `Accept-Language` 헤더에 가장 알맞은 것을 고르고,
 * 맞는 것이 없으면 목록의 첫 번째 언어(`en-US`)를 쓴다. 같은 규칙을 Spring의 [AcceptHeaderLocaleResolver]로 구현한다.
 *
 * Play의 언어 선택 쿠키(`PLAY_LANG`)는 읽지 않는다. v1.6 앱 코드는 `changeLang`을 부르지 않아 이 쿠키를 만들지 않으므로 화면에서 닿는 차이가 없다.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

    @Bean
    fun localeResolver(): LocaleResolver = AcceptHeaderLocaleResolver().apply {
        supportedLocales = SUPPORTED_LOCALES
        setDefaultLocale(SUPPORTED_LOCALES.first())
    }

    companion object {
        /** v1.6 `application.langs`의 기본값 `en-US, ko-KR, ja-JP, ru-RU, uz-UZ`이다. */
        val SUPPORTED_LOCALES: List<Locale> = listOf(
            Locale.forLanguageTag("en-US"), Locale.forLanguageTag("ko-KR"), Locale.forLanguageTag("ja-JP"),
            Locale.forLanguageTag("ru-RU"), Locale.forLanguageTag("uz-UZ")
        )
    }
}
