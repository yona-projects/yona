package dev.yona.domain.issue

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import jakarta.persistence.TypedQuery
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Calendar
import java.util.Date

/**
 * 프로젝트 이슈 목록의 검색 조건이다. v1.6 `models.support.SearchCondition`(`app/models/support/SearchCondition.java`)과
 * 그 부모 `AbstractPostingApp.SearchCondition`에 해당한다.
 *
 * @property state `open`, `closed`만 걸러 내고 그 밖의 값은 모든 상태이다
 * @property orderBy 정렬 기준. [IssueSearchService.ORDERABLE]에 없는 값은 `createdDate`이다
 * @property orderDir `asc` 또는 `desc`
 * @property filter 제목, 본문, 댓글에서 찾을 문자열
 * @property pageNum 0부터 시작하는 쪽 번호
 * @property commentedCheck 댓글이 있는 이슈만
 * @property milestoneId 마일스톤 번호. [NO_VALUE]이면 마일스톤이 없는 이슈
 * @property labelIds 고른 라벨 번호. 모든 라벨을 가진 이슈만 찾는다
 * @property authorId 작성자 번호. [NO_VALUE]이면 작성자가 없는 이슈 (프로젝트를 가리지 않는 검색도 이 값으로 거른다)
 * @property assigneeId 담당자 번호. [NO_VALUE]이면 담당자가 없는 이슈 (프로젝트를 가리지 않는 검색도 이 값으로 거른다)
 * @property commenterId 지정하면 **현재 사용자**가 댓글을 쓴 이슈만 (v1.6은 번호를 보지 않고 현재 사용자를 쓴다)
 * @property sharerId 지정하면 현재 사용자가 공유받은 이슈만. 비로그인이면 빈 결과
 * @property favoriteId 지정하면 현재 사용자가 즐겨찾는 이슈만. 비로그인이면 빈 결과
 * @property mentionId 지정하면 현재 사용자가 멘션된 이슈만. 비로그인이면 빈 결과
 * @property dueDate 지정한 날까지(그날 포함) 마감인 이슈
 * @property projectNames 조직 이슈 목록이 고른 프로젝트 이름. 검색 질의는 쓰지 않고 화면의 선택 상태를 유지하는 데 쓴다
 */
data class IssueSearchCondition(
    val state: String = State.OPEN.state,
    val orderBy: String = "createdDate",
    val orderDir: String = "desc",
    val filter: String = "",
    val pageNum: Int = 0,
    val commentedCheck: Boolean = false,
    val milestoneId: Long? = null,
    val labelIds: Set<Long> = emptySet(),
    val authorId: Long? = null,
    val assigneeId: Long? = null,
    val commenterId: Long? = null,
    val sharerId: Long? = null,
    val favoriteId: Long? = null,
    val mentionId: Long? = null,
    val dueDate: Date? = null,
    val projectNames: List<String> = emptyList(),
    /** 이 시각 이상에 만든 이슈만 찾는다(Magpie 연동, X-23). */
    val createdSince: Date? = null,
    /** 이 시각 미만에 만든 이슈만 찾는다(Magpie 연동, X-23). */
    val createdUntil: Date? = null
) {
    /** 담당자, 작성자 조건 중 하나라도 있으면 `true`이다 (v1.6 `hasCondition`). 초안 목록을 보일지 정할 때 쓴다. */
    fun hasCondition(): Boolean = assigneeId != null || authorId != null || mentionId != null || commenterId != null || sharerId != null || favoriteId != null

    /** 마감일을 `yyyy-MM-dd`로 만든다. 없으면 `null`이다. */
    val dueDateString: String? get() = dueDate?.let { java.text.SimpleDateFormat("yyyy-MM-dd").format(it) }

    companion object {
        /** 없음을 뜻하는 번호이다. 마일스톤 없음(`Milestone.NULL_MILESTONE_ID`), 익명 사용자(`User.anonymous.id`)가 모두 -1이다. */
        const val NO_VALUE = -1L
    }
}

