package dev.yona.domain.search

import dev.yona.config.YonaProperties
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.Milestone
import dev.yona.domain.project.Organization
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 검색 결과의 종류이다. v1.6 `models.enumeration.SearchType`이다. 값 문자열은 대소문자를 구분해 비교한다.
 *
 * @property type 요청과 화면에서 쓰는 문자열
 */
enum class SearchType(val type: String) {
    AUTO("auto"), NA("not available"), USER("user"), PROJECT("project"), ISSUE("issue"), POST("post"),
    MILESTONE("milestone"), ISSUE_COMMENT("issue_comment"), POST_COMMENT("post_comment"), REVIEW("review");

    companion object {
        /** 문자열에 해당하는 종류이다. 모르는 값은 [NA]이다. */
        fun of(value: String?): SearchType = entries.firstOrNull { it.type == value } ?: NA
    }
}

/**
 * 검색 범위이다. 둘 다 없으면 전체, [organization]이 있으면 조직, [project]가 있으면 프로젝트이다.
 * [deniedOrganizations]는 조직의 OAuth 앱 정책(X-39)이 이 요청의 앱을 막는 조직의 번호이다. 그 조직의 비공개와 보호 프로젝트에 속한 결과는 빼고, 내가 쓴 글도 마찬가지이다.
 */
data class SearchScope(val organization: Organization? = null, val project: Project? = null, val deniedOrganizations: Set<Long> = emptySet())

/** 종류별 검색 결과 수이다. 프로젝트 범위에서는 [projects]를 세지 않아 0이다. */
data class SearchCounts(
    val users: Long = 0, val projects: Long = 0, val issues: Long = 0, val posts: Long = 0, val milestones: Long = 0,
    val issueComments: Long = 0, val postComments: Long = 0, val reviews: Long = 0
) {
    /** 종류의 결과 수이다. */
    fun of(type: SearchType): Long = when (type) {
        SearchType.USER -> users
        SearchType.PROJECT -> projects
        SearchType.ISSUE -> issues
        SearchType.POST -> posts
        SearchType.MILESTONE -> milestones
        SearchType.ISSUE_COMMENT -> issueComments
        SearchType.POST_COMMENT -> postComments
        SearchType.REVIEW -> reviews
        else -> 0
    }
}

/** 한 쪽 분량의 검색 결과이다. [pageIndex]는 0부터이다. */
data class SearchPage<T>(val items: List<T>, val pageIndex: Int, val totalPageCount: Int)

/**
 * 통합 검색이다. v1.6 `models.Search`(`app/models/Search.java`)와 `SearchResult.updateSearchType`에 해당한다.
 *
 * 키워드는 대소문자를 구분하지 않고 필드 안에 통째로 포함되는지만 본다. `%`와 `_`는 글자 그대로 찾는다.
 * 볼 수 있는 프로젝트의 기준은 범위마다 다르다. 전체와 조직 범위의 이슈, 게시글, 댓글에서는 내가 쓴 글(이슈는 담당한 이슈 포함)이
 * 권한을 잃은 프로젝트에 있어도 찾는다. 프로젝트 범위는 라우트에서 읽기 권한을 확인하므로 프로젝트와 키워드만 본다.
 */
