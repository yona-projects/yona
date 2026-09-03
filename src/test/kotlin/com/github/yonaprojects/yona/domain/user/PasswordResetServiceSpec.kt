package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import org.springframework.test.util.AopTestUtils

@Transactional
class PasswordResetServiceSpec @Autowired constructor(
    private val passwordResetService: PasswordResetService,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("PasswordResetService") {
            lateinit var user: User

            beforeEach {
                userRepository.deleteAll()
                user = userRepository.save(User(loginId = "reset-user", name = "비번리셋유저", email = "reset@yona.io", password = "oldPassword", passwordSalt = "oldSalt"))
            }

            it("해시 생성 후 테이블에 추가 및 유효성 검증이 정상 동작해야 한다") {
                val hash = passwordResetService.generateResetHash(user.loginId)
                hash shouldNotBe null
                hash.isNotEmpty() shouldBe true

                passwordResetService.addHashToResetTable(user.loginId, hash)
                passwordResetService.isValidResetHash(hash) shouldBe true
            }

            it("테이블에 없는 해시는 유효하지 않아야 한다") {
                passwordResetService.isValidResetHash("invalidHash") shouldBe false
                passwordResetService.resetPassword("invalidHash", "newPassword") shouldBe false
            }

            it("비밀번호 리셋이 정상 동작해야 한다") {
                val hash = passwordResetService.generateResetHash(user.loginId)
                passwordResetService.addHashToResetTable(user.loginId, hash)

                val result = passwordResetService.resetPassword(hash, "newPassword")
                result shouldBe true

                val updatedUser = userRepository.findById(user.id!!).orElseThrow()
                updatedUser.password shouldNotBe "oldPassword"
                updatedUser.passwordSalt shouldNotBe "oldSalt"
                
                // 리셋 후 해시 제거 검증
                passwordResetService.isValidResetHash(hash) shouldBe false
            }

            it("만료된 해시는 유효하지 않아야 하고 테이블에서 제거되어야 한다") {
                val hash = passwordResetService.generateResetHash(user.loginId)
                passwordResetService.addHashToResetTable(user.loginId, hash)

                // 리플렉션으로 시간을 과거로 돌림
                // Spring이 @Transactional 때문에 CGLIB 프록시를 Objenesis로 생성해(생성자/필드
                // 초기화가 실행되지 않음) passwordResetService 자체에 리플렉션하면 프록시 껍데기의
                // 초기화 안 된(null) 필드를 읽게 된다 — AopTestUtils로 실제 타겟 인스턴스를 언랩해야 한다.
                val realService = AopTestUtils.getUltimateTargetObject<PasswordResetServiceImpl>(passwordResetService)
                val serviceKClass = PasswordResetServiceImpl::class
                val timetableField = serviceKClass.memberProperties.find { it.name == "resetHashTimetable" }
                timetableField?.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val timetable = timetableField?.getter?.call(realService) as ConcurrentHashMap<String, Long>
                timetable[hash] = System.currentTimeMillis() - (3600 * 1000 + 1) // 1시간 초과

                passwordResetService.isValidResetHash(hash) shouldBe false
            }
            
            it("비밀번호 리셋 시 해당 loginId의 유저가 없으면 false를 반환해야 한다") {
                val hash = passwordResetService.generateResetHash("not-found-user")
                passwordResetService.addHashToResetTable("not-found-user", hash)
                passwordResetService.resetPassword(hash, "newPassword") shouldBe false
            }

            it("타임스탬프 기록이 없는 해시는 만료로 간주해 유효하지 않아야 한다") {
                val hash = passwordResetService.generateResetHash(user.loginId)
                passwordResetService.addHashToResetTable(user.loginId, hash)

                // isExpired()의 resetHashTimetable[hashString] ?: return true — resetHashMap엔 있지만
                // resetHashTimetable엔 없는 상태를 리플렉션으로 인위 조성(addHashToResetTable은 항상 둘을
                // 함께 채우므로 정상 API 경로로는 이 불일치 상태에 도달할 수 없음).
                val realService = AopTestUtils.getUltimateTargetObject<PasswordResetServiceImpl>(passwordResetService)
                val serviceKClass = PasswordResetServiceImpl::class
                val timetableField = serviceKClass.memberProperties.find { it.name == "resetHashTimetable" }
                timetableField?.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val timetable = timetableField?.getter?.call(realService) as ConcurrentHashMap<String, Long>
                timetable.remove(hash)

                passwordResetService.isValidResetHash(hash) shouldBe false
            }

            it("getKeyByValue - 두 번째 이후 항목에서 값을 찾으면 해당 키를 반환해야 한다") {
                // private 제네릭 메서드라 리플렉션으로 직접 호출. resetHashMap(ConcurrentHashMap)은 순회
                // 순서를 보장하지 않아 공개 API만으로는 "첫 항목 불일치 후 계속 탐색" 분기를 결정적으로
                // 재현할 수 없으므로, 순서가 보장되는 LinkedHashMap을 직접 넘겨 호출한다.
                val realService = AopTestUtils.getUltimateTargetObject<PasswordResetServiceImpl>(passwordResetService)
                val method = PasswordResetServiceImpl::class.declaredFunctions.find { it.name == "getKeyByValue" }!!
                method.isAccessible = true
                val map = linkedMapOf("k1" to "v1", "k2" to "v2", "k3" to "v3")

                method.call(realService, map, "v3") shouldBe "k3"
            }

            it("getKeyByValue - 값을 찾지 못하면 null을 반환해야 한다") {
                val realService = AopTestUtils.getUltimateTargetObject<PasswordResetServiceImpl>(passwordResetService)
                val method = PasswordResetServiceImpl::class.declaredFunctions.find { it.name == "getKeyByValue" }!!
                method.isAccessible = true
                val map = linkedMapOf("k1" to "v1", "k2" to "v2")

                method.call(realService, map, "not-found") shouldBe null
            }
        }
    }
}
