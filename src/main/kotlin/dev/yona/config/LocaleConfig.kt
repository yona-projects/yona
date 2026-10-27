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
 * 맞는 것이 없으면 목록의 첫 번째 언어를 쓴다. 목록은 설정 `yona.langs`이다(기본 `en-US` 첫 번째). 같은 규칙을 Spring의 [AcceptHeaderLocaleResolver]로 구현한다.
 *
 * Play의 언어 선택 쿠키(`PLAY_LANG`)는 읽지 않는다. v1.6 앱 코드는 `changeLang`을 부르지 않아 이 쿠키를 만들지 않으므로 화면에서 닿는 차이가 없다.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfig(private val properties: YonaProperties) {

    /** 지원 언어 목록이다. 설정 `yona.langs`(기본 `en-US, ko-KR, ja-JP, ru-RU, uz-UZ`)에서 읽는다. */
    val supportedLocales: List<Locale> get() = SupportedLocales.of(properties.langs)

    @Bean
    fun localeResolver(): LocaleResolver = AcceptHeaderLocaleResolver().apply {
        supportedLocales = this@LocaleConfig.supportedLocales
        setDefaultLocale(this@LocaleConfig.supportedLocales.first())
    }
}
