package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WatchServiceImpl(
    private val watchRepository: WatchRepository,
    private val unwatchRepository: UnwatchRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userProjectNotificationRepository: UserProjectNotificationRepository
) : WatchService {

    override fun watch(user: User, resourceType: ResourceType, resourceId: String) {
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (watch == null) {
            watchRepository.save(Watch(user = user, resourceType = resourceType, resourceId = resourceId))
        }
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (unwatch != null) {
            unwatchRepository.delete(unwatch)
        }
    }

    // legacy ResourcePersistAdapter.postDelete()(deleteRelatedWatch/deleteRelatedUnwatch) 대응.
    override fun deleteAll(resourceType: ResourceType, resourceId: String) {
        watchRepository.deleteAll(watchRepository.findByResourceTypeAndResourceId(resourceType, resourceId))
        unwatchRepository.deleteAll(unwatchRepository.findByResourceTypeAndResourceId(resourceType, resourceId))
    }

    override fun unwatch(user: User, resourceType: ResourceType, resourceId: String) {
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (unwatch == null) {
            unwatchRepository.save(Unwatch(user = user, resourceType = resourceType, resourceId = resourceId))
        }
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        if (watch != null) {
            watchRepository.delete(watch)
        }
    }

    override fun isWatching(user: User, resourceType: ResourceType, resourceId: String): Boolean {
        val watch = watchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        val unwatch = unwatchRepository.findByUserAndResourceTypeAndResourceId(user, resourceType, resourceId)
        return watch != null && unwatch == null
    }

    override fun findWatchers(resourceType: ResourceType, resourceId: String): Set<User> {
        return watchRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .map { it.user }
            .toSet()
    }

    override fun findUnwatchers(resourceType: ResourceType, resourceId: String): Set<User> {
        return unwatchRepository.findByResourceTypeAndResourceId(resourceType, resourceId)
            .map { it.user }
            .toSet()
    }

    override fun findActualWatchers(
        baseWatchers: Set<User>,
        resourceType: ResourceType,
        resourceId: String,
        projectId: Long?,
        allowedWatchersOnly: Boolean,
        eventType: EventType?
    ): Set<User> {
        val actualWatchers = mutableSetOf<User>()
        actualWatchers.addAll(baseWatchers)

        // 1. 프로젝트 감시자 추가
        val projectWatchers = if (projectId != null) findWatchers(ResourceType.PROJECT, projectId.toString()) else emptySet()
        actualWatchers.addAll(projectWatchers)

        // 2. 해당 리소스 감시자 추가
        val resourceWatchers = findWatchers(resourceType, resourceId)
        actualWatchers.addAll(resourceWatchers)

        // 3. 해당 리소스 비감시자 제외
        actualWatchers.removeAll(findUnwatchers(resourceType, resourceId))

        // 4. legacy Watch.findActualWatchers()의 allowedWatchersOnly 필터 대응:
        // 이 리소스를 읽을 권한이 없는 감시자는 실제 감시자 목록에서 제외한다.
        if (allowedWatchersOnly) {
            actualWatchers.retainAll { hasReadPermission(it, projectId) }
        }

        // 5. legacy NotificationEvent.filterReceivers()의 UserProjectNotification 뮤트 필터 대응:
        // "프로젝트 감시를 통해서만" 이 사용자가 포함된 경우에만 뮤트 설정을 확인한다.
        // baseWatcher(작성자 등)이거나 리소스를 직접 명시적으로 감시 중이면 뮤트와 무관하게 항상 포함한다.
        if (projectId != null && eventType != null) {
            actualWatchers.retainAll { user ->
                val isOnlyProjectWatcher = user in projectWatchers &&
                    user !in baseWatchers &&
                    user !in resourceWatchers
                !isOnlyProjectWatcher || isNotificationEnabled(user, projectId, eventType)
            }
        }

        return actualWatchers
    }

    private fun hasReadPermission(user: User, projectId: Long?): Boolean {
        if (user.isSiteManager) return true
        // 프로젝트에 속하지 않은 전역 리소스는 yona AccessControl에서도 누구나 읽을 수 있다고 본다.
        if (projectId == null) return true

        val project = projectRepository.findById(projectId).orElse(null) ?: return false
        if (project.projectScope == ProjectScope.PUBLIC) return true
        return projectUserRepository.existsByProjectIdAndUserId(projectId, user.id ?: return false)
    }

    private fun isNotificationEnabled(user: User, projectId: Long, eventType: EventType): Boolean {
        val project = projectRepository.findById(projectId).orElse(null) ?: return true
        val setting = userProjectNotificationRepository.findByUserAndProjectAndNotificationType(user, project, eventType)
        if (setting != null) {
            return setting.allowed
        }
        // yona UserProjectNotification.isNotifiedByDefault(): NEW_COMMENT는 프로젝트 감시만으로는
        // 기본적으로 알림을 받지 않는다(사용자가 명시적으로 켜야만 받음).
        return eventType != EventType.NEW_COMMENT
    }
}