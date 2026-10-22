package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

/** 첨부 파일 저장소이다. */
interface AttachmentRepository : JpaRepository<Attachment, Long> {

    /** 컨테이너(리소스)에 속한 첨부를 번호 순으로 조회한다 (v1.6 `Attachment.findByContainer`). */
    fun findByContainerTypeAndContainerIdOrderById(containerType: ResourceType, containerId: Long): List<Attachment>

    /** 같은 해시의 첨부가 있는지 확인한다 (v1.6 `Attachment.exists`). */
    fun existsByHash(hash: String): Boolean

    /** 이름, 해시, 컨테이너가 모두 같은 첨부를 찾는다 (v1.6 `Attachment.findBy`). */
    fun findByNameAndHashAndContainerTypeAndContainerId(name: String, hash: String, containerType: ResourceType, containerId: Long): List<Attachment>
}
