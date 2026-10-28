package dev.yona.config

import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import java.util.Locale

/**
 * 문구 조회를 지원 언어 목록(`yona.langs`)에 든 언어로 한정한다.
 *
 * Play 2.3은 `application.langs`에 적은 언어의 메시지 파일만 읽는다. 서버 JVM의 기본 로캘(`Lang.defaultLang()`)이나 사용자 설정 언어가 그 목록에 없으면
 * 그 언어의 문구가 아니라 기본(영어) 문구가 나온다. 스프링은 번들이 있는 모든 언어를 쓰므로 조회하기 전에 로캘을 목록의 것으로 바꾸고, 없으면 [Locale.ROOT]로 바꾼다.
 * 요청의 언어는 [LocaleConfig]가 이미 목록에서 고르므로 영향이 없다.
 *
 * @property supported 지원 언어 목록. 설정이 늦게 묶이는 경우를 위해 조회할 때마다 읽는다
 */
class RestrictedMessageSource(private val delegate: MessageSource, private val supported: () -> List<Locale>) : MessageSource {

    /** 목록에서 같은 언어와 나라, 아니면 나라 없이 언어만 적은 항목을 찾는다. 없으면 [Locale.ROOT]이다. */
    fun effective(locale: Locale?): Locale {
        if (locale == null) return Locale.ROOT
        val list = supported()
        list.firstOrNull { it.language == locale.language && it.country == locale.country }?.let { return it }
        list.firstOrNull { it.country.isEmpty() && it.language == locale.language }?.let { return it }
        return Locale.ROOT
    }

    override fun getMessage(code: String, args: Array<out Any>?, defaultMessage: String?, locale: Locale?): String? =
        delegate.getMessage(code, args, defaultMessage, effective(locale))

    override fun getMessage(code: String, args: Array<out Any>?, locale: Locale?): String = delegate.getMessage(code, args, effective(locale))

    override fun getMessage(resolvable: MessageSourceResolvable, locale: Locale?): String = delegate.getMessage(resolvable, effective(locale))
}
