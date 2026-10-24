package dev.yona.web.user

import dev.yona.web.issue.IssueListItem

/**
 * 사용자 페이지(`GET /:user`)의 이슈 탭 한 줄이다. v1.6 `app/views/user/partial_issues.scala.html`이 템플릿 안에서 구하던 값을 미리 담는다.
 *
 * @property item 이슈 목록과 같은 방식으로 만든 값(제목, 댓글과 추천 수, 하위 이슈 진행, 라벨, 하위 이슈 목록 등)
 * @property open 열린 이슈이면 `true`, 닫혔으면 `false`이다
 * @property authorPureName 작성자의 이름에서 부서를 뺀 이름. 작성자의 이름이 비어 있으면 `null`이다
 * @property assigneePureName 담당자의 이름에서 부서를 뺀 이름. 담당자가 없으면 `null`이다
 * @property milestoneVisible 프로젝트가 마일스톤 메뉴를 쓰고 이슈에 마일스톤이 있으면 `true`이다
 * @property timeTitle 시각에 마우스를 올렸을 때 보이는 문구이다. 갱신된 적이 있으면 `Last Updated`, 아니면 `Created at`으로 시작한다
 * @property timeText 시각의 "얼마 전" 문구이다
 * @property dueDateString 마감일을 `yyyy-MM-dd`로 쓴 문자열이다. 마감일이 없으면 `null`이다
 */
data class UserIssueRow(
    val item: IssueListItem, val owner: String, val project: String, val open: Boolean,
    val authorPureName: String?, val assigneePureName: String?, val milestoneVisible: Boolean,
    val timeTitle: String, val timeText: String, val dueDateString: String?
) {
    /** 마감이 지난 열린 이슈이면 `true`이다. */
    val overdue: Boolean get() = item.due?.cssClass == "overdue"
}

/**
 * 사용자 페이지의 풀 리퀘스트 탭 한 줄이다. v1.6 `app/views/user/partial_pullRequests.scala.html`이다.
 *
 * @property stateKey 상태 문구의 메시지 키이다. 충돌이면 `pullRequest.state.conflict`이다
 * @property stateClass 상태 칸의 CSS 클래스이다. 충돌이면 `conflict`, 아니면 소문자 상태이다
 * @property contributorName 보낸 사람의 표시 이름. 이름이 비어 있으면 `null`이다
 */
data class UserPullRequestRow(
    val projectOwner: String, val projectName: String, val projectLogoUrl: String,
    val number: Long, val title: String, val conflict: Boolean, val stateKey: String, val stateClass: String,
    val contributorLoginId: String, val contributorName: String?,
    val createdTitle: String, val createdText: String, val threadCount: Long,
    val receiverLoginId: String?, val receiverName: String?, val receiverAvatarUrl: String?
)

/**
 * 사용자 페이지의 프로젝트 탭 한 줄이다. v1.6 `app/views/user/partial_projectlist.scala.html`이다.
 *
 * @property originalOwner 원본 프로젝트의 소유자. 포크가 아니면 `null`이다
 * @property memberCount 구성원 수(사이트 관리자 역할 제외)
 * @property showWatchButton 보는 사람이 소유자가 아니면 `true`이다
 * @property watching 보는 사람이 구독 중이면 `true`이다
 * @property watchingCount 구독자 수
 * @property canLeave 보는 사람이 이 사용자 본인이고 소유자가 아니면 `true`이다
 */
data class UserProjectRow(
    val owner: String, val name: String, val logoUrl: String, val private: Boolean, val publicScope: Boolean,
    val originalOwner: String?, val originalName: String?, val overview: String?,
    val memberCount: Int, val createdTitle: String, val createdText: String,
    val lastPushedTitle: String?, val lastPushedText: String?,
    val showWatchButton: Boolean, val watching: Boolean, val watchingCount: Long, val canLeave: Boolean
)

/**
 * 사용자 페이지 프로필 상자의 값이다.
 *
 * @property since 가입일(`MMM dd, yyyy`, 영어)
 * @property providers 연결된 소셜 로그인의 로고 HTML이다. 자격 증명마다 제공자 순서대로 담는다
 */
data class UserProfileView(
    val loginId: String, val name: String, val englishName: String?, val email: String?, val avatarUrl: String,
    val guest: Boolean, val siteManager: Boolean, val locked: Boolean, val since: String, val providers: List<String>,
    val ownPage: Boolean
)

/** 사용자 페이지 활동 탭에 담을 값이다. 숨겨야 하면 모두 비어 있다. */
data class UserActivity(
    val issues: List<UserIssueRow>, val pullRequests: List<UserPullRequestRow>, val projects: List<UserProjectRow>
)

