package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface NotificationEventRepository : JpaRepository<NotificationEvent, Long> {
    @Query("select ne from NotificationEvent ne join ne.receivers r where r = :user order by ne.created desc")
    fun findByReceiver(@Param("user") user: User, pageable: Pageable): Page<NotificationEvent>

    // yona NotificationEvent.add()/addWithoutSkipEvent()의 draft-time 병합 조회
    // (`.eq("resourceId",...).eq("resourceType",...).gt("created", draftDate).orderBy("id desc")`) 대응 (P1-27).
    fun findFirstByResourceTypeAndResourceIdAndCreatedAfterOrderByIdDesc(
        resourceType: ResourceType,
        resourceId: String,
        created: Instant
    ): NotificationEvent?

    // yona NotificationEvent.scheduleDeleteOldNotifications() 대응 (P1-27).
    fun deleteByCreatedBefore(threshold: Instant): Long
}