/**
 * 한 쪽 분량의 검색 결과이다. v1.6 Ebean `Page`에 해당한다.
 *
 * @property pageIndex 0부터 시작하는 쪽 번호
 * @property totalRows 조건에 맞는 전체 이슈 수
 */
data class IssuePage(val items: List<Issue>, val pageIndex: Int, val pageSize: Int, val totalRows: Long) {
    /** 전체 쪽 수이다. */
    val totalPageCount: Int get() = if (pageSize <= 0) 0 else Math.ceil(totalRows.toDouble() / pageSize).toInt()
}

/**
 * 프로젝트의 이슈를 검색한다. v1.6 `SearchCondition.asExpressionList(Project)`가 만들던 질의를 JPQL로 옮겼다.
 *
 * - 초안(`isDraft`)은 항상 뺀다.
 * - 작성자, 검색어, 라벨, 담당자 조건이 모두 없으면 하위 이슈(부모가 있는 이슈)는 뺀다.
 * - 정렬은 가중치(`weight`) 내림차순이 먼저이고 그 뒤에 [IssueSearchCondition.orderBy]이다. 마감일 정렬은 마감일이 없는 이슈를 항상 뒤에 둔다.
 */
@Service
class IssueSearchService(private val entityManager: EntityManager) {

    /** [project]에서 [condition]에 맞는 이슈 한 쪽을 찾는다. [viewer]는 댓글, 공유, 즐겨찾기 조건에 쓴다. */
    @Transactional(readOnly = true)
    fun search(project: Project, condition: IssueSearchCondition, viewer: User?, pageSize: Int): IssuePage {
        val query = Query(project, condition, viewer)
        val total = count(project, condition, viewer)
        val items = query.typed("select i from Issue i ${query.where()} order by ${orderBy(condition)}", Issue::class.java)
            .setFirstResult(condition.pageNum * pageSize).setMaxResults(pageSize).resultList
        return IssuePage(items, condition.pageNum, pageSize, total)
    }

    /** 조건에 맞는 모든 이슈를 정렬해 돌려준다. 엑셀 내보내기에 쓴다. */
    @Transactional(readOnly = true)
    fun findAll(project: Project, condition: IssueSearchCondition, viewer: User?): List<Issue> {
        val query = Query(project, condition, viewer)
        return query.typed("select i from Issue i ${query.where()} order by ${orderBy(condition)}", Issue::class.java).resultList
    }

    /** 조건에 맞는 이슈 수이다 (v1.6 `Issue.countIssuesBy`). */
    @Transactional(readOnly = true)
    fun count(project: Project, condition: IssueSearchCondition, viewer: User?): Long {
        val query = Query(project, condition, viewer)
        return query.typed("select count(i) from Issue i ${query.where()}", java.lang.Long::class.java).singleResult.toLong()
    }

    /**
     * 프로젝트를 가리지 않고 [viewer]와 관련된 이슈 한 쪽을 찾는다. 내 이슈(`/user/issues`)가 쓴다 (v1.6 `SearchCondition.asExpressionList()`).
     *
     * 프로젝트 목록과 달리 읽기 권한을 따지지 않고, 하위 이슈도 담으며, 라벨과 마일스톤 조건은 쓰지 않는다.
     * 작성자, 담당자, 댓글, 멘션, 공유, 즐겨찾기 조건은 값에 관계없이 **현재 사용자** 기준이다.
     */
    @Transactional(readOnly = true)
    fun searchAll(condition: IssueSearchCondition, viewer: User, pageSize: Int): IssuePage {
        val query = Query(null, condition, viewer)
        val total = countAll(condition, viewer)
        val items = query.typed("select i from Issue i ${query.where()} order by ${orderBy(condition)}", Issue::class.java)
            .setFirstResult(condition.pageNum * pageSize).setMaxResults(pageSize).resultList
        return IssuePage(items, condition.pageNum, pageSize, total)
    }

