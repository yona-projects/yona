package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/**
 * 풀 리퀘스트 REST API(조회와 쓰기)가 함께 쓰는 대상 찾기와 권한 확인이다(로드맵 X-15, `decisions.md` D-39).
 * 토큰 사용자, 프로젝트, 풀 리퀘스트를 찾고 화면과 같은 사용자 권한(`AccessControl`)을 확인한다. 토큰 범위는 `/-_-api` 필터와 범위 표가 이미 검사했다.
 */
@Component
class PullRequestApiSupport(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val accessControl: AccessControl,
    private val requests: ApiRequests
) {

    /** 이 요청이 가리키는 프로젝트와 풀 리퀘스트, 호출한 사용자이다. 오류이면 [error]가 응답이다. */
    class Target(val user: User? = null, val project: Project? = null, val pullRequest: PullRequest? = null, val error: ResponseEntity<*>? = null)

    /**
     * 대상을 찾는다. 토큰 사용자가 없으면 401, 프로젝트가 없거나 읽을 수 없으면 존재를 숨기려고 403, 풀 리퀘스트 번호가 없으면 404,
     * [operation]을 할 수 없으면 403이다. [number]가 `null`이면 프로젝트까지만 찾는다. 코드 접근을 구성원으로 제한한 프로젝트는 풀 리퀘스트와 코드가 걸린 요청에서만
     * 비구성원을 막으므로([requireCodeAccess]) 이슈나 라벨 같은 조회는 `false`로 부른다.
     */
    fun target(owner: String, projectName: String, number: Long?, request: HttpServletRequest, operation: Operation = Operation.READ, requireCodeAccess: Boolean = true): Target {
        val user = requests.tokenUser(request) ?: return Target(error = requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request"))
        val project = projectService.findByOwnerAndProjectName(owner, projectName)
        if (project == null || !accessControl.isAllowed(user, project.asResource(), Operation.READ) || (requireCodeAccess && project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, user))) {
            return Target(error = requests.message(HttpStatus.FORBIDDEN, "forbidden or not found"))
        }
        if (number == null) return Target(user, project)
        val pullRequest = pullRequestService.findOne(project, number) ?: return Target(error = requests.message(HttpStatus.NOT_FOUND, "no pull request by request"))
        if (!accessControl.isAllowed(user, pullRequest.asResource(), operation)) return Target(error = requests.message(HttpStatus.FORBIDDEN, "forbidden"))
        return Target(user, project, pullRequest)
    }

    fun ok(body: Any): ResponseEntity<Any> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    fun conflict(text: String) = requests.message(HttpStatus.CONFLICT, text)

    fun badRequest(text: String) = requests.message(HttpStatus.BAD_REQUEST, text)
}
