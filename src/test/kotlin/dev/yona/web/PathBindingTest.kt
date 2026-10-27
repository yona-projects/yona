package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 숫자 경로 변수에 숫자가 아닌 값이 오면 접근 검사보다 먼저 `400`이다. Play는 라우트의 바인더가 액션 앞에서 먼저 실패하므로
 * (`Global.onBadRequest`) 어노테이션으로 하는 접근 검사나 프로젝트 확인이 일어나지 않고 `Cache-Control`도 붙지 않는다.
 */
@AutoConfigureMockMvc
class PathBindingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `라벨 번호 자리의 글자는 프로젝트 확인 전에 400이다`() {
        // PUT /:user/:project/issue/label/:id 의 id는 Long이라 `categories`는 바인더가 거부한다
        mockMvc.perform(put("/nobody/none/issue/label/categories")).andExpect(status().isBadRequest).andExpect(header().doesNotExist("Cache-Control"))
    }

    @Test
    fun `분류 번호 자리의 글자도 400이다`() {
        mockMvc.perform(delete("/nobody/none/issue/label/category/abc")).andExpect(status().isBadRequest)
    }

    @Test
    fun `숫자이면 평소처럼 처리한다`() {
        // 없는 프로젝트는 프로젝트 확인이 403으로 막는다
        mockMvc.perform(get("/nobody/none/issue/1")).andExpect(status().isForbidden)
    }
}
