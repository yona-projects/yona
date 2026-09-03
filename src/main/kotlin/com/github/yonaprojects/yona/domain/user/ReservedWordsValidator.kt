package com.github.yonaprojects.yona.domain.user

/**
 * yona의 utils/ReservedWordsValidator.java 대응 (P2-01).
 * yona는 Play 라우트 테이블을 런타임에 스캔해 `/{loginId}` 패턴과 충돌하는 정적 최상위 경로를
 * 자동 수집하지만, yona는 Spring MVC 라우팅 방식이 달라 등가의 런타임 스캔 대신 실제 컨트롤러의
 * 정적 최상위 경로를 정적으로 나열한다(신규 최상위 정적 경로 추가 시 이 목록도 함께 갱신 필요).
 * User.loginId, Organization.name 검증에 사용한다(yona와 동일하게 Project.name에는 적용하지 않음
 * — 프로젝트는 `/{owner}/{projectName}`처럼 두 번째 경로 세그먼트라 충돌 위험이 다르다).
 */
object ReservedWordsValidator {
    val RESERVED_WORDS: Set<String> = setOf(
        "new", "projects", "orgs", "organizations", "api", "site", "users", "user",
        "login", "logout", "signup", "search", "svn", "git", "git-lfs", "files",
        "images", "javascripts", "stylesheets", "help", "membership", "members",
        "settings", "notifications", "messages", "watch", "unwatch", "labels",
        "milestones", "issues", "pulls", "pull", "post", "posts", "board",
        "project", "org", "data", "export", "import", "migration",
        "bootstrap-setup", "diagnostic"
    )

    fun isReserved(word: String): Boolean {
        return RESERVED_WORDS.any { it.equals(word, ignoreCase = true) }
    }
}
