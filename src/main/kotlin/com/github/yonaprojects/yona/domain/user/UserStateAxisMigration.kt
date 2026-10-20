package com.github.yonaprojects.yona.domain.user

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 이전 버전은 사이트 관리자·게스트를 잠금과 같은 state 컬럼 값(SITE_ADMIN/GUEST)으로 표현했다. v1.6처럼 셋을 독립 축으로 둔다
 * (관리자: SiteAdmin 소속 → is_site_admin, 게스트: is_guest, 잠금·삭제만 state). 기동 시 기존 행을 옮기며, 이미 옮겨진 DB에서는
 * 아무 행도 건드리지 않는다(멱등).
 */
@Component
class UserStateAxisMigration(
    private val jdbcTemplate: JdbcTemplate
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(UserStateAxisMigration::class.java)

    override fun run(args: ApplicationArguments) {
        val moved = migrate()
        if (moved > 0) logger.info("사용자 state 축 분리 마이그레이션: {}건 이전", moved)
    }

    @Transactional
    fun migrate(): Int {
        val admins = jdbcTemplate.update("UPDATE n4user SET is_site_admin = ?, state = 'ACTIVE' WHERE state = 'SITE_ADMIN'", true)
        val guests = jdbcTemplate.update("UPDATE n4user SET is_guest = ?, state = 'ACTIVE' WHERE state = 'GUEST'", true)
        return admins + guests
    }
}
