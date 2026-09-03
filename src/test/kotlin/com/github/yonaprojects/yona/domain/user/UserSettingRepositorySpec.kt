package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

// yona models/UserSetting.java findByUser() 대응 (P2-11)
@Transactional
class UserSettingRepositorySpec @Autowired constructor(
    private val userSettingRepository: UserSettingRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("UserSettingRepository") {
            beforeEach {
                userSettingRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("사용자별 기본 로그인 페이지 설정을 저장하고 조회할 수 있어야 한다") {
                val user = userRepository.save(User(loginId = "settinguser", name = "설정유저", email = "setting@yona.io"))

                userSettingRepository.save(UserSetting(user = user, loginDefaultPage = "notifications"))

                val found = userSettingRepository.findByUserId(user.id!!).orElse(null)
                found shouldBe found
                found?.loginDefaultPage shouldBe "notifications"
            }

            it("설정이 없는 사용자는 조회 결과가 비어있어야 한다") {
                val user = userRepository.save(User(loginId = "nosetting", name = "무설정유저", email = "nosetting@yona.io"))

                val found = userSettingRepository.findByUserId(user.id!!)

                found.isPresent shouldBe false
            }
        }
    }
}
