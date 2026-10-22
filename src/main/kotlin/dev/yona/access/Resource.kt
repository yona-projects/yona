package dev.yona.access

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.user.User

/**
 * 권한 검사의 대상이 되는 리소스이다. v1.6 `models.resource.Resource`에 해당한다.
 *
 * v1.6은 엔티티마다 `asResource()`로 이 객체를 만들고, 권한 검사는 `(종류, 번호)`로 필요한 엔티티를 다시
 * 조회한다. v2도 같은 방식이다.
 *
 * @property id 리소스 번호 (문자열이다. 예: 이슈 번호, `"<프로젝트 번호>:<커밋 해시>"`)
 * @property type 리소스 종류
 * @property container 이 리소스를 담고 있는 리소스. 첨부는 글이나 사용자를 컨테이너로 가진다
 * @property authorId 작성자 번호. 작성자 개념이 없으면 `null`이다
 */
sealed class Resource(
    val id: String,
    val type: ResourceType,
    val container: Resource? = null,
    val authorId: Long? = null
) {
    /** 이 리소스가 속한 프로젝트이다. 전역 리소스는 프로젝트가 없다. */
    abstract val project: Project?

    /** [user]가 작성자이면 `true`이다 (v1.6 `Resource.isAuthoredBy`). */
    fun isAuthoredBy(user: User?): Boolean = authorId != null && authorId == user?.id
}

/**
 * 프로젝트에 속하지 않는 리소스(사용자, 조직, 프로젝트 자체, 라벨, 임시 첨부 등)이다.
 * 프로젝트를 물으면 v1.6처럼 [UnsupportedOperationException]이 발생한다.
 */
class GlobalResource(id: String, type: ResourceType, container: Resource? = null, authorId: Long? = null) :
    Resource(id, type, container, authorId) {

    override val project: Project?
        get() = throw UnsupportedOperationException("전역 리소스는 프로젝트가 없습니다: $type/$id")
}

/** 프로젝트에 속한 리소스(이슈, 게시글, 마일스톤, 코드 등)이다. */
class ProjectResource(
    id: String,
    type: ResourceType,
    override val project: Project?,
    container: Resource? = null,
    authorId: Long? = null
) : Resource(id, type, container, authorId)
