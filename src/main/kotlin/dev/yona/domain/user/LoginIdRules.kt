package dev.yona.domain.user

/**
 * 로그인 ID와 조직 이름의 형식 및 예약어 규칙이다. v1.6 `User.LOGIN_ID_PATTERN`과 `utils.ReservedWordsValidator`이다.
 *
 * 형식: 영문, 숫자, 한글, `-`로 시작하고, `_` 또는 `.`을 한 글자씩 사이에 두고 이어 쓸 수 있다(연달아 쓸 수는 없다). 한글 아이디를 공식으로 허용한다(usersec-F3).
 * 예약어: 주소의 첫 구간으로 이미 쓰이는 이름이다. 이 이름은 사용자나 조직, 프로젝트 소유자가 될 수 없다.
 */
object LoginIdRules {

    /**
     * v1.6 `User.LOGIN_ID_PATTERN`은 `[a-zA-Z0-9가-힣-]+([_.][a-z_.A-Z0-9가-힣-]+)*`로 구분 기호 뒤에도 `_`와 `.`를 받아 `a..b` 같은 이름이 서버에서만 통과했다.
     * 만들기와 설정 화면의 규칙(`^[a-zA-Z0-9-가-힣]+([_.][a-zA-Z0-9-가-힣]+)*$`)에 맞춰 구분 기호를 연달아 쓰지 못하게 했다(organization-F5).
     * 이미 만들어진 이름은 그대로 두고, 새로 만들 때 검사에만 적용한다.
     */
    const val LOGIN_ID_PATTERN = "[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*"

    private val FORMAT = Regex("^$LOGIN_ID_PATTERN$")

    /**
     * 예약어이다. v1.6은 기동할 때 라우트 표의 각 경로 첫 구간을 모아 만들고 `new`, `projects`, `orgs`, `organizations`를 더했다.
     * v2는 v1.6 라우트에서 뽑은 목록을 고정해서 쓴다(화면 주소가 v1.6과 같아야 하므로 같은 이름이 막혀야 한다). 엔터프라이즈 SSO의 `/sso/` 아래(X-16, D-80)를 위해 `sso`를 더했다.
     */
    val RESERVED_WORDS: Set<String> = setOf(
        "-_-api", "assets", "authenticate", "categories", "comments", "favicon.ico", "files", "info", "labels", "logout",
        "lostPassword", "markdown", "messages.js", "migration", "new", "noti", "notification", "notifications", "organizations",
        "orgs", "project", "projectform", "projects", "resetPassword", "restricted", "search", "sites", "sso", "svn", "threads",
        "unwatch", "user", "users", "verify", "watch"
    )

    /** [value]가 로그인 ID 형식을 지키면 `true`이다. */
    fun isValidFormat(value: String): Boolean = FORMAT.matches(value)

    /** [value]가 예약어이면(대소문자 무시) `true`이다. */
    fun isReserved(value: String?): Boolean = RESERVED_WORDS.any { it.equals(value, ignoreCase = true) }
}
