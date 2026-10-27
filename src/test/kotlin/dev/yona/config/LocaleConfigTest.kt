package dev.yona.config

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.util.Locale
import kotlin.test.assertEquals

/**
 * 지원 언어 목록을 `application.langs`(v2 설정 `yona.langs`)에서 읽는지 검증한다.
 *
 * 원본: `conf/application.conf.default:95`, Play 2.3의 언어 선택(`Accept-Language`에 가장 알맞은 언어, 없으면 목록의 첫 언어).
 */
class LocaleConfigTest {

    private fun resolver(langs: List<String>) = LocaleConfig(YonaProperties(langs = langs)).localeResolver()

    private fun request(acceptLanguage: String?) = MockHttpServletRequest().apply { acceptLanguage?.let { addHeader("Accept-Language", it) } }

    @Test
    fun `기본 목록은 v1_6 기본값이고 헤더가 없으면 첫 언어인 영어이다`() {
        assertEquals(listOf("en-US", "ko-KR", "ja-JP", "ru-RU", "uz-UZ"), YonaProperties().langs)
        assertEquals(Locale.forLanguageTag("en-US"), resolver(YonaProperties().langs).resolveLocale(request(null)))
    }

    @Test
    fun `목록을 한 언어로 줄이면 헤더가 없어도 그 언어이다 (v1_6 PasswordResetAppTest가 ko-KR 한 개로 줄여 한국어를 기대한다)`() {
        assertEquals(Locale.forLanguageTag("ko-KR"), resolver(listOf("ko-KR")).resolveLocale(request(null)))
        assertEquals(Locale.forLanguageTag("ko-KR"), resolver(listOf("ko-KR")).resolveLocale(request("ja-JP")))
    }

    @Test
    fun `헤더에 가장 알맞은 지원 언어를 고른다`() {
        val r = resolver(listOf("en-US", "ko-KR", "ja-JP"))
        assertEquals(Locale.forLanguageTag("ja-JP"), r.resolveLocale(request("ja-JP,en;q=0.5")))
        assertEquals(Locale.forLanguageTag("en-US"), r.resolveLocale(request("fr-FR")))
    }

    @Test
    fun `공백이 섞인 쉼표 목록도 읽는다 (v1_6 설정 형식)`() {
        val locales = SupportedLocales.parse("en-US, ko-KR , ja-JP")
        assertEquals(listOf("en-US", "ko-KR", "ja-JP"), locales.map { it.toLanguageTag() })
    }
}
