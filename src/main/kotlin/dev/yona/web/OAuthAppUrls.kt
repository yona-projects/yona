package dev.yona.web

/**
 * OAuth 앱 화면(목록, 만들기, 수정, 비밀 값 다시 만들기, 지우기)이 가리킬 주소 모음이다. 사용자 설정과 조직 설정이 같은 템플릿 조각(`oauth/partial-apps`)을 쓰므로 소유자마다 다른 주소를 이것으로 넘긴다.
 *
 * @property list 앱 목록 화면
 * @property new 앱 만들기 화면
 * @property create 앱 만들기 양식을 보내는 주소
 * @property editBase 앱 수정 화면 주소의 앞부분(뒤에 `/번호`가 붙는다)
 * @property actionBase 수정, 비밀 값, 지우기 요청 주소의 앞부분
 */
class OAuthAppUrls(val list: String, val new: String, val create: String, private val editBase: String, private val actionBase: String) {

    /** 앱 수정 화면 주소이다. */
    fun edit(id: Long?): String = "$editBase/$id"

    /** 수정 양식을 보내는 주소이다. */
    fun update(id: Long?): String = "$actionBase/$id"

    /** 비밀 값 다시 만들기 주소이다. */
    fun secret(id: Long?): String = "$actionBase/$id/secret"

    /** 앱 소유자 이전 주소이다. */
    fun transfer(id: Long?): String = "$actionBase/$id/transfer"

    /** 앱 지우기 주소이다. */
    fun delete(id: Long?): String = "$actionBase/$id/delete"

    companion object {
        /** 사용자 설정의 앱 화면이다. */
        val USER = OAuthAppUrls("/user/editform/oauth-apps", "/user/editform/oauth-apps/new", "/user/oauth-apps", "/user/editform/oauth-apps", "/user/oauth-apps")

        /** 조직 설정의 앱 화면이다. [base]는 `/organizations/이름/oauth-apps`이다. */
        fun organization(base: String) = OAuthAppUrls(base, "$base/new", base, base, base)
    }
}
