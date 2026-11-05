package dev.yona.domain.site

import dev.yona.domain.board.Posting
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Project
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 한 쪽 분량의 목록이다. [pageIndex]는 0부터이다. */
data class ContentPage<T>(val items: List<T>, val pageIndex: Int, val totalPageCount: Int)

/**
 * 사이트 관리의 프로젝트, 게시글, 이슈 목록이다. v1.6 `Project.findByName`, `Posting.finder`, `Issue.findIssuesByState`
 * (`SiteApp.projectList`, `postList`, `issueList`)에 해당한다. 읽기 권한을 따지지 않고 비공개 프로젝트의 것도 모두 담는다.
 */
@Service
@Transactional(readOnly = true)
class SiteContentService(private val entityManager: EntityManager) {

    /**
     * 프로젝트를 한 쪽(25개) 찾는다. [name]이 비어 있으면 만든 최근 순이고, 아니면 이름에 대소문자 구분 없이 포함된 프로젝트를 번호 순으로 찾는다
     * (v1.6은 정렬을 정하지 않는다). 소유자와 개요는 보지 않는다.
     */
    fun projects(name: String, pageIndex: Int): ContentPage<Project> =
        if (name.isBlank()) page("from Project p order by p.createdDate desc, p.id desc", "select count(p) from Project p", emptyMap(), PROJECT_PAGE_SIZE, pageIndex, Project::class.java)
        else page(
            "from Project p where lower(p.name) like :name escape '\\' order by p.id", "select count(p) from Project p where lower(p.name) like :name escape '\\'",
            mapOf("name" to dev.yona.util.LikeEscape.contains(name)), PROJECT_PAGE_SIZE, pageIndex, Project::class.java
        )

    /** 게시글을 만든 최근 순으로 한 쪽(30개) 찾는다. 공지와 비공개 프로젝트의 글도 담는다. */
    fun postings(pageIndex: Int): ContentPage<Posting> =
        page("from Posting p order by p.createdDate desc, p.id desc", "select count(p) from Posting p", emptyMap(), PAGE_SIZE, pageIndex, Posting::class.java)

    /** [state]인 이슈를 만든 최근 순으로 한 쪽(30개) 찾는다. [State.ALL]이면 모든 상태의 이슈이다(v1.6은 상태가 `ALL`인 이슈를 찾아 항상 비었다, R-53-4). */
    fun issues(state: State, pageIndex: Int): ContentPage<Issue> =
        if (state == State.ALL) page("from Issue i order by i.createdDate desc, i.id desc", "select count(i) from Issue i", emptyMap(), PAGE_SIZE, pageIndex, Issue::class.java)
        else page(
            "from Issue i where i.state = :state order by i.createdDate desc, i.id desc", "select count(i) from Issue i where i.state = :state",
            mapOf("state" to state), PAGE_SIZE, pageIndex, Issue::class.java
        )

    private fun <T> page(select: String, count: String, params: Map<String, Any>, size: Int, pageIndex: Int, type: Class<T>): ContentPage<T> {
        val total = entityManager.createQuery(count, java.lang.Long::class.java).also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.singleResult.toLong()
        val items = entityManager.createQuery("select ${alias(select)} $select", type)
            .also { q -> params.forEach { (k, v) -> q.setParameter(k, v) } }.setFirstResult(pageIndex * size).setMaxResults(size).resultList
        return ContentPage(items, pageIndex, Math.ceil(total.toDouble() / size).toInt())
    }

    private fun alias(select: String) = select.substringAfter("from ").split(" ")[1]

    companion object {
        /** 프로젝트 목록의 한 쪽 개수이다 (v1.6 `SiteApp.PROJECT_COUNT_PER_PAGE`). */
        const val PROJECT_PAGE_SIZE = 25

        /** 게시글과 이슈 목록의 한 쪽 개수이다 (v1.6 `SiteApp.POSTING_COUNT_PER_PAGE`, `ISSUE_COUNT_PER_PAGE`). */
        const val PAGE_SIZE = 30
    }
}