    /** 프로젝트를 가리지 않고 조건에 맞는 이슈 수이다 (v1.6 `Issue.countIssuesBy(SearchCondition)`). */
    @Transactional(readOnly = true)
    fun countAll(condition: IssueSearchCondition, viewer: User): Long {
        val query = Query(null, condition, viewer)
        return query.typed("select count(i) from Issue i ${query.where()}", java.lang.Long::class.java).singleResult.toLong()
    }

    /**
     * [projectIds]에 속한 프로젝트의 이슈 한 쪽을 찾는다. 조직 이슈 목록이 쓴다 (v1.6 `SearchCondition.asExpressionList(Organization)`).
     *
     * 내 이슈와 같이 하위 이슈를 담고 라벨, 마일스톤, 댓글 단 사람 조건은 쓰지 않으며 작성자, 담당자, 멘션, 공유, 즐겨찾기 조건은
     * 값에 관계없이 현재 사용자 기준이다. 내 이슈와 달리 조건이 없어도 담당자 조건을 자동으로 걸지 않는다.
     *
     * @param viewer 보는 사람. 없으면 익명이며 사용자 기준 조건은 걸리지 않는다
     */
    @Transactional(readOnly = true)
    fun searchIn(projectIds: Collection<Long>, condition: IssueSearchCondition, viewer: User?, pageSize: Int): IssuePage {
        val query = Query(null, condition.copy(commenterId = null), viewer, projectIds)
        val total = countIn(projectIds, condition, viewer)
        val items = query.typed("select i from Issue i ${query.where()} order by ${orderBy(condition)}", Issue::class.java)
            .setFirstResult(condition.pageNum * pageSize).setMaxResults(pageSize).resultList
        return IssuePage(items, condition.pageNum, pageSize, total)
    }

    /** [projectIds]에 속한 프로젝트에서 조건에 맞는 이슈 수이다 (v1.6 `Issue.countIssuesBy(Organization, SearchCondition)`). */
    @Transactional(readOnly = true)
    fun countIn(projectIds: Collection<Long>, condition: IssueSearchCondition, viewer: User?): Long {
        val query = Query(null, condition.copy(commenterId = null), viewer, projectIds)
        return query.typed("select count(i) from Issue i ${query.where()}", java.lang.Long::class.java).singleResult.toLong()
    }

    /** 사용자가 멘션된 열린 이슈 수이다 (v1.6 `Issue.getCountOfMentionedOpenIssues`). */
    @Transactional(readOnly = true)
    fun countMentionedOpenIssues(userId: Long): Long {
        val ids = mentioningIssueIds(userId)
        if (ids.isEmpty()) return 0
        return entityManager.createQuery("select count(i) from Issue i where i.id in :ids and i.state = :state", java.lang.Long::class.java)
            .setParameter("ids", ids).setParameter("state", State.OPEN).singleResult.toLong()
    }

    /**
     * 사용자가 멘션된 이슈 번호이다. 본문에서 멘션된 이슈와 멘션된 댓글이 달린 이슈를 모은다 (v1.6 `Mention.getMentioningIssueIds`).
     */
    fun mentioningIssueIds(userId: Long): Set<Long> {
        val ids = HashSet<Long>()
        val commentIds = HashSet<Long>()
        entityManager.createQuery("select m from Mention m where m.user.id = :user and m.resourceType in :types", dev.yona.domain.notification.Mention::class.java)
            .setParameter("user", userId).setParameter("types", listOf(ResourceType.ISSUE_POST, ResourceType.ISSUE_COMMENT)).resultList.forEach { mention ->
                val id = mention.resourceId?.toLongOrNull() ?: return@forEach
                if (mention.resourceType == ResourceType.ISSUE_POST) ids += id else commentIds += id
            }
        if (commentIds.isNotEmpty()) {
            ids += entityManager.createQuery("select c.issue.id from IssueComment c where c.id in :ids", java.lang.Long::class.java)
                .setParameter("ids", commentIds).resultList.map { it.toLong() }
        }
        return ids
    }

