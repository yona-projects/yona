package dev.yona.domain.user

import dev.yona.domain.enumeration.UserState
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/** 이메일의 앞부분으로 로그인 ID 후보를 만들 때 겹치면 번호를 올리는 규칙을 검증한다(R-59-5). */
@Transactional
class SignupServiceLoginIdTest : AbstractIntegrationTest() {

    @Autowired private lateinit var signup: SignupService
    @Autowired private lateinit var users: UserRepository

    private fun existing(vararg ids: String) = ids.forEach { users.save(User(loginId = it, name = it, email = "$it@example.com", state = UserState.ACTIVE)) }

    @Test
    fun `후보가 비어 있으면 그대로 쓴다`() {
        assertEquals("freshcand", signup.generateLoginId("freshcand"))
    }

    @Test
    fun `후보가 있으면 -yona를 붙인다`() {
        existing("gcand")
        assertEquals("gcand-yona", signup.generateLoginId("gcand"))
    }

    @Test
    fun `후보와 -yona가 모두 있으면 원래 후보에 번호를 붙이고 겹치면 번호를 올린다 (R-59-5, v1_6은 dup2, dup23, dup234처럼 이어 붙였다)`() {
        existing("dupc", "dupc-yona", "dupc2", "dupc3")
        assertEquals("dupc4", signup.generateLoginId("dupc"))
    }
}
