package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

// 이전 버전은 사이트 관리자·게스트를 state 값(SITE_ADMIN/GUEST)으로 표현했다. 독립 축으로 옮기면서 기존 행을 잃지 않아야 한다.
class UserStateAxisMigrationIntegrationSpec @Autowired constructor(
    private val migration: UserStateAxisMigration,
    private val userRepository: UserRepository,
    private val jdbcTemplate: JdbcTemplate
) : AbstractIntegrationTest() {
    init {
        describe("UserStateAxisMigration.migrate") {
            it("state=SITE_ADMIN/GUEST 행을 플래그로 옮기고 state를 ACTIVE로 되돌리며, 다시 실행해도 변하지 않는다") {
                val admin = userRepository.save(User(loginId = "mig-admin", name = "관리자", email = "mig-admin@yona.io"))
                val guest = userRepository.save(User(loginId = "mig-guest", name = "게스트", email = "mig-guest@yona.io"))
                val locked = userRepository.save(User(loginId = "mig-locked", name = "잠금", email = "mig-locked@yona.io", state = UserState.LOCKED))
                jdbcTemplate.update("update n4user set state = 'SITE_ADMIN' where id = ?", admin.id)
                jdbcTemplate.update("update n4user set state = 'GUEST' where id = ?", guest.id)

                (migration.migrate() >= 2) shouldBe true

                val a = userRepository.findById(admin.id!!).orElseThrow()
                a.isSiteAdmin shouldBe true
                a.state shouldBe UserState.ACTIVE
                val g = userRepository.findById(guest.id!!).orElseThrow()
                g.isGuest shouldBe true
                g.state shouldBe UserState.ACTIVE
                userRepository.findById(locked.id!!).orElseThrow().state shouldBe UserState.LOCKED

                migration.migrate() shouldBe 0
            }
        }
    }
}
