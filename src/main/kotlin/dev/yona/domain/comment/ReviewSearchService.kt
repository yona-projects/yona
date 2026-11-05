package dev.yona.domain.comment

import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.project.Project
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 리뷰 목록의 검색 조건이다. v1.6 `models.support.ReviewSearchCondition`(`app/models/support/ReviewSearchCondition.java`)에 해당한다.
 *
 * @property state 스레드 상태 이름. 대소문자를 가리지 않고 읽는다([threadState])
 * @property authorId 스레드를 만든 사람. 현재 사용자가 아니라 받은 값을 그대로 쓴다
 * @property participantId 이 사용자가 쓴 리뷰 댓글이 달린 스레드만 찾는다
 * @property orderBy 정렬 기준. [ReviewSearchService.ORDERABLE]에 없는 값은 `createdDate`이다
 * @property orderDir `asc` 또는 `desc`(대소문자 무시)
 * @property filter 리뷰 댓글 내용, 커밋 번호, 파일 경로에서 찾을 문자열
 * @property pageNum 1부터 시작하는 쪽 번호
 */
data class ReviewSearchCondition(
    val state: String = ThreadState.OPEN.name,
    val authorId: Long? = null,
    val participantId: Long? = null,
    val orderBy: String = "createdDate",
    val orderDir: String = "desc",
    val filter: String = "",
    val pageNum: Int = 1
) {
    /** [state]를 상태로 읽는다. 모르는 값이면 `null`이다. */
    val threadState: ThreadState? get() = ThreadState.entries.firstOrNull { it.name.equals(state, ignoreCase = true) }

    /** [orderDir]이 `asc`, `desc`(대소문자 무시)이면 `true`이다. */
    val validDirection: Boolean get() = orderDir.equals("asc", ignoreCase = true) || orderDir.equals("desc", ignoreCase = true)
}

/** 한 쪽 분량의 검색 결과이다. */
data class ReviewPage(val items: List<CommentThread>, val pageIndex: Int, val pageSize: Int, val totalRows: Long) {
    /** 전체 쪽 수이다. */
    val totalPageCount: Int get() = if (pageSize <= 0) 0 else Math.ceil(totalRows.toDouble() / pageSize).toInt()
}

/**
 * 프로젝트의 리뷰 스레드를 검색한다. v1.6 `ReviewSearchCondition.asExpressionList(Project)`, `CommentThread.countReviewsBy`에 해당한다.
 *
 * 커밋 번호와 파일 경로는 스레드 종류(코드 범위, 파일 단위)마다 따로 있어 종류별 부분 질의로 찾는다.
 * 정렬 기준이 같은 스레드는 번호 순서를 같은 방향으로 이어 순서를 고정한다.
 */
@Service
class ReviewSearchService(private val entityManager: EntityManager) {

    /** 한 쪽에 보여 줄 스레드 수이다 (v1.6 `ReviewThreadApp.REVIEWS_PER_PAGE`). */
    val itemsPerPage: Int = 15

    /** [project]에서 [condition]에 맞는 스레드 한 쪽을 찾는다. [ReviewSearchCondition.threadState]와 [ReviewSearchCondition.validDirection]이 맞아야 한다. */
    @Transactional(readOnly = true)
    fun search(project: Project, condition: ReviewSearchCondition): ReviewPage {
        val query = Query(project, condition)
        val items = query.typed("select t from CommentThread t ${query.where()} order by ${orderBy(condition)}", CommentThread::class.java)
            .setFirstResult((condition.pageNum - 1).coerceAtLeast(0) * itemsPerPage).setMaxResults(itemsPerPage).resultList
        return ReviewPage(items, condition.pageNum - 1, itemsPerPage, count(project, condition))
    }

    /** 조건에 맞는 모든 스레드를 정렬해 돌려준다. 엑셀 내보내기에 쓴다. */
    @Transactional(readOnly = true)
    fun findAll(project: Project, condition: ReviewSearchCondition): List<CommentThread> {
        val query = Query(project, condition)
        return query.typed("select t from CommentThread t ${query.where()} order by ${orderBy(condition)}", CommentThread::class.java).resultList
    }

    /** 조건에 맞는 스레드 수이다 (v1.6 `CommentThread.countReviewsBy`). */
    @Transactional(readOnly = true)
    fun count(project: Project, condition: ReviewSearchCondition): Long {
        val query = Query(project, condition)
        return query.typed("select count(t) from CommentThread t ${query.where()}", java.lang.Long::class.java).singleResult.toLong()
    }

    private fun orderBy(condition: ReviewSearchCondition): String {
        val direction = if (condition.orderDir.equals("asc", ignoreCase = true)) "asc" else "desc"
        val field = if (condition.orderBy in ORDERABLE) condition.orderBy else "createdDate"
        return "t.$field $direction, t.id $direction"
    }

    /** 질의의 조건절과 매개변수를 모은다. */
    private inner class Query(project: Project, condition: ReviewSearchCondition) {
        private val clauses = mutableListOf("t.project.id = :project")
        private val params = linkedMapOf<String, Any?>("project" to project.id)

        init {
            condition.authorId?.let { clauses += "t.authorId = :author"; params["author"] = it }
            condition.participantId?.let {
                clauses += "exists (select 1 from ReviewComment pc where pc.thread.id = t.id and pc.authorId = :participant)"
                params["participant"] = it
            }
            clauses += "t.state = :state"
            params["state"] = requireNotNull(condition.threadState) { "알 수 없는 상태: ${condition.state}" }
            if (condition.filter.isNotBlank()) {
                clauses += "(exists (select 1 from ReviewComment fc where fc.thread.id = t.id and lower(cast(fc.contents as string)) like :filter escape '\\')" +
                    " or t.id in (select x.id from CodeCommentThread x where lower(x.commitId) like :filter escape '\\' or lower(x.path) like :filter escape '\\')" +
                    " or t.id in (select y.id from NonRangedCodeCommentThread y where lower(y.commitId) like :filter escape '\\' or lower(y.path) like :filter escape '\\'))"
                params["filter"] = dev.yona.util.LikeEscape.contains(condition.filter)
            }
        }

        fun where(): String = "where " + clauses.joinToString(" and ")

        fun <T> typed(jpql: String, type: Class<T>): TypedQuery<T> {
            val typed = entityManager.createQuery(jpql, type)
            params.forEach { (name, value) -> typed.setParameter(name, value) }
            return typed
        }
    }

    /** `like`의 특수 문자(`%`, `_`)와 이스케이프 문자(`!`)를 그대로 찾도록 이스케이프한다. */

    companion object {
        /** 정렬 기준으로 쓸 수 있는 스레드 속성이다. 화면은 `createdDate`만 보낸다. */
        val ORDERABLE = setOf("id", "createdDate", "state")
    }
}
