package dev.yona.domain.pullrequest

import dev.yona.domain.LONG_TEXT
import dev.yona.domain.enumeration.ReviewVerdict
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Date

/**
 * 풀 리퀘스트 리뷰 판정 한 건이다(추가 개발 항목 X-12, `decisions.md` D-35). 제출한 리뷰는 모두 이력으로 쌓고, 정책에는 리뷰어별 최신 유효 판정만 쓴다.
 * 이름과 컬럼은 v1.6 풀 리퀘스트 계열 테이블의 규칙을 따른다(소문자 스네이크, `pull_request_` 접두사, 사용자 `author_id`와 `author_login_id` 쌍).
 *
 * @property pullRequest 리뷰한 풀 리퀘스트
 * @property authorId 리뷰어 사용자 번호
 * @property authorLoginId 리뷰어 로그인 ID 사본이다. 사용자가 삭제 처리되어도 이력에 이름이 남는다
 * @property verdict 판정
 * @property contents 요약 본문(마크다운). Comment와 Request changes는 필수, Approve는 비어 있을 수 있다
 * @property createdDate 제출 시각
 * @property dismissedDate 해제한 시각. 해제되지 않았으면 `null`이다. 해제된 리뷰는 Comment로 취급한다
 * @property dismissedByLoginId 해제한 사람의 로그인 ID. 시스템이 해제했으면(새 커밋) `null`이다
 * @property dismissedReason 해제 사유
 */
@Entity
@Table(
    name = "pull_request_review",
    indexes = [
        Index(name = "ix_pull_request_review_pullRequest", columnList = "pull_request_id"),
        Index(name = "ix_pull_request_review_author", columnList = "author_id")
    ]
)
class PullRequestReview(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "pull_request_id")
    var pullRequest: PullRequest? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(length = 17)
    @Enumerated(EnumType.STRING)
    var verdict: ReviewVerdict? = null,
    @Column(length = LONG_TEXT)
    var contents: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "dismissed_date")
    var dismissedDate: Date? = null,
    @Column(name = "dismissed_by_login_id")
    var dismissedByLoginId: String? = null,
    @Column(name = "dismissed_reason", length = LONG_TEXT)
    var dismissedReason: String? = null
)

/** [PullRequestReview] 저장소이다. */
interface PullRequestReviewRepository : JpaRepository<PullRequestReview, Long> {

    /** 풀 리퀘스트의 리뷰를 제출 순서로 읽는다. */
    fun findByPullRequestIdOrderByCreatedDateAscIdAsc(pullRequestId: Long): List<PullRequestReview>
}
