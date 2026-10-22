package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/** 역할 저장소이다. 행의 기본키는 `RoleType.id`와 같다. */
interface RoleRepository : JpaRepository<Role, Long>
