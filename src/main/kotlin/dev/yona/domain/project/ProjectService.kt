package dev.yona.domain.project

import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import java.net.URLDecoder

/**
 * 프로젝트의 조회와 소속 판정, 프로젝트 메뉴에 보이는 개수를 담당한다.
 * v1.6 `Project`의 정적 메서드(`findByOwnerAndProjectName`, `findByPreviousPlaceOf`, `hasMember`)와
 * 각 모델의 개수 조회(`Issue.countIssues` 등)에 해당한다.
 *
 * v1.6은 프로젝트 조회 결과를 `CacheStore.projectMap`에 캐시했다. v2는 조회마다 DB를 읽는다. 캐시는 성능 문제가
 * 확인되면 추가한다.
 */
@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val siteAdmins: SiteAdminRepository,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val pullRequests: PullRequestRepository,
    private val threads: CommentThreadRepository
) {

    /**
     * 소유자와 이름으로 프로젝트를 찾는다. 대소문자를 구별하지 않고 URL 인코딩된 값을 풀어서 비교한다.
     * 현재 이름으로 찾지 못하면 이름이나 소유자가 바뀌기 전의 위치로 찾는다.
     */
    fun findByOwnerAndProjectName(owner: String, projectName: String): Project? {
        val decodedOwner = decodeUrlString(owner)
        val decodedName = decodeUrlString(projectName)
        return projects.findByOwnerIgnoreCaseAndNameIgnoreCase(decodedOwner, decodedName)
            ?: findByPreviousPlaceOf(decodedOwner, decodedName)
    }

    /**
     * 프로젝트가 이름을 바꾸거나 다른 소유자에게 옮겨졌을 때, 바뀌기 전의 위치로 프로젝트를 찾는다.
     * 같은 이전 위치를 가진 프로젝트가 여럿이면 가장 최근에 바뀐 것을 고른다.
     */
    fun findByPreviousPlaceOf(previousOwnerLoginId: String, previousName: String): Project? =
        projects.findByPreviousOwnerLoginIdIgnoreCaseAndPreviousNameIgnoreCaseOrderByPreviousNameChangedTimeDesc(
            previousOwnerLoginId, previousName
        ).firstOrNull()

    /**
     * [loginId]가 소유한 프로젝트 중 [projectName]과 겹치지 않는 이름을 정한다. 겹치지 않으면 그대로, 겹치면 `이름-1`, `이름-2`처럼
     * 번호를 붙인다 (v1.6 `Project.newProjectName`).
     */
    fun newProjectName(loginId: String, projectName: String): String {
        if (findByOwnerAndProjectName(loginId, projectName) == null) return projectName
        var i = 1
        while (true) {
            val candidate = "$projectName-$i"
            if (findByOwnerAndProjectName(loginId, candidate) == null) return candidate
            i++
        }
    }

    /**
     * 사용자가 프로젝트의 구성원이면 `true`이다. 역할과 상관없이 `project_user`에 행이 있거나 매니저이거나
     * 사이트 관리자이면 구성원이다 (v1.6 `Project.hasMember`).
     */
    fun hasMember(project: Project, user: User?): Boolean {
        val userId = user?.id ?: return false
        val projectId = requireNotNull(project.id)
        return projectUsers.existsByUserIdAndProjectId(userId, projectId) ||
            projectUsers.existsByUserIdAndProjectIdAndRoleId(userId, projectId, RoleType.MANAGER.id) ||
            siteAdmins.existsByAdminId(userId)
    }

    /**
     * 프로젝트의 최상위 이슈 개수이다. [State.ALL]이면 초안을 뺀 전체를 센다 (v1.6 `Issue.countIssues`).
     */
    fun countIssues(project: Project, state: State): Long {
        val projectId = requireNotNull(project.id)
        return if (state == State.ALL) {
            issues.countByProjectIdAndParentIsNullAndStateNot(projectId, State.DRAFT)
        } else {
            issues.countByProjectIdAndParentIsNullAndState(projectId, state)
        }
    }

    /** 프로젝트의 게시글 개수이다 (v1.6 `Posting.countPostings`). */
    fun countPostings(project: Project): Long = postings.countByProjectId(requireNotNull(project.id))

    /** 이 프로젝트가 받는 열린 풀 리퀘스트 개수이다 (v1.6 `PullRequest.countOpenedPullRequests`). */
    fun countOpenPullRequests(project: Project): Long =
        pullRequests.countByToProjectIdAndState(requireNotNull(project.id), State.OPEN)

    /** 프로젝트의 열린 리뷰 스레드 개수이다 (v1.6 `CommentThread.countReviewsBy(projectId, null)`). */
    fun countOpenReviewThreads(project: Project): Long =
        threads.countByProjectIdAndState(requireNotNull(project.id), ThreadState.OPEN)

    private fun decodeUrlString(value: String): String = try {
        URLDecoder.decode(value, Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        value // v1.6 `HttpUtil.decodeUrlString`은 해석하지 못하면 원래 값을 쓴다.
    }
}
