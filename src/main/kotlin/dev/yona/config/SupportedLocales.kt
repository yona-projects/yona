package dev.yona.config

import java.util.Locale

/**
 * 설정 `yona.langs`(v1.6 `application.langs`)가 정한 지원 언어 목록이다.
 *
 * v1.6(Play 2.3)은 이 목록에서 `Accept-Language`에 가장 알맞은 언어를 고르고, 맞는 것이 없으면 첫 번째 언어를 쓴다.
 * 요청 밖(알림 메일 문구처럼 현재 요청의 언어가 없는 곳)에서도 첫 번째 언어가 기본이다.
 */
object SupportedLocales {

    /** 쉼표로 이은 문자열(공백 허용)을 로케일 목록으로 바꾼다. 비면 빈 목록이다. */
    fun parse(text: String): List<Locale> =
        text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { Locale.forLanguageTag(it) }

    /** 설정 값 목록에서 로케일 목록을 만든다. 비면 영어 하나로 본다. */
    fun of(langs: List<String>): List<Locale> =
        langs.flatMap { parse(it) }.ifEmpty { listOf(Locale.forLanguageTag("en-US")) }
}
