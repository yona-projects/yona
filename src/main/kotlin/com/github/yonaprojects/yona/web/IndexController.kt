package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant

@Controller
class IndexController(
    private val notificationEventRepository: NotificationEventRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val organizationRepository: OrganizationRepository,
    private val milestoneRepository: MilestoneRepository,
    private val userSettingRepository: UserSettingRepository
) {

    data class NotificationViewDto(
        val id: Long,
        val title: String,
        val created: Instant?,
        val eventType: EventType,
        val newValue: String?,
        val message: String,
        val senderLoginId: String?,
        val senderName: String?,
        val senderAvatarUrl: String?,
        val url: String?,
        val iconClass: String
    )

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    // yona Application.java index()의 loginDefaultPage 리다이렉트 대응
    @GetMapping("/")
    fun index(authentication: Authentication?, model: Model): String {
        val user = getLoginUser(authentication)
        if (user != null) {
            val loginDefaultPage = userSettingRepository.findByUserId(user.id!!).orElse(null)?.loginDefaultPage
            if (!loginDefaultPage.isNullOrBlank()) {
                return "redirect:$loginDefaultPage"
            }

            val pageable = PageRequest.of(0, 20)
            val notificationPage = notificationEventRepository.findByReceiver(user, pageable)
            val mappedList = mapNotificationsToView(notificationPage.content)
            model.addAttribute("notifications", mappedList)
            model.addAttribute("currentUser", user)
        }
        return "index"
    }

    @GetMapping("/notifications")
    fun notifications(authentication: Authentication?, model: Model): String {
        val user = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        val pageable = PageRequest.of(0, 20)
        val notificationPage = notificationEventRepository.findByReceiver(user, pageable)
        val mappedList = mapNotificationsToView(notificationPage.content)
        model.addAttribute("notifications", mappedList)
        model.addAttribute("currentUser", user)
        return "index/notifications"
    }

    @GetMapping("/_notifications")
    fun partialNotifications(
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(defaultValue = "20") size: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        // yona actions/AnonymousCheckAction.java 대응 — legacy는 401 상태를 별도로 보여주는 페이지가
        // 없고 비로그인 사용자를 항상 로그인 폼으로 302 리다이렉트한다(Secured.onUnauthorized()도 동일한
        // redirect(loginFormUrl) 패턴).
        val user = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        
        val pageIndex = if (size > 0) from / size else 0
        val pageSize = if (size > 0) size else 20
        val pageable = PageRequest.of(pageIndex, pageSize)
        
        val notificationPage = notificationEventRepository.findByReceiver(user, pageable)
        val mappedList = mapNotificationsToView(notificationPage.content)
        model.addAttribute("notifications", mappedList)
        return "index/partial_notifications"
    }

    private fun mapNotificationsToView(events: List<NotificationEvent>): List<NotificationViewDto> {
        if (events.isEmpty()) return emptyList()

        val issueIds = events.filter { it.resourceType == ResourceType.ISSUE_POST }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val postingIds = events.filter { it.resourceType == ResourceType.BOARD_POST }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val prIds = events.filter { it.resourceType == ResourceType.PULL_REQUEST }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val projectIds = events.filter { it.resourceType == ResourceType.PROJECT }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val organizationIds = events.filter { it.resourceType == ResourceType.ORGANIZATION }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val milestoneIds = events.filter { it.resourceType == ResourceType.MILESTONE }.mapNotNull { it.resourceId.toLongOrNull() }.toSet()
        val senderIds = events.mapNotNull { it.senderId }.toSet()

        val issues = if (issueIds.isNotEmpty()) issueRepository.findAllById(issueIds).associateBy { it.id } else emptyMap()
        val postings = if (postingIds.isNotEmpty()) postingRepository.findAllById(postingIds).associateBy { it.id } else emptyMap()
        val prs = if (prIds.isNotEmpty()) pullRequestRepository.findAllById(prIds).associateBy { it.id } else emptyMap()
        val projects = if (projectIds.isNotEmpty()) projectRepository.findAllById(projectIds).associateBy { it.id } else emptyMap()
        val orgs = if (organizationIds.isNotEmpty()) organizationRepository.findAllById(organizationIds).associateBy { it.id } else emptyMap()
        val milestones = if (milestoneIds.isNotEmpty()) milestoneRepository.findAllById(milestoneIds).associateBy { it.id } else emptyMap()
        val senders = if (senderIds.isNotEmpty()) userRepository.findAllById(senderIds).associateBy { it.id } else emptyMap()

        return events.map { event ->
            val resId = event.resourceId.toLongOrNull()
            var url: String? = null
            
            when (event.eventType) {
                EventType.MEMBER_ENROLL_REQUEST, EventType.MEMBER_ENROLL_ACCEPT -> {
                    val proj = projects[resId]
                    if (proj != null) {
                        url = "/projects/${proj.owner}/${proj.name}/members"
                    }
                }
                EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> {
                    val org = orgs[resId]
                    if (org != null) {
                        url = "/organizations/${org.name}/members"
                    }
                }
                EventType.NEW_COMMIT -> {
                    val proj = projects[resId]
                    if (proj != null) {
                        url = "/${proj.owner}/${proj.name}/commits"
                    }
                }
                else -> {
                    when (event.resourceType) {
                        ResourceType.ISSUE_POST -> {
                            val issue = issues[resId]
                            if (issue != null) {
                                url = "/${issue.project.owner}/${issue.project.name}/issue/${issue.number}"
                            }
                        }
                        ResourceType.BOARD_POST -> {
                            val post = postings[resId]
                            if (post != null) {
                                url = "/${post.project.owner}/${post.project.name}/post/${post.number}"
                            }
                        }
                        ResourceType.PULL_REQUEST -> {
                            val pr = prs[resId]
                            if (pr != null) {
                                url = "/${pr.toProject.owner}/${pr.toProject.name}/pullRequest/${pr.number}"
                            }
                        }
                        ResourceType.PROJECT -> {
                            val proj = projects[resId]
                            if (proj != null) {
                                url = "/${proj.owner}/${proj.name}"
                            }
                        }
                        ResourceType.ORGANIZATION -> {
                            val org = orgs[resId]
                            if (org != null) {
                                url = "/organizations/${org.name}"
                            }
                        }
                        ResourceType.MILESTONE -> {
                            val ms = milestones[resId]
                            if (ms != null) {
                                url = "/${ms.project.owner}/${ms.project.name}/milestone/${ms.id}"
                            }
                        }
                        else -> {
                            url = null
                        }
                    }
                }
            }

            val sender = senders[event.senderId]
            
            var msg = event.newValue ?: ""
            if (event.eventType == EventType.ISSUE_STATE_CHANGED) {
                msg = if (event.newValue == "closed") "이슈가 닫혔습니다." else "이슈가 다시 열렸습니다."
            } else if (event.eventType == EventType.PULL_REQUEST_STATE_CHANGED) {
                msg = if (event.newValue == "closed") "풀 리퀘스트가 닫혔습니다." else if (event.newValue == "merged") "풀 리퀘스트가 병합되었습니다." else "풀 리퀘스트가 다시 열렸습니다."
            } else if (event.eventType == EventType.MEMBER_ENROLL_REQUEST) {
                msg = "프로젝트 가입 신청이 등록되었습니다."
            } else if (event.eventType == EventType.MEMBER_ENROLL_ACCEPT) {
                msg = "프로젝트 가입 신청이 승인되었습니다."
            }

            val iconClass = when (event.eventType) {
                EventType.NEW_COMMENT, EventType.NEW_REVIEW_COMMENT, EventType.REVIEW_THREAD_STATE_CHANGED -> "comment2"
                EventType.NEW_ISSUE, EventType.ISSUE_STATE_CHANGED -> {
                    if (event.newValue == "closed") "list-alt closed" else "list-alt"
                }
                EventType.ISSUE_ASSIGNEE_CHANGED -> "friends changed"
                EventType.NEW_POSTING -> "edit2"
                EventType.NEW_PULL_REQUEST, EventType.PULL_REQUEST_COMMIT_CHANGED, EventType.PULL_REQUEST_STATE_CHANGED -> {
                    if (event.newValue == "closed") "merge closed"
                    else if (event.newValue == "merged") "merge merged"
                    else "merge"
                }
                EventType.MEMBER_ENROLL_REQUEST -> {
                    if (event.newValue == "ACCEPT") "addfriend closed"
                    else if (event.newValue == "CANCEL") "addfriend rejected"
                    else "addfriend"
                }
                EventType.NEW_COMMIT -> "push"
                EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "preview changed"
                EventType.ISSUE_BODY_CHANGED -> "ellipsis-horizontal"
                EventType.COMMENT_UPDATED -> "ellipsis-horizontal"
                else -> "megaphone"
            }

            NotificationViewDto(
                id = event.id ?: 0L,
                title = event.title,
                created = event.created,
                eventType = event.eventType,
                newValue = event.newValue,
                message = msg,
                senderLoginId = sender?.loginId,
                senderName = sender?.name,
                senderAvatarUrl = sender?.avatarUrl,
                url = url,
                iconClass = iconClass
            )
        }
    }
}
