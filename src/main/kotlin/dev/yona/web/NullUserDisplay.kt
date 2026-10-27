package dev.yona.web

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component

/**
 * 사용자를 찾지 못했을 때 화면에 쓰는 값이다. v1.6 `User.findByLoginId`는 사용자가 없으면 `NullUser`를 돌려주는데, 그 이름은 메시지
 * `user.notExists.name`이고 로그인 ID와 이메일은 빈 문자열이다. 외부 API로 가져온 글처럼 작성자 행이 없는 글에서 이 값이 목록에 보인다.
 */
@Component
class NullUserDisplay(private val messages: MessageSource) {

    /** 요청 언어의 `user.notExists.name` 문구이다. */
    fun name(): String = messages.getMessage("user.notExists.name", null, "user.notExists.name", LocaleContextHolder.getLocale()).orEmpty()

    companion object {
        /** `NullUser.loginId`이다. 사용자 링크는 `/`가 된다. */
        const val LOGIN_ID = ""
    }
}
