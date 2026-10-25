package dev.yona.domain.notification

import dev.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

/** 메일 회신으로 만들어진 리소스와 원본 메일의 연결 저장소이다. */
interface OriginalEmailRepository : JpaRepository<OriginalEmail, Long> {

    /** 메시지 ID로 찾는다. */
    fun findByMessageId(messageId: String): List<OriginalEmail>

    /** 리소스로 만든 메일을 찾는다 (v1.6 `OriginalEmail.findBy`). */
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<OriginalEmail>
}