@Service
class SearchService(
    private val entityManager: EntityManager,
    private val properties: YonaProperties,
    private val metrics: dev.yona.observability.CollaborationMetrics
) {

    /** 한 쪽의 결과 수이다 (v1.6 `SearchApp.DEFAULT_PAGE`). */
    val pageSize: Int get() = PAGE_SIZE

    /** 종류별 결과 수를 센다. 프로젝트 범위에서는 프로젝트를 세지 않는다. */
    @Transactional(readOnly = true)
    fun counts(scope: SearchScope, keyword: String, viewer: User?): SearchCounts = metrics.search("counts") { countsImpl(scope, keyword, viewer) }

    private fun countsImpl(scope: SearchScope, keyword: String, viewer: User?): SearchCounts = SearchCounts(
        users = count(SearchType.USER, scope, keyword, viewer),
        projects = if (scope.project == null) count(SearchType.PROJECT, scope, keyword, viewer) else 0,
        issues = count(SearchType.ISSUE, scope, keyword, viewer),
        posts = count(SearchType.POST, scope, keyword, viewer),
        milestones = count(SearchType.MILESTONE, scope, keyword, viewer),
        issueComments = count(SearchType.ISSUE_COMMENT, scope, keyword, viewer),
        postComments = count(SearchType.POST_COMMENT, scope, keyword, viewer),
        reviews = count(SearchType.REVIEW, scope, keyword, viewer)
    )

    /**
     * 요청한 종류를 정한다. [SearchType.AUTO]이면 결과가 있는 첫 종류(이슈, 사용자, 프로젝트, 게시글, 마일스톤, 이슈 댓글,
     * 게시글 댓글, 코드 리뷰 순)이고 모두 없으면 이슈이다. 그 밖에는 결과 수와 관계없이 그대로이다 (v1.6 `SearchResult.updateSearchType`).
     */
    fun resolve(requested: SearchType, counts: SearchCounts): SearchType {
        if (requested != SearchType.AUTO) return requested
        return AUTO_ORDER.firstOrNull { counts.of(it) > 0 } ?: SearchType.ISSUE
    }

    /** [type]의 결과 한 쪽을 찾는다. [type]은 [SearchType.AUTO], [SearchType.NA]가 아니어야 한다. */
    @Transactional(readOnly = true)
    fun page(type: SearchType, scope: SearchScope, keyword: String, viewer: User?, pageIndex: Int): SearchPage<Any> =
        metrics.search("page") { pageImpl(type, scope, keyword, viewer, pageIndex) }

    private fun pageImpl(type: SearchType, scope: SearchScope, keyword: String, viewer: User?, pageIndex: Int): SearchPage<Any> {
        val query = Query(type, scope, keyword, viewer)
        val total = query.count()
        val items = query.typed("select ${query.spec.alias} from ${query.spec.entity} ${query.spec.alias} ${query.where()} order by ${query.spec.order}", Any::class.java)
            .setFirstResult(pageIndex * PAGE_SIZE).setMaxResults(PAGE_SIZE).resultList
        return SearchPage(items, pageIndex, Math.ceil(total.toDouble() / PAGE_SIZE).toInt())
    }

    private fun count(type: SearchType, scope: SearchScope, keyword: String, viewer: User?): Long = Query(type, scope, keyword, viewer).count()

    /**
     * 종류마다 다른 부분이다.
     *
     * @property projectPath 항목이 속한 프로젝트를 가리키는 경로. 사용자와 프로젝트 검색은 `null`이다
     * @property fields 키워드를 찾을 필드
     * @property authorPath 작성자 번호 경로. 작성자 개념이 없으면 `null`이다
     * @property assignee 담당자 조건도 나의 항목으로 칠지(이슈만)
     */
    private data class Spec(
        val entity: String, val alias: String, val projectPath: String?, val fields: List<String>, val authorPath: String?,
        val order: String, val assignee: Boolean = false
    )

    private fun spec(type: SearchType): Spec = when (type) {
        SearchType.ISSUE -> Spec(Issue::class.simpleName!!, "i", "i.project", listOf("i.title", "i.body"), "i.authorId", "i.createdDate desc, i.id desc", assignee = true)
        SearchType.POST -> Spec(Posting::class.simpleName!!, "i", "i.project", listOf("i.title", "i.body"), "i.authorId", "i.createdDate desc, i.id desc")
        SearchType.MILESTONE -> Spec(Milestone::class.simpleName!!, "i", "i.project", listOf("i.title", "i.contents"), null, "i.dueDate desc nulls last, i.id desc")
        SearchType.ISSUE_COMMENT -> Spec(IssueComment::class.simpleName!!, "i", "i.issue.project", listOf("i.contents"), "i.authorId", "i.createdDate desc, i.id desc")
        SearchType.POST_COMMENT -> Spec(PostingComment::class.simpleName!!, "i", "i.posting.project", listOf("i.contents"), "i.authorId", "i.createdDate desc, i.id desc")
        SearchType.REVIEW -> Spec(ReviewComment::class.simpleName!!, "i", "i.thread.project", listOf("i.contents"), "i.authorId", "i.createdDate desc, i.id desc")
        SearchType.USER -> Spec(User::class.simpleName!!, "i", null, listOf("i.name", "i.loginId"), null, "i.name asc, i.id asc")
        SearchType.PROJECT -> Spec(Project::class.simpleName!!, "i", null, listOf("i.overview", "i.name"), null, "i.name asc, i.id asc")
        else -> throw IllegalArgumentException("검색할 수 없는 종류입니다: $type")
    }

    /** 질의의 조건절과 매개변수를 만든다. */
    private inner class Query(private val type: SearchType, private val scope: SearchScope, keyword: String, private val viewer: User?) {
        val spec = spec(type)
        private val params = linkedMapOf<String, Any?>("kw" to "%" + keyword.lowercase() + "%")
        private val where: String = build()

        fun where(): String = "where $where"

        fun count(): Long = typed("select count(${spec.alias}) from ${spec.entity} ${spec.alias} ${where()}", java.lang.Long::class.java).singleResult.toLong()

        fun <T> typed(jpql: String, resultType: Class<T>) = entityManager.createQuery(jpql, resultType).also { q ->
            params.forEach { (name, value) -> if (Regex(":$name\\b").containsMatchIn(jpql)) q.setParameter(name, value) }
        }

        private fun keywordMatches(): String =
            spec.fields.joinToString(" or ", "(", ")") { "lower(cast($it as string)) like :kw escape '\\'" }

        private fun build(): String {
            val base = when (type) {
                SearchType.USER -> return userCondition()
                SearchType.PROJECT -> projectCondition()
                else -> itemCondition()
            }
            if (scope.deniedOrganizations.isEmpty()) return base
            val path = if (type == SearchType.PROJECT) "i" else requireNotNull(spec.projectPath)
            params["deniedOrganizations"] = scope.deniedOrganizations; params["deniedPublicScope"] = ProjectScope.PUBLIC
            return "($base) and not (coalesce($path.organization.id, -1L) in :deniedOrganizations and $path.projectScope <> :deniedPublicScope)"
        }

        private fun me(): Long? = viewer?.id

        /** 작성자가 있는 항목, 마일스톤의 조건이다. */
        private fun itemCondition(): String {
            val projectPath = requireNotNull(spec.projectPath)
            scope.project?.let { params["scopeProject"] = it.id; return "$projectPath.id = :scopeProject and ${keywordMatches()}" }
            val parts = mutableListOf("(${visibleProjects(projectPath)} and ${keywordMatches()})")
            me()?.let { id ->
                params["me"] = id
                spec.authorPath?.let { parts += "($it = :me and ${keywordMatches()})" }
                if (spec.assignee) parts += "(${spec.alias}.assignee.id in (select a.id from Assignee a where a.user.id = :me) and ${keywordMatches()})"
            }
            val any = parts.joinToString(" or ", "(", ")")
            return scope.organization?.let { params["scopeOrg"] = it.id; "$projectPath.organization.id = :scopeOrg and $any" } ?: any
        }

        /** 볼 수 있는 프로젝트의 조건이다. 전체 범위와 조직 범위의 기준이 다르다. */
        private fun visibleProjects(projectPath: String): String {
            val id = me() ?: return "$projectPath.projectScope = :publicScope".also { params["publicScope"] = ProjectScope.PUBLIC }
            params["me"] = id
            params["publicScope"] = ProjectScope.PUBLIC
            val member = "exists (select 1 from ProjectUser pu where pu.project.id = $projectPath.id and pu.user.id = :me)"
            val organization = scope.organization
            if (organization != null) {
                val organizationMember = isOrganizationMember(requireNotNull(organization.id), id)
                return if (organizationMember) {
                    params["publicScopes"] = listOf(ProjectScope.PUBLIC, ProjectScope.PROTECTED)
                    "($member or $projectPath.projectScope in :publicScopes)"
                } else {
                    "($member or $projectPath.projectScope = :publicScope)"
                }
            }
            val organizations = myOrganizationIds(id)
            if (organizations.isEmpty()) return "($projectPath.projectScope = :publicScope or $member)"
            params["myOrganizations"] = organizations
            params["privateScope"] = ProjectScope.PRIVATE
            return "($projectPath.projectScope = :publicScope or $member or ($projectPath.organization.id in :myOrganizations and $projectPath.projectScope <> :privateScope))"
        }

        private fun userCondition(): String {
            params["active"] = UserState.ACTIVE
            val base = "${spec.alias}.state = :active and ${keywordMatches()}"
            scope.project?.let { params["scopeProject"] = it.id; return "$base and exists (select 1 from ProjectUser pu where pu.project.id = :scopeProject and pu.user.id = i.id)" }
            scope.organization?.let { params["scopeOrg"] = it.id; return "$base and exists (select 1 from OrganizationUser ou where ou.organization.id = :scopeOrg and ou.user.id = i.id)" }
            return base
        }

        /** 프로젝트 검색의 가시성이다. 사이트가 프로젝트 목록을 숨기면 공개 프로젝트도 구성원만 찾는다. */
        private fun projectCondition(): String {
            val hide = properties.hideProjectListing
            val organization = scope.organization?.let { params["scopeOrg"] = it.id; " and i.organization.id = :scopeOrg" }.orEmpty()
            val id = me()
            params["publicScope"] = ProjectScope.PUBLIC
            if (id == null) {
                return if (hide) "1 = 0" else "i.projectScope = :publicScope and ${keywordMatches()}$organization"
            }
            params["me"] = id
            val visible = mutableListOf<String>()
            if (!hide) visible += "i.projectScope = :publicScope"
            val organizations = myOrganizationIds(id)
            if (organizations.isNotEmpty()) {
                params["myOrganizations"] = organizations
                params["protectedScope"] = ProjectScope.PROTECTED
                visible += "(i.organization.id in :myOrganizations and i.projectScope = :protectedScope)"
            }
            visible += "exists (select 1 from ProjectUser pu where pu.project.id = i.id and pu.user.id = :me)"
            return "(${visible.joinToString(" or ")}) and ${keywordMatches()}$organization"
        }
    }

    private fun myOrganizationIds(userId: Long): List<Long> =
        entityManager.createQuery("select ou.organization.id from OrganizationUser ou where ou.user.id = :user", java.lang.Long::class.java)
            .setParameter("user", userId).resultList.map { it.toLong() }.distinct()

    private fun isOrganizationMember(organizationId: Long, userId: Long): Boolean =
        entityManager.createQuery("select count(ou) from OrganizationUser ou where ou.organization.id = :org and ou.user.id = :user", java.lang.Long::class.java)
            .setParameter("org", organizationId).setParameter("user", userId).singleResult > 0

    /** `like`의 특수 문자(`%`, `_`)와 이스케이프 문자(`!`)를 그대로 찾도록 이스케이프한다. */

    companion object {
        private const val PAGE_SIZE = 20

        private val AUTO_ORDER = listOf(
            SearchType.ISSUE, SearchType.USER, SearchType.PROJECT, SearchType.POST, SearchType.MILESTONE,
            SearchType.ISSUE_COMMENT, SearchType.POST_COMMENT, SearchType.REVIEW
        )
    }
}
