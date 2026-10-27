package dev.yona.domain.project

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [FormErrors]가 오류를 필드 이름의 해시 순서로 돌려주는지 검증한다.
 *
 * v1.6 Play `Form.errors()`는 `HashMap`이라 화면의 `htError` JSON 순서가 오류가 생긴 순서가 아니라 필드 이름의 해시 순서이다
 * (공식 v1.16.0의 `/_import` 오류 화면은 `url`을 먼저 거절해도 `name`이 앞에 나온다).
 */
class FormErrorsTest {

    @Test
    fun `필드 순서는 거절한 순서가 아니라 HashMap 순서이다`() {
        val errors = FormErrors()
        errors.reject("url", "a")
        errors.reject("name", "b")
        assertEquals(listOf("name", "url"), errors.fields().keys.toList())
    }

    @Test
    fun `같은 필드의 오류는 거절한 순서를 지킨다`() {
        val errors = FormErrors()
        errors.reject("name", "a")
        errors.reject("name", "b")
        assertEquals(listOf("a", "b"), errors.fields()["name"])
    }
}
