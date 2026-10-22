package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.MentionListService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.User
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.organization.OrganizationViewFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 편집기의 `@` 멘션 후보이다. v1.6 `ProjectApp.mentionList`(`app/controllers/ProjectApp.java:383-497`)에 해당한다.
 *
 * `mentionType=user`이면 사용자 후보, `issue`이면 이슈 후보를 `{"result": [...]}`로 돌려준다. 그 밖의 종류는 빈 객체이다.
 * v1.6은 읽기 권한을 확인하지 않아 비공개 프로젝트의 구성원 목록이 누구에게나 보였다. 편집기는 프로젝트를 읽을 수 있어야
 * 쓰는 화면이므로 읽기 권한을 요구한다([decisions.md] A-35).
 */
@Controller
@AnonymousCheck
class MentionListController(
    private val projectService: ProjectService,
    private val mentionService: MentionListService,
    private val views: ProjectViewFactory,
    private val organizationViews: OrganizationViewFactory,
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider,
    private val history: dev.yona.repository.CommitHistoryService,
    private val pullRequests: dev.yona.domain.pullrequest.PullRequestRepository
) {

    @GetMapping("/{user}/{project}/mentionList")
    @IsAllowed(Operation.READ)
    fun mentionList(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @RequestParam(name = "number", defaultValue = "-1") number: Long,
        @RequestParam(name = "resourceType", defaultValue = "") resourceType: String,
        @RequestParam(name = "query", defaultValue = "") query: String,
        @RequestParam(name = "mentionType", defaultValue = "") mentionType: String
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val result = linkedMapOf<String, List<Map<String, String>>>()

        if (mentionType.equals("user", ignoreCase = true)) {
            val viewer = currentUserProvider.get().user
            val candidates = mentionService.users(project, number, resourceType, query, viewer)
            result["result"] = userList(project, candidates, viewer)
        }
        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = mentionService.issues(project, query, ISSUE_MENTION_SHOW_LIMIT).map {
                mapOf("name" to "${it.number}${it.title}", "issueNo" to it.number.toString(), "title" to it.title.orEmpty())
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT).contentType(MediaType.APPLICATION_JSON).body(result)
    }

    /**
     * 커밋 diff 화면(코드 리뷰 댓글 편집기)의 `@` 멘션 후보이다. v1.6 `ProjectApp.mentionListAtCommitDiff`(`app/controllers/ProjectApp.java:500-538`)에 해당한다.
     * `pullRequestId`를 주면 그 풀 리퀘스트의 보내는 프로젝트에서 커밋을 찾는다. 커밋이 없으면 v1.6은 `500`이었고 v2는 `404`이다([decisions.md] A-72).
     */
    @GetMapping("/{user}/{project}/mentionListAtCommitDiff")
    @IsAllowed(Operation.READ)
    fun mentionListAtCommitDiff(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @RequestParam(name = "commitId", defaultValue = "") commitId: String,
        @RequestParam(name = "pullRequestId", defaultValue = "-1") pullRequestId: Long,
        @RequestParam(name = "query", defaultValue = "") query: String,
        @RequestParam(name = "mentionType", defaultValue = "") mentionType: String
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val fromProject = (if (pullRequestId != -1L) pullRequests.findById(pullRequestId).orElse(null)?.fromProject else null) ?: project
        val commit = history.commit(fromProject, commitId) ?: return ErrorViews.notFound()
        val result = linkedMapOf<String, List<Map<String, String>>>()
        if (mentionType.equals("user", ignoreCase = true)) {
            val viewer = currentUserProvider.get().user
            result["result"] = userList(project, mentionService.usersAtCommitDiff(project, fromProject, commit.authorEmail, commit.id, query, viewer), viewer)
        }
        if (mentionType.equals("issue", ignoreCase = true)) {
            result["result"] = mentionService.issues(project, query, ISSUE_MENTION_SHOW_LIMIT).map {
                mapOf("name" to "${it.number}${it.title}", "issueNo" to it.number.toString(), "title" to it.title.orEmpty())
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT).contentType(MediaType.APPLICATION_JSON).body(result)
    }

    /** 사용자 후보에 프로젝트 전체와 조직 전체 항목을 더한다. 열 명을 넘으면 열 번째 근처에 끼워 넣는다(v1.6 동작). */
    private fun userList(project: Project, candidates: List<User>, viewer: User?): List<Map<String, String>> {
        val list = candidates.filter { !it.loginId.isNullOrEmpty() && it.loginId != ADMIN_LOGIN_ID }.map {
            val displayName = UserDisplay.of(it, viewer?.lang)
            mapOf(
                "loginid" to it.loginId.orEmpty(), "searchText" to it.name.orEmpty() + displayName + it.loginId.orEmpty(),
                "name" to displayName, "image" to avatars.avatarUrl(it)
            )
        }.toMutableList()

        val organization = project.organization
        val projectEntry = mapOf(
            "loginid" to "${project.owner}/${project.name}", "username" to project.name.orEmpty(), "name" to "@project all:",
            "searchText" to "${project.owner}/${project.name}/project/member/all", "image" to views.of(project).logoUrl
        )
        if (list.size > 9) list.add(if (organization != null) 8 else 9, projectEntry) else list.add(projectEntry)

        if (organization != null) {
            val orgEntry = mapOf(
                "loginid" to organization.name.orEmpty(), "username" to organization.name.orEmpty(), "name" to "@group all: ",
                "searchText" to "${organization.name}/group/org/member/all", "image" to organizationViews.of(organization).logoUrl
            )
            // v1.6 구현은 열 명을 넘으면 열 번째에 끼우고 맨 끝에도 한 번 더 붙인다.
            if (list.size > 9) list.add(9, orgEntry)
            list.add(orgEntry)
        }
        return list
    }

    private companion object {
        const val ISSUE_MENTION_SHOW_LIMIT = 20
        const val ADMIN_LOGIN_ID = "admin"
    }
}
