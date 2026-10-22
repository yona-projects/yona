package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/** 사이트 관리자 저장소이다. */
interface SiteAdminRepository : JpaRepository<SiteAdmin, Long> {

    /** 사용자가 사이트 관리자로 등록되어 있는지 확인한다 (v1.6 `SiteAdmin.exists`). */
    fun existsByAdminId(userId: Long): Boolean
}
