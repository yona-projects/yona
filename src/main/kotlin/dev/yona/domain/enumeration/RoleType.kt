package dev.yona.domain.enumeration

/**
 * 역할의 종류이다. `id`는 `role` 테이블의 기본키와 일치해야 한다(초기 데이터가 이 번호로 적재된다).
 * v1.6 `models.enumeration.RoleType`과 같다.
 */
enum class RoleType(val id: Long) {
    MANAGER(1), MEMBER(2), SITEMANAGER(3), ANONYMOUS(4), GUEST(5), ORG_ADMIN(6), ORG_MEMBER(7)
}
