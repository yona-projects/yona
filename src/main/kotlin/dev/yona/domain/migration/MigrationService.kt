package dev.yona.domain.migration

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.web.AttachmentListing
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * 이전(Migration) 화면이 쓰는 데이터를 만든다. v1.6 `MigrationApp`(`app/controllers/MigrationApp.java:93-450`)에 해당한다.
 *
 * Yona의 프로젝트, 마일스톤, 이슈, 게시글을 GitHub로 옮기려고 브라우저의 스크립트가 받아 가는 JSON이다. 본문은 앞에 원 작성자 안내를 붙이고
 * 첨부 목록을 뒤에 붙인다. 응답의 키와 시각 모양은 GitHub API와 맞춘 v1.6 그대로이다(게시글도 `issue` 키를 쓴다).
 *
 * 내보낼 수 있는 사용자는 출발 프로젝트 목록과 같은 기준이다(D-23): 그 프로젝트의 매니저, 소속 조직의 관리자, 사이트 매니저.
 */
@Service
@Transactional(readOnly = true)
class MigrationService(
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val siteAdmins: SiteAdminRepository,
    private val assignees: AssigneeRepository,
    private val issues: IssueRepository,
    private val issueComments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val milestones: MilestoneRepository,
    private val labels: IssueLabelRepository,
    private val attachments: AttachmentListing,
    private val entityManager: EntityManager
) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    private fun time(date: Date?): String = LocalDateTime.ofInstant(requireNotNull(date).toInstant(), ZoneId.systemDefault()).format(formatter)

    /** 사용자가 이 프로젝트의 설정을 바꿀 수 있는지(매니저이거나 사이트 매니저) 확인한다 (v1.6 `ProjectUser.isAllowedToSettings`). */
    private fun isAllowedToSettings(user: User, project: Project): Boolean {
        val userId = requireNotNull(user.id)
        return siteAdmins.existsByAdminId(userId) || projectUsers.existsByUserIdAndProjectIdAndRoleId(userId, requireNotNull(project.id), RoleType.MANAGER.id)
    }

    /** 사용자가 이 프로젝트를 내보낼 수 있는지 확인한다(D-23). */
    fun canExport(user: User, project: Project): Boolean {
        if (isAllowedToSettings(user, project)) return true
        val organizationId = project.organization?.id ?: return false
        return organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(organizationId, requireNotNull(user.id), RoleType.ORG_ADMIN.id)
    }

    /**
     * 출발 프로젝트 목록이다. 내가 `ORG_ADMIN`인 조직의 모든 프로젝트와, 내가 구성원인 프로젝트 중 설정을 바꿀 수 있는 것을 합쳐
     * 소유자와 이름 순으로 정렬한다. 사이트 매니저도 자신이 구성원인 프로젝트만 담긴다.
     */
    fun sourceProjects(user: User): List<Map<String, Any?>> {
        val userId = requireNotNull(user.id)
        val found = LinkedHashMap<Long, Project>()
        organizationUsers.findByUserIdAndRoleId(userId, RoleType.ORG_ADMIN.id).forEach { ou ->
            projects.findByOrganizationId(requireNotNull(ou.organization?.id)).forEach { found.putIfAbsent(requireNotNull(it.id), it) }
        }
        projects.findProjectsByMember(userId).filter { isAllowedToSettings(user, it) }.forEach { found.putIfAbsent(requireNotNull(it.id), it) }
        return found.values.sortedWith(compareBy<Project> { it.owner }.thenBy { it.name }).map { project ->
            linkedMapOf(
                "owner" to project.owner, "projectName" to project.name, "private" to project.isPrivate(),
                "members" to projectUsers.findMembers(requireNotNull(project.id), RoleType.SITEMANAGER.id).size, "full_name" to "${project.owner}/${project.name}"
            )
        }
    }

    /** 프로젝트 요약이다: 담당자 목록과 구성원, 이슈, 게시글, 마일스톤 수. */
    fun summary(project: Project): Map<String, Any?> {
        val projectId = requireNotNull(project.id)
        return linkedMapOf(
            "owner" to project.owner, "projectName" to project.name, "full_name" to "${project.owner}/${project.name}",
            "assignees" to assignees.findByProjectIdOrderByIdAsc(projectId).map { a ->
                linkedMapOf("name" to a.user?.name, "login" to a.user?.loginId, "email" to a.user?.email)
            },
            "memberCount" to projectUsers.findMembers(projectId, RoleType.SITEMANAGER.id).size,
            "issueCount" to issues.findByProjectIdOrderByIdAsc(projectId).size,
            "postCount" to postings.countByProjectId(projectId),
            "milestoneCount" to milestones.findByProjectId(projectId, Sort.by("id")).size
        )
    }

    /** 라벨을 번호를 키로 담는다(분류 이름, 라벨 이름 순). */
    fun labels(project: Project): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        labels.findByProjectIdOrderByCategoryNameAscNameAsc(requireNotNull(project.id)).forEach { label ->
            map[label.id.toString()] = linkedMapOf("id" to label.id, "name" to label.name, "categoryId" to label.category?.id, "categoryName" to label.category?.name)
        }
        return linkedMapOf("labels" to map)
    }

    /** 이슈와 라벨의 짝이다. */
    fun issueLabelPairs(project: Project): Map<String, Any?> {
        val rows = entityManager.createNativeQuery(
            "select i.id, iil.issue_label_id from issue i, issue_issue_label iil where i.project_id = :project and i.id = iil.issue_id order by i.id, iil.issue_label_id"
        ).setParameter("project", project.id).resultList
        return linkedMapOf("issueLabelPairs" to rows.map { row ->
            val columns = row as Array<*>
            linkedMapOf("issueId" to (columns[0] as Number).toLong(), "issueLabelId" to (columns[1] as Number).toLong())
        })
    }

    /** 마일스톤이다. 마감일이 있으면 `due_on`을 더한다. */
    fun milestones(project: Project): Map<String, Any?> = linkedMapOf(
        "milestones" to milestones.findByProjectId(requireNotNull(project.id), Sort.by("id")).map { linkedMapOf("milestone" to milestoneNode(it)) }
    )

    private fun milestoneNode(m: Milestone): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>("id" to m.id, "title" to m.title, "state" to m.state?.state, "description" to m.contents)
        m.dueDate?.let { node["due_on"] = time(it) }
        return node
    }

    /**
     * 이슈와 댓글이다.
     *
     * @param withWikiCommit 상대 링크와 첨부를 위키 커밋 경로로 바꾼다
     */
    fun issues(project: Project, withWikiCommit: Boolean): Map<String, Any?> = linkedMapOf(
        "issues" to issues.findByProjectIdOrderByIdAsc(requireNotNull(project.id)).map { issue -> issueNode(project, issue, withWikiCommit) }
    )

    /** 게시글과 댓글이다. GitHub API와 맞추려고 게시글도 `issue` 키에 담는다. */
    fun posts(project: Project, withWikiCommit: Boolean): Map<String, Any?> = linkedMapOf(
        "issues" to postings.findByProjectIdOrderByIdAsc(requireNotNull(project.id)).map { postNode(project, it, withWikiCommit) }
    )

    private fun issueNode(project: Project, issue: Issue, wiki: Boolean): Map<String, Any?> {
        val link = "/${project.owner}/${project.name}/issue/${issue.number}"
        val node = linkedMapOf<String, Any?>("id" to issue.id, "title" to issue.title)
        node["body"] = body(issue.body, issue.authorLoginId, issue.authorName, "이슈", link, ResourceType.ISSUE_POST, requireNotNull(issue.id), wiki)
        node["created_at"] = time(issue.createdDate)
        issue.assignee?.let { node["assignee"] = it.user?.loginId }
        issue.milestone?.let { node["milestone"] = it.title; node["milestoneId"] = it.id }
        node["closed"] = issue.state == State.CLOSED
        val comments = issueComments.findByIssueIdOrderByCreatedDateAscIdAsc(requireNotNull(issue.id)).map { c ->
            linkedMapOf<String, Any?>(
                "created_at" to time(c.createdDate),
                "body" to body(c.contents, c.authorLoginId, c.authorName, "코멘트", "$link#comment-${c.id}", ResourceType.ISSUE_COMMENT, requireNotNull(c.id), wiki)
            )
        }
        return linkedMapOf("issue" to node, "comments" to comments)
    }

    private fun postNode(project: Project, post: Posting, wiki: Boolean): Map<String, Any?> {
        val link = "/${project.owner}/${project.name}/post/${post.number}"
        val node = linkedMapOf<String, Any?>("title" to post.title)
        node["body"] = body(post.body, post.authorLoginId, post.authorName, "게시글", link, ResourceType.BOARD_POST, requireNotNull(post.id), wiki)
        node["created_at"] = time(post.createdDate)
        val comments = postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(requireNotNull(post.id)).map { c ->
            linkedMapOf<String, Any?>(
                "created_at" to time(c.createdDate),
                "body" to body(c.contents, c.authorLoginId, c.authorName, "코멘트", "$link#comment-${c.id}", ResourceType.NONISSUE_COMMENT, requireNotNull(c.id), wiki)
            )
        }
        return linkedMapOf("issue" to node, "comments" to comments)
    }

    /** 본문이다: 원 작성자 안내, 본문(상대 링크 변환), 첨부 목록 순이다. */
    private fun body(text: String?, authorLoginId: String?, authorName: String?, type: String, link: String, container: ResourceType, id: Long, wiki: Boolean): String {
        val converted = if (wiki) relativeLinksToWikiCommitPath(text.orEmpty()) else relativeLinksToAbsolutePath(text.orEmpty())
        val sb = StringBuilder(String.format("@%s (%s) 님이 작성한 [%s](%s)입니다. \n\\---\n\n%s", authorLoginId, authorName, type, link, converted))
        // 읽을 수 없는 첨부가 하나라도 있으면 목록 전체를 붙이지 않는다(v1.6은 예외를 삼킨다).
        val files = attachments.fileList(container, id)?.get("attachments").orEmpty()
        if (files.isNotEmpty()) {
            sb.append("\n\n--- attachments ---")
            files.forEach { file ->
                if (wiki) sb.append(String.format("\n[%s](../wiki/files/%s/%s)", file["name"], file["id"], file["name"]!!.replace("#", "%23")))
                else sb.append(String.format("\n[%s](%s)", file["name"], "/" + file["url"]))
            }
        }
        return sb.toString()
    }

    /** 상대 링크를 서버 주소(`/`)를 붙인 경로로 바꾼다. 서버 주소가 `/`라 결과는 같다 (v1.6 `relativeLinksToAbsolutePath`). */
    private fun relativeLinksToAbsolutePath(text: String): String =
        text.replace(IMG_SRC, "$1/$2$3").replace(MARKDOWN_LINK, "[$1](/$2)")

    /** 상대 이미지 주소는 그대로 두고 마크다운 상대 링크를 위키 파일 경로로 바꾼다 (v1.6 `relativeLinksToWikiCommitPath`). */
    private fun relativeLinksToWikiCommitPath(text: String): String =
        text.replace(IMG_SRC, "$1/$2$3").replace(MARKDOWN_LINK, "[$1](../wiki/$2/$1)")

    private companion object {
        val IMG_SRC = Regex("""(<img src=["'])/(?<link>.*)(["']>)""")
        val MARKDOWN_LINK = Regex("""\[(?<text>[^\]]*)\]\(/(?<link>[^)]*)\)""")
    }
}
