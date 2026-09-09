package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.notification.UserProjectNotification
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import org.springframework.ui.Model

import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import org.springframework.transaction.annotation.Transactional

@Controller
class WatchController(
    private val watchService: WatchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User {
        if (authentication == null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.")
        }
        return userRepository.findByLoginId(authentication.name).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.")
        }
    }

    private fun checkWatchPermission(user: User, resourceType: ResourceType, resourceId: String) {
        val project = when (resourceType) {
            ResourceType.PROJECT -> {
                projectRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.") }
            }
            ResourceType.ISSUE_POST -> {
                val issue = issueRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "이슈를 찾을 수 없습니다.") }
                issue.project
            }
            ResourceType.BOARD_POST -> {
                val posting = postingRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.") }
                posting.project
            }
            ResourceType.PULL_REQUEST -> {
                val pullRequest = pullRequestRepository.findById(resourceId.toLongOrNull() ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다."))
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Pull Request를 찾을 수 없습니다.") }
                pullRequest.toProject
            }
            ResourceType.COMMIT -> {
                // yona Commit.asResource(project) 대응 — resourceId는 "{project.id}:{commitId}" 합성 키
                // (CodeReviewServiceImpl.getCommitWatchers()가 알림 수신자 계산에 이미 쓰는 것과 동일한 포맷).
                val projectId = resourceId.substringBefore(':', missingDelimiterValue = "").toLongOrNull()
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 리소스 ID입니다.")
                projectRepository.findById(projectId)
                    .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.") }
            }
            else -> {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 리소스 타입입니다.")
            }
        }

        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
    }


    @PostMapping("/watch")
    @ResponseBody
    fun watchResource(
        @RequestParam("resource.type") resourceTypeStr: String,
        @RequestParam("resource.id") resourceId: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val resourceType = ResourceType.valueOf(resourceTypeStr)
        checkWatchPermission(user, resourceType, resourceId)
        watchService.watch(user, resourceType, resourceId)
        return ResponseEntity.ok().build()
    }

    @RequestMapping(value = ["/unwatch"], method = [RequestMethod.GET, RequestMethod.POST])
    fun unwatchResource(
        @RequestParam("resource.type") resourceTypeStr: String,
        @RequestParam("resource.id") resourceId: String,
        @RequestHeader(value = "Referer", required = false) referer: String?,
        authentication: Authentication?
    ): String {
        val user = getLoginUser(authentication)
        val resourceType = ResourceType.valueOf(resourceTypeStr)
        checkWatchPermission(user, resourceType, resourceId)
        watchService.unwatch(user, resourceType, resourceId)
        return "redirect:${referer ?: "/"}"
    }

    @PostMapping("/{owner}/{projectName}/watch")
    @ResponseBody
    fun watchProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }
        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
        watchService.watch(user, ResourceType.PROJECT, project.id.toString())
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{owner}/{projectName}/unwatch")
    @ResponseBody
    @Transactional
    fun unwatchProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }
        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }
        watchService.unwatch(user, ResourceType.PROJECT, project.id.toString())
        userProjectNotificationRepository.deleteByUserAndProject(user, project)
        return ResponseEntity.ok().build()
    }

    @RequestMapping(value = ["/watch/toggle/{projectId}/{notificationType}", "/noti/toggle/{projectId}/{notificationType}"], method = [RequestMethod.GET, RequestMethod.POST])
    @ResponseBody
    fun toggleProjectNotification(
        @PathVariable projectId: Long,
        @PathVariable notificationType: String,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val user = getLoginUser(authentication)
        val project = projectRepository.findById(projectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        if (!accessControl.isAllowed(user, project, Operation.WATCH)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다.")
        }

        if (!watchService.isWatching(user, ResourceType.PROJECT, projectId.toString())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "프로젝트를 감시하고 있지 않습니다.")
        }

        val notiType = try {
            EventType.valueOf(notificationType)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 알림 타입입니다.")
        }

        val isNotifiedByDefault = isNotifiedByDefault(notiType)
        val existing = userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user, project, notiType)
        
        if (existing == null) {
            val newNotification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = notiType,
                allowed = !isNotifiedByDefault
            )
            userProjectNotificationRepository.save(newNotification)
        } else {
            existing.toggle()
            if (existing.allowed == isNotifiedByDefault(notiType)) {
                userProjectNotificationRepository.delete(existing)
            } else {
                userProjectNotificationRepository.save(existing)
            }
        }

        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    private fun isNotifiedByDefault(eventType: EventType): Boolean {
        return eventType != EventType.NEW_COMMENT
    }

    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/watchers")
    @ResponseBody
    fun getWatchers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable number: Long,
        @RequestParam("type") type: String
    ): ResponseEntity<WatchersResponse> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        // yona AbstractPosting.getWatchers()/Issue.getWatchers()의 Watch.findActualWatchers()
        // (작성자/담당자/투표자 + 명시적 Watch row + 프로젝트 감시자 합산, 읽기 권한 없는 사용자
        // 필터링) 대응. 명시적 Watch row만 반환하던 것을 watchService.findActualWatchers()
        // 재사용으로 교체한다.
        val watchers: Set<User> = when (type.lowercase()) {
            "issues" -> {
                val issue = issueRepository.findByProjectAndNumber(project, number)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "이슈를 찾을 수 없습니다.")
                val baseWatchers = mutableSetOf<User>()
                issue.assignee?.user?.let { baseWatchers.add(it) }
                baseWatchers.addAll(issue.voters)
                issue.authorId?.let { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }
                watchService.findActualWatchers(baseWatchers, ResourceType.ISSUE_POST, issue.id.toString(), project.id)
            }
            "posts" -> {
                val posting = postingRepository.findByProjectAndNumber(project, number)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
                val baseWatchers = mutableSetOf<User>()
                posting.authorId?.let { authorId -> userRepository.findById(authorId).ifPresent { baseWatchers.add(it) } }
                watchService.findActualWatchers(baseWatchers, ResourceType.BOARD_POST, posting.id.toString(), project.id)
            }
            else -> emptySet()
        }

        // yona WatcherApi.java의 LIMIT=100 대응.
        val limited = watchers.take(100)
        val watcherDtos = limited.map {
            WatcherDto(name = it.name, url = "/user/${it.loginId}")
        }

        return ResponseEntity.ok(
            WatchersResponse(
                totalWatchers = watchers.size,
                watchersInList = limited.size,
                watchers = watcherDtos
            )
        )
    }

    @GetMapping("/{owner}/{projectName}/watchers")
    fun watchers(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        val watchers = watchService.findWatchers(ResourceType.PROJECT, project.id.toString())
        model.addAttribute("project", project)
        model.addAttribute("watchers", watchers)
        return "project/watchers"
    }

    data class WatchersResponse(
        val totalWatchers: Int,
        val watchersInList: Int,
        val watchers: List<WatcherDto>
    )

    data class WatcherDto(
        val name: String,
        val url: String
    )
}

