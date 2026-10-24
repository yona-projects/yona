package dev.yona.web.search

import dev.yona.domain.search.SearchCounts
import dev.yona.domain.search.SearchType

/**
 * 발췌 한 조각이다.
 *
 * @property truncated 본문 전체보다 짧으면 `true`이다. 화면에 `.....`를 붙인다
 */
data class SearchSnippet(val text: String, val truncated: Boolean)

/**
 * 이슈, 게시글, 댓글, 코드 리뷰 댓글 결과 한 줄이다. v1.6 `app/views/search/partial_issues`, `partial_posts`,
 * `partial_issue_comments`, `partial_post_comments`, `partial_reviews`가 템플릿 안에서 구하던 값이다.
 *
 * @property postId 제목 줄의 `#번호`. 없으면 제목 줄이 없다
 * @property titleUrl 제목 링크. 없으면 제목 줄에 링크가 없다
 * @property titleClass 제목 링크에 `title` 클래스를 붙일지
 * @property snippetsUrl 발췌 전체를 감쌀 링크이다(커밋의 코드 리뷰 댓글)
 * @property projectLabel `소유자/이름`. 프로젝트 범위에서는 `null`이다
 * @property authorName 작성자의 표시 이름. 이름이 없으면 `null`이다
 * @property noAuthorText 작성자가 없을 때 보이는 문구이다
 */
data class SearchItemRow(
    val postId: String?, val titleUrl: String?, val titleText: String?, val titleClass: Boolean,
    val snippets: List<SearchSnippet>, val snippetsUrl: String?,
    val projectLabel: String?, val projectUrl: String?,
    val authorLoginId: String?, val authorName: String?, val noAuthorText: String,
    val createdTitle: String, val createdText: String
)

/** 마일스톤 결과 한 줄이다. [dueDateString]이 없으면 마감 정보가 없다. */
data class SearchMilestoneRow(
    val url: String, val title: String, val snippets: List<SearchSnippet>, val projectLabel: String?, val projectUrl: String?,
    val dueDateString: String?, val until: String?
)

/** 사용자 결과 한 줄이다. [since]는 가입일(`MMM dd, yyyy`)이다. */
data class SearchUserRow(val loginId: String, val name: String, val avatarUrl: String, val since: String)

/** 프로젝트 결과 한 줄이다. */
data class SearchProjectRow(
    val owner: String, val name: String, val logoUrl: String, val originalOwner: String?, val originalName: String?, val overview: String?,
    val createdTitle: String, val createdText: String, val lastPushedTitle: String?, val lastPushedText: String?
)

/**
 * 검색 결과 화면의 모델이다.
 *
 * @property searchType 확정된 종류
 * @property rows 종류에 맞는 행 목록이다
 * @property action 검색 폼이 보낼 주소(범위마다 다르다)
 * @property projectScoped 프로젝트 범위이면 `true`이다
 */
data class SearchView(
    val keyword: String, val searchType: SearchType, val counts: SearchCounts, val rows: List<Any>, val totalPageCount: Int,
    val action: String, val projectScoped: Boolean
) {
    /** 확정된 종류의 결과 수이다. */
    val currentCount: Long get() = counts.of(searchType)
}
