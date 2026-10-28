package dev.yona.domain.board

import dev.yona.domain.project.Project
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 게시글 검색 조건이다. v1.6 `BoardApp.SearchCondition`(`app/controllers/BoardApp.java:47-123`)에 해당한다.
 *
 * @property orderBy 정렬 기준. [PostingSearchService.ORDERABLE]에 없는 값은 `createdDate`이다. 기본값 `id`도 `createdDate`로 바꾼다
 * @property orderDir `asc` 또는 `desc`(그 밖은 `desc`)
 * @property filter 제목이나 본문에서 찾을 문자열(대소문자 무시)
 * @property pageNum 0부터 시작하는 쪽 번호
 * @property labelIds 이 중 하나라도 붙은 글만 찾는다. 비면 거르지 않는다
 */
data class PostingSearchCondition(
    val orderBy: String = "id",
    val orderDir: String = "desc",
    val filter: String = "",
    val pageNum: Int = 0,
    val labelIds: Set<Long> = emptySet()
) {
    /** 정렬 기준을 정리한 값이다. */
    val effectiveOrderBy: String get() = if (orderBy == "id") "createdDate" else orderBy
}

/** 한 쪽 분량의 게시글 검색 결과이다 (v1.6 Ebean `Page`). */
data class PostingPage(val items: List<Posting>, val pageIndex: Int, val pageSize: Int, val totalRows: Long) {
    /** 전체 쪽 수이다. */
    val totalPageCount: Int get() = if (pageSize <= 0) 0 else Math.ceil(totalRows.toDouble() / pageSize).toInt()
}

/**
 * 프로젝트의 게시글을 검색한다. v1.6 `BoardApp.SearchCondition.asExpressionList(Project)`와 `Posting.findNotices`를 JPQL로 옮겼다.
 * 목록에는 공지가 아닌 글만 나오고, 공지는 [notices]가 따로 모두 돌려준다.
 */
@Service
class PostingSearchService(private val entityManager: EntityManager) {

    /** [project]의 공지가 아닌 글 한 쪽을 찾는다. */
    @Transactional(readOnly = true)
    fun search(project: Project, condition: PostingSearchCondition, pageSize: Int): PostingPage {
        val where = StringBuilder("where p.project.id = :project and p.notice = false")
        val params = linkedMapOf<String, Any>("project" to requireNotNull(project.id))
        if (condition.filter.isNotEmpty()) {
            where.append(" and (lower(p.title) like :filter escape '\\' or lower(cast(p.body as string)) like :filter escape '\\')")
            params["filter"] = "%" + condition.filter.lowercase() + "%"
        }
        if (condition.labelIds.isNotEmpty()) {
            where.append(" and p.id in (select p2.id from Posting p2 join p2.labels l where l.id in :labelIds)")
            params["labelIds"] = condition.labelIds
        }
        val field = if (condition.effectiveOrderBy in ORDERABLE) condition.effectiveOrderBy else "createdDate"
        val direction = if (condition.orderDir.equals("asc", ignoreCase = true)) "asc" else "desc"

        val total = entityManager.createQuery("select count(p) from Posting p $where", java.lang.Long::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        val items = entityManager.createQuery("select p from Posting p $where order by p.$field $direction, p.id desc", Posting::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }
            .setFirstResult(condition.pageNum * pageSize).setMaxResults(pageSize).resultList
        return PostingPage(items, condition.pageNum, pageSize, total)
    }

    /**
     * 여러 프로젝트의 글 한 쪽을 찾는다. 조직 게시판 목록이 쓴다 (v1.6 `SearchCondition.asExpressionList(Organization)`).
     * 공지도 일반 글과 함께 정렬되어 나온다. [projectIds]가 비면 결과도 비어 있다. 라벨 조건은 쓰지 않는다.
     */
    @Transactional(readOnly = true)
    fun searchInProjects(projectIds: Collection<Long>, condition: PostingSearchCondition, pageSize: Int): PostingPage {
        if (projectIds.isEmpty()) return PostingPage(emptyList(), condition.pageNum, pageSize, 0)
        val where = StringBuilder("where p.project.id in :projects")
        val params = linkedMapOf<String, Any>("projects" to projectIds)
        if (condition.filter.isNotEmpty()) {
            where.append(" and (lower(p.title) like :filter escape '\\' or lower(cast(p.body as string)) like :filter escape '\\')")
            params["filter"] = "%" + condition.filter.lowercase() + "%"
        }
        val field = if (condition.effectiveOrderBy in ORDERABLE) condition.effectiveOrderBy else "createdDate"
        val direction = if (condition.orderDir.equals("asc", ignoreCase = true)) "asc" else "desc"
        val total = entityManager.createQuery("select count(p) from Posting p $where", java.lang.Long::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        val items = entityManager.createQuery("select p from Posting p $where order by p.$field $direction, p.id desc", Posting::class.java)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }
            .setFirstResult(condition.pageNum * pageSize).setMaxResults(pageSize).resultList
        return PostingPage(items, condition.pageNum, pageSize, total)
    }

    /** [project]의 공지를 최근 작성한 순서로 모두 돌려준다 (v1.6 `Posting.findNotices`). */
    @Transactional(readOnly = true)
    fun notices(project: Project): List<Posting> =
        entityManager.createQuery("select p from Posting p where p.project.id = :project and p.notice = true order by p.createdDate desc, p.id desc", Posting::class.java)
            .setParameter("project", project.id).resultList

    /** `like`의 특수 문자(`%`, `_`)와 이스케이프 문자(`!`)를 그대로 찾도록 이스케이프한다. */

    companion object {
        /** 정렬 기준으로 쓸 수 있는 게시글 속성이다. 화면은 `updatedDate`, `createdDate`, `numOfComments`를 보낸다. */
        val ORDERABLE = setOf("number", "title", "createdDate", "updatedDate", "numOfComments")
    }
}
