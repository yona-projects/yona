package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

// yona 초기 Role 데이터 적재 로직(신규, legacy에는 대응하는 별도 클래스 없음 — conf/evolutions
// 마이그레이션의 초기 데이터 INSERT를 애플리케이션 기동 시점 CommandLineRunner로 이식).
class DatabaseInitializerSpec : DescribeSpec({
    val roleRepository = mockk<RoleRepository>()
    val initializer = DatabaseInitializer(roleRepository)

    beforeTest {
        clearMocks(roleRepository)
    }

    describe("run()") {
        it("Role 데이터가 하나도 없으면 기본 Role 전체를 적재해야 한다") {
            every { roleRepository.count() } returns 0L
            val savedRoles = slot<List<Role>>()
            every { roleRepository.saveAll(capture(savedRoles)) } returns emptyList()

            initializer.run()

            verify(exactly = 1) { roleRepository.saveAll(any<List<Role>>()) }
            savedRoles.captured.isNotEmpty() shouldBe true
        }

        it("Role 데이터가 이미 있으면 다시 적재하지 않아야 한다") {
            every { roleRepository.count() } returns 5L

            initializer.run()

            verify(exactly = 0) { roleRepository.saveAll(any<List<Role>>()) }
        }
    }
})
