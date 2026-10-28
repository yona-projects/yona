package dev.yona.config

import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import java.util.Locale
import kotlin.test.assertEquals

/**
 * 문구 조회가 지원 언어 목록(`yona.langs`)에 든 언어만 쓰는지 검증한다.
 *
 * 원본: Play 2.3은 `application.langs`에 적은 언어의 메시지 파일만 읽는다. 서버 JVM의 기본 로캘이 목록에 없으면(예: 한국어 서버에서 `ja-JP,en-US`만 지원)
 * 그 언어의 문구가 아니라 기본(영어) 문구를 쓴다. 공식 v1.16.0에서 확인했다.
 */
class RestrictedMessageSourceTest {

    private val bundle = ResourceBundleMessageSource().apply {
        setBasenames("messages")
        setDefaultEncoding("UTF-8")
        setFallbackToSystemLocale(false)
    }

    private fun source(vararg langs: String) = RestrictedMessageSource(bundle) { SupportedLocales.of(langs.toList()) }

    private fun message(source: RestrictedMessageSource, locale: Locale) = source.getMessage("notification.issue.closed", null, locale)

    @Test
    fun `목록에 있는 언어는 그 언어의 문구이다`() {
        val s = source("ja-JP", "en-US")
        assertEquals("イシューの状態がクローズになりました", message(s, Locale.forLanguageTag("ja-JP")))
        assertEquals("Issue has been closed", message(s, Locale.forLanguageTag("en-US")))
    }

    @Test
    fun `목록에 없는 언어는 기본 영어 문구이다`() {
        val s = source("ja-JP", "en-US")
        assertEquals("Issue has been closed", message(s, Locale.forLanguageTag("ko-KR")))
        assertEquals("Issue has been closed", message(s, Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `나라가 다르면 목록에 없는 언어이다`() {
        // Play는 `ja-JP`와 `ja-XX`를 다른 코드로 본다. 언어만 적은 항목(`ja`)은 `messages.ja` 파일이 있어야 쓰이는데, 이 저장소에는 그 파일이 없다
        assertEquals("Issue has been closed", message(source("ja-JP"), Locale.forLanguageTag("ja-XX")))
        assertEquals("Issue has been closed", message(source("ja"), Locale.forLanguageTag("ja-XX")))
    }

    @Test
    fun `기본 목록에서는 다섯 언어가 모두 그대로이다`() {
        val s = source("en-US", "ko-KR", "ja-JP", "ru-RU", "uz-UZ")
        assertEquals("이슈 닫힘", message(s, Locale.forLanguageTag("ko-KR")))
        assertEquals("Выпуск был закрыт", message(s, Locale.forLanguageTag("ru-RU")))
    }
}
