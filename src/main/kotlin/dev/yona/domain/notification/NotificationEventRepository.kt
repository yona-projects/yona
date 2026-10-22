package dev.yona.domain.notification

import dev.yona.domain.user.User
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** 알림 이벤트 저장소이다. */
interface NotificationEventRepository : JpaRepository<NotificationEvent, Long> {

    /**
     * 사용자가 받는 알림을 최신순으로 조회한다.
     * v1.6 `NotificationEvent.findByReceiver`는 `notification_event_n4user`를 조인해 `created DESC`로 정렬한다.
     * 같은 시각의 알림 순서가 흔들리지 않도록 번호 내림차순을 덧붙였다.
     */
    @Query("select e from NotificationEvent e join e.receivers r where r.id = :userId order by e.created desc, e.id desc")
    fun findByReceiverId(userId: Long, pageable: Pageable): List<NotificationEvent>

    /** 사용자가 받는 알림 중 [from]번째(0부터)부터 [size]개를 최신순으로 돌려준다. */
    fun findByReceiver(user: User, from: Int, size: Int): List<NotificationEvent> =
        findByReceiverId(requireNotNull(user.id), OffsetPageRequest(from.toLong(), size))
}

/** 페이지 번호가 아니라 시작 위치(offset)로 자르는 [Pageable]이다. v1.6의 `setFirstRow`, `setMaxRows`에 해당한다. */
class OffsetPageRequest(private val offset: Long, private val limit: Int) : Pageable {
    override fun getPageNumber(): Int = (offset / limit).toInt()
    override fun getPageSize(): Int = limit
    override fun getOffset(): Long = offset
    override fun getSort(): Sort = Sort.unsorted()
    override fun next(): Pageable = OffsetPageRequest(offset + limit, limit)
    override fun previousOrFirst(): Pageable = OffsetPageRequest(maxOf(0, offset - limit), limit)
    override fun first(): Pageable = OffsetPageRequest(0, limit)
    override fun withPage(pageNumber: Int): Pageable = OffsetPageRequest(pageNumber.toLong() * limit, limit)
    override fun hasPrevious(): Boolean = offset > 0
}

/** 사용자가 프로젝트에서 이벤트 종류별로 알림을 받겠다(또는 받지 않겠다)고 정한 설정의 저장소이다. */
interface UserProjectNotificationRepository : JpaRepository<UserProjectNotification, Long> {

    /** 프로젝트에서 [eventType] 알림을 받겠다([allowed]=`true`)거나 받지 않겠다(`false`)고 정한 설정이다 (v1.6 `UserProjectNotification.findByEventTypeAndOption`). */
    fun findByProjectIdAndNotificationTypeAndAllowed(projectId: Long, notificationType: dev.yona.domain.enumeration.EventType, allowed: Boolean): List<UserProjectNotification>
}
