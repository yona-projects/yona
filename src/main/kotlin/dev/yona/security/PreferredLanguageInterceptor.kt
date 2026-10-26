package dev.yona.security

import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 로그인한 사용자의 선호 언어(`user.lang`)를 요청마다 현재 요청의 언어에 맞춘다.
 *
 * v1.6은 `Global.getDefaultAction`이 모든 요청에서 `UserApp.updatePreferredLanguage()`를 불러,
 * 익명 사용자는 건너뛰고, 요청에 `Accept-Language` 헤더(또는 언어 쿠키)가 있을 때 요청의 언어 코드가
 * `user.lang`과 다르면 `user.lang`을 새 값으로 저장한다. 이 값은 알림 메일의 언어, 사용자 표시 이름의 영문 이름 선택,
 * `<html lang>`, 메일 수신 처리 응답의 언어에 쓰인다.
 *
 * Play의 언어 쿠키(`PLAY_LANG`)는 v2가 읽지 않으므로([dev.yona.config.LocaleConfig]) 헤더만 확인한다.
 * 저장에 실패해도 요청은 계속 처리하고 경고만 남긴다 (v1.6은 예외를 삼키고 경고를 기록한다).
 */
@Component
class PreferredLanguageInterceptor(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.getHeader("Accept-Language").isNullOrBlank()) return true
        val user = currentUserProvider.get().user ?: return true
        val code = LocaleContextHolder.getLocale().toLanguageTag()
        if (code == user.lang) return true
        try {
            user.lang = code.take(MAX_LENGTH)
            users.save(user)
        } catch (e: RuntimeException) {
            log.warn("Failed to update the preferred language of user {}", user.loginId, e)
        }
        return true
    }

    private companion object {
        const val MAX_LENGTH = 255
        val log = LoggerFactory.getLogger(PreferredLanguageInterceptor::class.java)
    }
}
