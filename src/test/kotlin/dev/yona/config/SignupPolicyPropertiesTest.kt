package dev.yona.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 가입 승인제 설정을 검증한다 (v1.6 `UserApp.isUsingSignUpConfirm`): 새 키가 없을 때만 v1.1의 옛 키 `signup.require.confirm`을 쓴다. */
class SignupPolicyPropertiesTest {

    @Test
    fun `새 키가 있으면 그 값이고 없으면 옛 키이며 둘 다 없으면 거짓이다`() {
        assertEquals(false, YonaProperties.Signup().required())
        assertEquals(true, YonaProperties.Signup(requireConfirm = true).required())
        assertEquals(true, YonaProperties.Signup(requireAdminConfirm = true, requireConfirm = false).required())
        // 새 키가 명시적으로 거짓이면 옛 키가 참이어도 거짓이다
        assertEquals(false, YonaProperties.Signup(requireAdminConfirm = false, requireConfirm = true).required())
    }
}