    private fun orderBy(condition: IssueSearchCondition): String {
        val direction = if (condition.orderDir.equals("asc", ignoreCase = true)) "asc" else "desc"
        val field = if (condition.orderBy in ORDERABLE) condition.orderBy else "createdDate"
        val main = if (field == "dueDate") "case when i.dueDate is null then 1 else 0 end, i.dueDate $direction" else "i.$field $direction"
        return "i.weight desc, i.isDraft desc, $main, i.id $direction"
    }

    /** 질의의 조건절과 매개변수를 모은다. */
    private inner class Query(
        private val project: Project?, private val condition: IssueSearchCondition, viewer: User?, projectIds: Collection<Long>? = null
    ) {
        private val clauses = mutableListOf("i.isDraft = false")
        private val params = linkedMapOf<String, Any?>()

        init {
            if (project != null) {
                clauses += "i.project.id = :project"
                params["project"] = project.id
            }
            if (projectIds != null) {
                if (projectIds.isEmpty()) clauses += "1 = 0" else { clauses += "i.project.id in :projectIds"; params["projectIds"] = projectIds }
            }
            val viewerId = viewer?.id ?: IssueSearchCondition.NO_VALUE
            // 프로젝트를 가리지 않는 검색은 보는 사람이 읽을 수 있는 프로젝트의 이슈만 찾는다. v1.6은 읽기 권한을 따지지 않아 프로젝트에서 빠진 뒤에도 내 이슈가 보였고,
            // 담당자 없음(assigneeId=-1) 검색으로 모든 프로젝트의 담당 없는 이슈 제목이 보였다(C135, R-49-1)
            if (project == null && projectIds == null) {
                params["vPublic"] = ProjectScope.PUBLIC; params["vPrivate"] = ProjectScope.PRIVATE; params["vUser"] = viewerId
                clauses += "(i.project.projectScope = :vPublic or exists (select 1 from ProjectUser pu where pu.project.id = i.project.id and pu.user.id = :vUser) or " +
                    "(i.project.organization.id in (select ou.organization.id from OrganizationUser ou where ou.user.id = :vUser) and i.project.projectScope <> :vPrivate))"
            }
            if (condition.filter.isNotBlank()) {
                clauses += "(lower(i.title) like :filter escape '\\' or lower(cast(i.body as string)) like :filter escape '\\' or exists " +
                    "(select 1 from IssueComment c where c.issue.id = i.id and lower(cast(c.contents as string)) like :filter escape '\\'))"
                params["filter"] = "%" + condition.filter.lowercase() + "%"
            }
            // 작성자와 담당자는 요청한 사용자 번호로 거른다. v1.6은 프로젝트를 가리지 않는 검색에서 값을 무시하고 현재 사용자를 썼다(C047)
            condition.authorId?.let {
                if (it == IssueSearchCondition.NO_VALUE) clauses += "i.authorId is null"
                else { clauses += "i.authorId = :author"; params["author"] = it }
            }
            condition.assigneeId?.let {
                if (it == IssueSearchCondition.NO_VALUE) clauses += "i.assignee is null"
                else if (project == null) { clauses += "i.assignee.user.id = :assignee"; params["assignee"] = it }
                else { clauses += "(i.assignee.user.id = :assignee and i.assignee.project.id = :project)"; params["assignee"] = it }
            }
            if (condition.commenterId != null && (project != null || condition.commenterId != IssueSearchCondition.NO_VALUE)) {
                clauses += "i.id in (select c.issue.id from IssueComment c where c.authorId = :commenter" + (if (project != null) " and c.projectId = :project)" else ")")
                params["commenter"] = viewerId
            }
            // 멘션, 공유, 즐겨찾기는 로그인한 사용자 기준이라 비로그인이면 조건을 무시하지 않고 빈 결과로 한다. v1.6은 무시해 전체가 나왔다(C048)
            // 프로젝트 안에서도 멘션 조건을 건다. v1.6은 프로젝트 목록에서 쓰지 않았다(C049)
            if (condition.mentionId != null) {
                val ids = if (viewer != null) mentioningIssueIds(viewerId) else emptySet()
                if (ids.isEmpty()) clauses += "1 = 0" else { clauses += "i.id in :mentioned"; params["mentioned"] = ids }
            }
            if (condition.sharerId != null) {
                if (viewer == null) clauses += "1 = 0" else {
                    clauses += "i.id in (select s.issue.id from IssueSharer s where s.user.id = :sharer)"
                    params["sharer"] = viewerId
                }
            }
            if (condition.favoriteId != null) {
                if (viewer == null) clauses += "1 = 0" else {
                    clauses += "i.id in (select f.issue.id from FavoriteIssue f where f.user.id = :favorite)"
                    params["favorite"] = viewerId
                }
            }
            addState()
            if (project != null) {
                addLabels()
                condition.milestoneId?.let {
                    if (it == IssueSearchCondition.NO_VALUE) clauses += "i.milestone is null" else { clauses += "i.milestone.id = :milestone"; params["milestone"] = it }
                }
            }
            if (condition.commentedCheck) clauses += "i.numOfComments >= 1"
            condition.dueDate?.let { clauses += "i.dueDate < :dueBefore"; params["dueBefore"] = nextDay(it) }
            condition.createdSince?.let { clauses += "i.createdDate >= :createdSince"; params["createdSince"] = it }
            condition.createdUntil?.let { clauses += "i.createdDate < :createdUntil"; params["createdUntil"] = it }
            if (project != null && condition.authorId == null && condition.filter.isBlank() && condition.labelIds.isEmpty() && condition.assigneeId == null && condition.mentionId == null) {
                clauses += "i.parent is null"
            }
        }

        private fun addState() {
            val state = State.entries.firstOrNull { it.state == condition.state } ?: State.OPEN
            if (state == State.OPEN || state == State.CLOSED) {
                clauses += "i.state = :state"
                params["state"] = state
            }
        }

        /** 고른 라벨을 모두 가진 이슈만 찾는다. 존재하지 않는 라벨 번호는 건너뛰고, 존재하는 라벨이 하나도 없으면 아무것도 찾지 못한다. */
        private fun addLabels() {
            if (condition.labelIds.isEmpty()) return
            val existing = entityManager.createQuery("select l.id from IssueLabel l where l.id in :ids", java.lang.Long::class.java)
                .setParameter("ids", condition.labelIds).resultList.map { it.toLong() }
            if (existing.isEmpty()) { clauses += "1 = 0"; return }
            existing.forEachIndexed { index, id ->
                clauses += "exists (select 1 from Issue t$index join t$index.labels l$index where t$index.id = i.id and l$index.id = :label$index)"
                params["label$index"] = id
            }
        }

        fun where(): String = "where " + clauses.joinToString(" and ")

        fun <T> typed(jpql: String, type: Class<T>): TypedQuery<T> {
            val typed = entityManager.createQuery(jpql, type)
            params.forEach { (name, value) -> if (jpql.contains(":$name")) typed.setParameter(name, value) }
            return typed
        }
    }

    private fun nextDay(date: Date): Date = Calendar.getInstance().apply { time = date; add(Calendar.DAY_OF_MONTH, 1) }.time

    /** `like`의 특수 문자(`%`, `_`)와 이스케이프 문자(`!`)를 그대로 찾도록 이스케이프한다. */

    companion object {
        /** 정렬 기준으로 쓸 수 있는 이슈 속성이다. 화면은 `dueDate`, `updatedDate`, `createdDate`, `numOfComments`를 보낸다. */
        val ORDERABLE = setOf("id", "number", "title", "state", "weight", "createdDate", "updatedDate", "dueDate", "numOfComments")
    }
}
