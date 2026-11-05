package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/** 사이트 관리자 저장소이다. */
interface SiteAdminRepository : JpaRepository<SiteAdmin, Long> {

    /** 사용자가 사이트 관리자로 등록되어 있는지 확인한다 (v1.6 `SiteAdmin.exists`). */
    fun existsByAdminId(userId: Long): Boolean

    /** 지정한 사용자를 뺀 사이트 관리자 행 수를 센다. 목록 탭의 배지에서 기본 관리자를 빼는 데 쓴다. */
    fun countByAdminIdNot(userId: Long): Long
}
