package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/** 첨부 파일 저장소이다. */
interface AttachmentRepository : JpaRepository<Attachment, Long> {

    /** 컨테이너(리소스)에 속한 첨부를 번호 순으로 조회한다 (v1.6 `Attachment.findByContainer`). */
    fun findByContainerTypeAndContainerIdOrderById(containerType: ResourceType, containerId: Long): List<Attachment>

    /** 같은 해시의 첨부가 있는지 확인한다 (v1.6 `Attachment.exists`). */
    fun existsByHash(hash: String): Boolean

    /** 이름, 해시, 컨테이너가 모두 같은 첨부를 찾는다 (v1.6 `Attachment.findBy`). */
    fun findByNameAndHashAndContainerTypeAndContainerId(name: String, hash: String, containerType: ResourceType, containerId: Long): List<Attachment>

    /** 컨테이너 종류가 같고 [from] 이후(포함)에 만든 첨부를 조회한다 (v1.6 임시 파일 정리의 조회 조건). */
    fun findByContainerTypeAndCreatedDateGreaterThanEqual(containerType: ResourceType, from: java.util.Date): List<Attachment>

    /**
     * 사용자가 올린 첨부를 최근 순으로 쪽 단위로 조회한다 (v1.6 `Attachment.findByUser`).
     * [filter]가 비어 있지 않으면 이름에 대소문자 구분 없이 포함된 것만 조회한다.
     */
    @Query("select a from Attachment a where a.ownerLoginId = :loginId and (:filter = '' or lower(a.name) like lower(concat('%', :filter, '%')) escape '\\') order by a.createdDate desc, a.id desc")
    fun findByUser(loginId: String, filter: String, pageable: Pageable): Page<Attachment>
}
