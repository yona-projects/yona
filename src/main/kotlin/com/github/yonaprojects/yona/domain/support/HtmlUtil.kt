package com.github.yonaprojects.yona.domain.support

import org.owasp.html.PolicyFactory
import org.owasp.html.Sanitizers

// yona utils/HtmlUtil.java 대응. 사용자 표시 이름처럼 화면에 HTML로 다시 그려지는 문자열을 저장 전에 정화한다.
// v1.6 defaultSanitize는 Sanitizers.FORMATTING(서식 태그만 허용, 스크립트·이벤트 핸들러·임의 요소 제거)이었다.
object HtmlUtil {
    private val DEFAULT_POLICY: PolicyFactory = Sanitizers.FORMATTING

    fun defaultSanitize(text: String): String = DEFAULT_POLICY.sanitize(text)
}
