package dev.yona.web.user

/**
 * 사용자 메뉴의 프로젝트 한 줄이다.
 *
 * @property logoUrl 프로젝트 로고 주소. 로고를 올리지 않았으면 `null`이다(빈 자리 표시를 그린다)
 * @property favored 즐겨찾기한 프로젝트인지
 */
data class MenuProject(
    val id: Long, val owner: String, val name: String, val overview: String?, val priv: Boolean, val logoUrl: String?, val favored: Boolean
)

/**
 * 즐겨찾기 탭의 조직 묶음이다.
 *
 * @property projectCount 조직의 프로젝트 수. 조직 관리자와 사이트 관리자에게만 보이고 아니면 `null`이다
 * @property last 즐겨찾기한 조직 가운데 마지막이다(구분선이 붙는다)
 * @property projects 읽을 수 있는 조직 프로젝트를 이름 순서로 담는다
 */
data class MenuOrganization(val id: Long, val name: String, val projectCount: Int?, val favored: Boolean, val last: Boolean, val projects: List<MenuProject>)

/** 최근 본 이슈나 글 한 줄이다. [label]은 `프로젝트 #번호` 형태의 설명이다. */
data class MenuRecentIssue(val title: String?, val url: String, val label: String)

/** 사용자 메뉴의 세 탭이 쓰는 값이다. */
data class UserMenuModel(
    val loginId: String?,
    val ownProjects: List<MenuProject>,
    val organizations: List<MenuOrganization>,
    val noOrganizations: Boolean,
    val etcFavorites: List<MenuProject>,
    val recentlyVisited: List<MenuProject>,
    val watching: List<MenuProject>,
    val createdByMe: List<MenuProject>,
    val joinMember: List<MenuProject>,
    val recentIssues: List<MenuRecentIssue>
)
