package dev.yona.domain.pullrequest

import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 풀 리퀘스트 목록의 분류이다. v1.6 `PullRequestApp.Category`.
 *
 * @property code 화면에서 쓰는 이름(`open`, `closed`, `sent`, `accepted`)
 * @property projectField 프로젝트 조건이 걸리는 필드. 받은 요청은 `toProject`, 보낸 요청은 `fromProject`
 * @property order 내림차순으로 정렬하는 필드
 * @property states 이 분류에 속하는 상태. 비어 있으면 상태를 가리지 않는다
 */
enum class PullRequestCategory(val code: String, val projectField: String, val order: String, vararg val states: State) {
    OPEN("open", "toProject", "number", State.OPEN),
    CLOSED("closed", "toProject", "received", State.CLOSED, State.MERGED),
    SENT("sent", "fromProject", "created"),
    ACCEPTED("accepted", "fromProject", "created", State.MERGED)
}

/**
 * 풀 리퀘스트 검색 조건이다. v1.6 `PullRequestApp.SearchCondition`.
 *
 * @property filter 제목, 본문, 병합 커밋, 리뷰 댓글, 현재 커밋의 메시지와 해시에서 찾는 글
 * @property contributorId 보낸 사람. 보낸 요청(`SENT`)에서는 화면이 쓰지 않는다
 * @property pageNum 1부터 시작하는 쪽 번호
 * @property project 프로젝트를 지정하면 분류의 프로젝트 필드가 이 프로젝트인 요청만 찾는다. 조직 목록은 `null`이다
 * @property organizationProjectIds 조직 목록이 쓴다. 받는 쪽과 보내는 쪽 프로젝트가 모두 이 목록에 있어야 한다 (v1.6 `condition.organization`)
 * @property assigneeId 담당자. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34)
 * @property labelIds 고른 라벨 번호. 모든 라벨을 가진 요청만 찾는다(이슈 목록과 같다, D-34)
 */
data class PullRequestSearchCondition(
    val project: Project?,
    val category: PullRequestCategory,
    val filter: String? = null,
    val contributorId: Long? = null,
    val pageNum: Int = 1,
    val organizationProjectIds: List<Long>? = null,
    val assigneeId: Long? = null,
    val labelIds: Set<Long> = emptySet()
) {
    fun withCategory(category: PullRequestCategory) = copy(category = category)
}

/** 검색 결과 한 쪽이다. */
data class PullRequestPage(val items: List<PullRequest>, val totalPages: Int, val totalCount: Long)

/**
 * 풀 리퀘스트를 검색한다. v1.6 `PullRequest.findPagingList`, `count`, `createSearchExpressionList`
 * (`app/models/PullRequest.java:752-818`)에 해당한다.
 *
 * 분류의 프로젝트 조건과 상태 조건, 보낸 사람 조건을 모두 만족하고, 검색어가 있으면 제목, 본문, 병합 커밋 해시, 리뷰 댓글 내용,
 * 현재 커밋의 메시지나 해시 가운데 하나에 대소문자 구별 없이 들어 있어야 한다. 정렬은 분류의 기준으로 내림차순이며 값이 없는 것은 뒤로 간다
 * (v1.6이 실행되던 MariaDB의 동작이며 다른 DB에서도 같게 맞춘다).
 */
@Service
class PullRequestSearchService(private val pullRequests: PullRequestRepository) {

    /** 한 쪽에 보여 줄 풀 리퀘스트 수이다 (v1.6 `PullRequest.ITEMS_PER_PAGE`). */
    val itemsPerPage: Int = 15

    @Transactional(readOnly = true)
    fun search(condition: PullRequestSearchCondition): PullRequestPage {
        val sort = Sort.by(Sort.Order.desc(condition.category.order).nullsLast(), Sort.Order.desc("id"))
        val page: Page<PullRequest> = pullRequests.findAll(specification(condition), PageRequest.of((condition.pageNum - 1).coerceAtLeast(0), itemsPerPage, sort))
        return PullRequestPage(page.content, page.totalPages, page.totalElements)
    }

    /** 조건에 맞는 풀 리퀘스트 수이다. 쪽 번호는 무시한다. */
    @Transactional(readOnly = true)
    fun count(condition: PullRequestSearchCondition): Long = pullRequests.count(specification(condition))

    private fun specification(condition: PullRequestSearchCondition) = Specification<PullRequest> { root, query, cb ->
        val predicates = mutableListOf<Predicate>()
        condition.project?.let { predicates += cb.equal(root.get<Project>(condition.category.projectField).get<Long>("id"), it.id) }
        condition.organizationProjectIds?.let { ids ->
            if (ids.isEmpty()) predicates += cb.disjunction()
            else {
                predicates += root.get<Project>("toProject").get<Long>("id").`in`(ids)
                predicates += root.get<Project>("fromProject").get<Long>("id").`in`(ids)
            }
        }
        if (condition.category.states.isNotEmpty()) predicates += root.get<State>("state").`in`(condition.category.states.toList())
        condition.contributorId?.let { predicates += cb.equal(root.get<Any>("contributor").get<Long>("id"), it) }
        condition.assigneeId?.let { assignee ->
            val sub = query!!.subquery(Long::class.java)
            val pr = sub.correlate(root)
            sub.select(cb.literal(1L)).where(cb.equal(pr.join<PullRequest, dev.yona.domain.user.User>("assignees").get<Long>("id"), assignee))
            predicates += cb.exists(sub)
        }
        condition.labelIds.forEach { label ->
            val sub = query!!.subquery(Long::class.java)
            val pr = sub.correlate(root)
            sub.select(cb.literal(1L)).where(cb.equal(pr.join<PullRequest, dev.yona.domain.issue.IssueLabel>("labels").get<Long>("id"), label))
            predicates += cb.exists(sub)
        }

        val filter = condition.filter
        if (!filter.isNullOrBlank()) {
            val pattern = "%" + filter.lowercase() + "%"
            fun likeIgnoreCase(path: jakarta.persistence.criteria.Expression<String>) = cb.like(cb.lower(path.`as`(String::class.java)), pattern, '\\')

            val comments = query!!.subquery(Long::class.java).let { sub ->
                val rc = sub.from(ReviewComment::class.java)
                sub.select(cb.literal(1L)).where(
                    cb.equal(rc.get<Any>("thread").get<Any>("pullRequest"), root), likeIgnoreCase(rc.get("contents"))
                )
                cb.exists(sub)
            }
            val commits = query.subquery(Long::class.java).let { sub ->
                val c = sub.from(PullRequestCommit::class.java)
                sub.select(cb.literal(1L)).where(
                    cb.equal(c.get<Any>("pullRequest"), root), cb.equal(c.get<PullRequestCommitState>("state"), PullRequestCommitState.CURRENT),
                    cb.or(likeIgnoreCase(c.get("commitMessage")), likeIgnoreCase(c.get("commitId")))
                )
                cb.exists(sub)
            }
            predicates += cb.or(likeIgnoreCase(root.get("title")), likeIgnoreCase(root.get("body")), likeIgnoreCase(root.get("mergedCommitIdTo")), comments, commits)
        }
        cb.and(*predicates.toTypedArray())
    }

}
