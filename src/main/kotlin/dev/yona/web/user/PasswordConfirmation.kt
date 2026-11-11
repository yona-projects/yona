package dev.yona.web.user

import jakarta.servlet.http.HttpServletRequest

/**
 * 비밀번호 확인 입력(`retypedPassword`)이 비밀번호와 같은지 서버에서 확인한다.
 *
 * v1.6은 가입(`POST /users/signup`), 비밀번호 재설정(`POST /resetPassword`), 비밀번호 변경(`POST /user/resetPassword`)에서
 * 확인 입력을 화면 스크립트로만 검사하고 서버는 보지 않았다. 그래서 스크립트를 거치지 않는 요청은 서로 다른 두 값으로도 가입하고
 * 비밀번호를 바꿀 수 있었다(usersec-F2). 지금은 서버도 검사한다.
 *
 * 확인 입력을 아예 보내지 않는 요청(API나 도구)은 막지 않는다. 확인 입력이 있는데 다를 때만 거절한다.
 */
internal object PasswordConfirmation {

    /** 거절할 때 보이는 메시지 키이다. 사이트 비밀 키 설정 화면과 같은 문구를 쓴다. */
    const val MISMATCH_KEY = "user.confirmPassword.alert"

    /**
     * 확인 입력이 있고 비밀번호와 다르면 `true`이다.
     *
     * @param request 비밀번호는 `password`, 확인 입력은 `retypedPassword` 매개변수이다
     */
    fun mismatched(request: HttpServletRequest): Boolean {
        val retyped = request.getParameter("retypedPassword") ?: return false
        return retyped != request.getParameter("password")
    }
}
