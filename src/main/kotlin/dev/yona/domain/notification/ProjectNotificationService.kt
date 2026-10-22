package dev.yona.domain.notification

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 구독 중인 프로젝트에서 이벤트 종류별로 알림을 받을지 정하는 설정을 다룬다. v1.6 `models.UserProjectNotification`에 해당한다.
 *
 * 설정이 없으면 기본값을 따른다. 기본값은 모든 이벤트가 켜짐이고 새 댓글만 꺼짐이다. 기본값과 다르게 정한 것만 행으로 남기고,
 * 다시 기본값과 같아지면 행을 지운다.
 */
@Service
class ProjectNotificationService(private val settings: UserProjectNotificationRepository) {

    /** 구독만 한 상태에서 [type] 알림을 기본으로 받으면 `true`이다 (v1.6 `isNotifiedByDefault`). 새 댓글만 받지 않는다. */
    fun isNotifiedByDefault(type: EventType): Boolean = type != EventType.NEW_COMMENT

    /** 사용자의 프로젝트별 설정을 프로젝트 번호, 이벤트 종류 순으로 모아 돌려준다 (v1.6 `getProjectNotifications`). */
    @Transactional(readOnly = true)
    fun byProject(user: User): Map<Long, Map<EventType, Boolean>> {
        val result = HashMap<Long, MutableMap<EventType, Boolean>>()
        for (setting in settings.findByUserId(requireNotNull(user.id))) {
            val projectId = setting.project?.id ?: continue
            result.getOrPut(projectId) { HashMap() }[requireNotNull(setting.notificationType)] = setting.allowed == true
        }
        return result
    }

    /**
     * 설정 맵 [byProject]로 [type] 알림을 받는지 알려 준다. 프로젝트에 설정이 하나도 없거나 그 종류만 설정하지 않았으면
     * 기본값을 따른다 (v1.6 `isEnabledNotiType(notiMap, project, notiType)`).
     */
    fun isEnabled(byProject: Map<Long, Map<EventType, Boolean>>, projectId: Long, type: EventType): Boolean =
        byProject[projectId]?.get(type) ?: isNotifiedByDefault(type)

    /**
     * 설정을 뒤집는다. 아직 정하지 않았으면 기본값의 반대로 정하고, 이미 있으면 값을 뒤집은 결과가 기본값과 같아질 때 행을 지운다
     * (v1.6 `WatchProjectApp.toggle`과 `UserProjectNotification.toggle`).
     */
    @Transactional
    fun toggle(user: User, project: Project, type: EventType) {
        val existing = settings.findByUserIdAndProjectIdAndNotificationType(requireNotNull(user.id), requireNotNull(project.id), type).firstOrNull()
        if (existing == null) {
            settings.save(UserProjectNotification(user = user, project = project, notificationType = type, allowed = !isNotifiedByDefault(type)))
            return
        }
        existing.allowed = existing.allowed != true
        if (existing.allowed == isNotifiedByDefault(type)) settings.delete(existing) else settings.save(existing)
    }

    /** 프로젝트 구독을 해제할 때 그 프로젝트에 정한 알림 설정을 모두 지운다 (v1.6 `deleteUnwatchedProjectNotifications`). */
    @Transactional
    fun deleteAll(user: User, project: Project) {
        settings.deleteAll(settings.findByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id)))
    }
}
