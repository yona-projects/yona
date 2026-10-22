package dev.yona.domain.project

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 편집기의 `@` 멘션 후보를 모은다. v1.6 `ProjectApp.mentionList`와 그 보조 메서드들
 * (`app/controllers/ProjectApp.java:383-497, 832-930`)에 해당한다.
 */
@Service
class MentionListService(
    private val entityManager: EntityManager,
    private val users: UserRepository,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val issues: IssueRepository,
    private val postings: PostingRepository
) {

    /**
     * 사용자 후보를 순서대로 모은다.
     *
     * 검색어가 없거나 비공개 프로젝트이면 (1) 글의 댓글 작성자(최근 순)와 글쓴이 (2) 프로젝트 구성원 (3) 조직 구성원
     * (4) 프로젝트에 글을 썼거나 구독한 사람 (5) 이슈 공유자 순이고, 공개 프로젝트에서 검색어가 있으면 활성 사용자를 검색한다.
     * 마지막에 [viewer]를 한 번 빼고 다시 맨 뒤에 둔다.
     *
     * @param number 글 번호. 글에 속한 후보가 필요 없으면 `-1`
     * @param resourceType 글 종류 이름(`issue_post`, `board_post`). 그 밖은 글 후보가 없다
     */
    @Transactional(readOnly = true)
    fun users(project: Project, number: Long, resourceType: String, query: String, viewer: User?): List<User> {
        val list = linkedSetOf<Long>()
        val ordered = mutableListOf<User>()
        fun add(user: User?) {
            val id = user?.id ?: return
            if (list.add(id)) ordered += user
        }

        if (query.isEmpty() || !project.isPublic()) {
            collectAuthorAndCommenters(project, number, resourceType).forEach { add(it) }
            projectMembers(project).forEach { add(it) }
            groupMembers(project).forEach { add(it) }
            authorsAndWatchers(project).forEach { add(it) }
            sharers(project, number, resourceType).forEach { add(it) }
        } else {
            searchActive(query).forEach { add(it) }
        }

        if (viewer != null) {
            ordered.removeAll { it.id == viewer.id }
            ordered += viewer
        }
        return ordered
    }

    /**
     * 커밋 diff 화면(코드 리뷰 댓글)의 사용자 후보이다. 검색어가 없으면 (1) 커밋 작성자(이메일로 찾은 사용자와 이메일 앞부분을 로그인 ID로 쓰는 사용자)
     * (2) 이 커밋에 코드 리뷰 댓글을 단 사용자(최근에 단 사람이 뒤로) (3) 프로젝트 구성원 (4) 조직 구성원 순이고, 검색어가 있으면 활성 사용자를 검색한다.
     * 마지막에 [viewer]를 한 번 빼고 다시 맨 뒤에 둔다 (v1.6 `ProjectApp.mentionListAtCommitDiff`).
     *
     * @param fromProject 커밋이 있는 프로젝트(풀 리퀘스트이면 보내는 프로젝트)
     */
    @Transactional(readOnly = true)
    fun usersAtCommitDiff(project: Project, fromProject: Project, commitAuthorEmail: String?, commitId: String, query: String, viewer: User?): List<User> {
        val ordered = mutableListOf<User>()
        fun add(user: User?) { if (user != null && ordered.none { it.id == user.id }) ordered += user }
        if (query.isEmpty()) {
            commitAuthorEmail?.let { email ->
                add(userLookup.findByEmail(email))
                if (email.contains("@")) add(users.findByLoginId(email.substring(0, email.lastIndexOf("@"))))
            }
            val comments = entityManager.createQuery(
                "select c from ReviewComment c join c.thread t where t.commitId = :commitId and t.project.id = :projectId and t.pullRequest is null", dev.yona.domain.comment.ReviewComment::class.java
            ).setParameter("commitId", commitId).setParameter("projectId", fromProject.id).resultList
            for (comment in comments) {
                val author = comment.authorLoginId?.let { users.findByLoginId(it) } ?: continue
                ordered.removeAll { it.id == author.id }
                ordered += author
            }
            projectMembers(project).forEach { add(it) }
            groupMembers(project).forEach { add(it) }
        } else {
            searchActive(query).forEach { add(it) }
        }
        if (viewer != null) {
            ordered.removeAll { it.id == viewer.id }
            ordered += viewer
        }
        return ordered
    }

    /**
     * 풀 리퀘스트 화면의 사용자 후보이다. 검색어가 없으면 (1) 리뷰 댓글을 단 사용자(최근에 단 사람이 뒤로) (2) 프로젝트 구성원 (3) 조직 구성원 (4) 선택한 커밋의
     * 작성자(`commit`이 있을 때) 순이고, 검색어가 있으면 활성 사용자를 검색한다. 풀 리퀘스트를 보낸 사람이 없으면 더하고, [viewer]를 한 번 빼고 다시 맨 뒤에 둔다
     * (v1.6 `ProjectApp.mentionListAtPullRequest`).
     */
    @Transactional(readOnly = true)
    fun usersAtPullRequest(project: Project, pullRequest: dev.yona.domain.pullrequest.PullRequest, commitAuthorEmail: String?, query: String, viewer: User?): List<User> {
        val ordered = mutableListOf<User>()
        fun add(user: User?) { if (user != null && ordered.none { it.id == user.id }) ordered += user }
        if (query.isEmpty()) {
            val comments = entityManager.createQuery(
                "select c from ReviewComment c join c.thread t where t.pullRequest.id = :id order by c.id", dev.yona.domain.comment.ReviewComment::class.java
            ).setParameter("id", pullRequest.id).resultList
            for (comment in comments) {
                val author = comment.authorLoginId?.let { users.findByLoginId(it) } ?: continue
                ordered.removeAll { it.id == author.id }
                ordered += author
            }
            projectMembers(project).forEach { add(it) }
            groupMembers(project).forEach { add(it) }
            commitAuthorEmail?.let { email ->
                add(userLookup.findByEmail(email))
                if (email.contains("@")) add(users.findByLoginId(email.substring(0, email.lastIndexOf("@"))))
            }
        } else {
            searchActive(query).forEach { add(it) }
        }
        add(pullRequest.contributor)
        if (viewer != null) {
            ordered.removeAll { it.id == viewer.id }
            ordered += viewer
        }
        return ordered
    }

    /** 최근 이슈를 [limit]개까지 돌려준다. [query]가 있으면 제목에 들어 있거나 번호가 그 문자열로 시작하는 이슈만이다. */
    @Transactional(readOnly = true)
    fun issues(project: Project, query: String, limit: Int): List<Issue> {
        val projectId = requireNotNull(if (project.originalProject != null) project.originalProject?.id else project.id)
        val jpql = if (query.isEmpty()) {
            "select i from Issue i where i.project.id = :project order by i.createdDate desc, i.id desc"
        } else {
            "select i from Issue i where i.project.id = :project and (lower(i.title) like lower(concat('%', :query, '%')) " +
                "or cast(i.number as string) like concat(:query, '%')) order by i.createdDate desc, i.id desc"
        }
        val typed = entityManager.createQuery(jpql, Issue::class.java).setParameter("project", projectId)
        if (query.isNotEmpty()) typed.setParameter("query", query)
        return typed.setMaxResults(limit).resultList
    }

    /** 글의 댓글 작성자를 최근에 쓴 사람부터, 그 뒤에 글쓴이를 모은다. */
    private fun collectAuthorAndCommenters(project: Project, number: Long, resourceType: String): List<User> {
        val projectId = requireNotNull(project.id)
        val result = mutableListOf<User>()
        val postAuthorLoginId: String?
        val commentAuthors: List<String?>
        when (resourceType) {
            ResourceType.ISSUE_POST.resource -> {
                val issue = issues.findByProjectIdAndNumber(projectId, number) ?: return result
                postAuthorLoginId = issue.authorLoginId
                commentAuthors = entityManager.createQuery(
                    "select c.authorLoginId from IssueComment c where c.issue.id = :id order by c.createdDate, c.id", String::class.java
                ).setParameter("id", issue.id).resultList
            }
            ResourceType.BOARD_POST.resource -> {
                val posting = postings.findByProjectIdAndNumber(projectId, number) ?: return result
                postAuthorLoginId = posting.authorLoginId
                commentAuthors = entityManager.createQuery(
                    "select c.authorLoginId from PostingComment c where c.posting.id = :id order by c.createdDate, c.id", String::class.java
                ).setParameter("id", posting.id).resultList
            }
            else -> return result
        }
        // 댓글을 쓴 순서대로 넣되 이미 있으면 뒤로 옮기고, 마지막에 뒤집어 최근 작성자가 먼저 오게 한다.
        val commenters = mutableListOf<User?>()
        for (loginId in commentAuthors) {
            val user = loginId?.let { users.findByLoginIdIgnoreCase(it) }
            commenters.remove(user)
            commenters += user
        }
        commenters.reversed().forEach { if (it != null) result += it }
        users.findByLoginIdIgnoreCase(postAuthorLoginId.orEmpty())?.let { if (it !in result) result += it }
        return result
    }

    private fun projectMembers(project: Project): List<User> =
        entityManager.createQuery("select pu.user from ProjectUser pu where pu.project.id = :id order by pu.id", User::class.java)
            .setParameter("id", project.id).resultList

    private fun groupMembers(project: Project): List<User> {
        val organization = project.organization ?: return emptyList()
        return entityManager.createQuery("select ou.user from OrganizationUser ou where ou.organization.id = :id order by ou.id", User::class.java)
            .setParameter("id", organization.id).resultList
    }

    /** 프로젝트에 이슈, 게시글, 풀 리퀘스트를 쓴 사람과 프로젝트를 구독한 사람이다. */
    private fun authorsAndWatchers(project: Project): List<User> {
        val ids = linkedSetOf<Long>()
        listOf(
            "select distinct i.authorId from Issue i where i.project.id = :id",
            "select distinct p.authorId from Posting p where p.project.id = :id",
            "select distinct r.contributor.id from PullRequest r where r.toProject.id = :id"
        ).forEach { jpql ->
            entityManager.createQuery(jpql, java.lang.Long::class.java).setParameter("id", project.id).resultList.forEach { it?.let { id -> ids += id.toLong() } }
        }
        entityManager.createQuery(
            "select distinct w.user.id from Watch w where w.resourceType = :type and w.resourceId = :id", java.lang.Long::class.java
        ).setParameter("type", ResourceType.PROJECT).setParameter("id", project.id.toString()).resultList.forEach { it?.let { id -> ids += id.toLong() } }
        return ids.sorted().mapNotNull { users.findById(it).orElse(null) }
    }

    private fun sharers(project: Project, number: Long, resourceType: String): List<User> {
        if (resourceType != ResourceType.ISSUE_POST.resource) return emptyList()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return emptyList()
        return entityManager.createQuery("select s.user from IssueSharer s where s.issue.id = :id order by s.id", User::class.java)
            .setParameter("id", issue.id).resultList
    }

    /** 활성 사용자를 이름, 아이디, 영문 이름, 이메일에서 찾는다. 최근 가입 순으로 한 쪽(30명)만이다 (v1.6 `User.findUsers(0, query, ACTIVE)`). */
    private fun searchActive(query: String): List<User> =
        entityManager.createQuery(
            "select u from User u where u.id <> :manager and u.loginId <> 'anonymous' and u.state = :state and (" +
                "lower(u.loginId) like lower(concat('%', :q, '%')) or lower(u.name) like lower(concat('%', :q, '%')) or " +
                "lower(u.englishName) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%'))) " +
                "order by u.createdDate desc, u.id desc", User::class.java
        ).setParameter("manager", SITE_MANAGER_ID).setParameter("state", UserState.ACTIVE).setParameter("q", query)
            .setMaxResults(USER_COUNT_PER_PAGE).resultList

    private companion object {
        const val SITE_MANAGER_ID = 1L
        const val USER_COUNT_PER_PAGE = 30
    }
}
