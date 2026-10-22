package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * v1.6의 `public/` 폴더가 같은 URL(`/assets/` 아래 모든 경로, `/favicon.ico`)로 제공되는지 검증한다.
 *
 * v1.6 routes: `GET /assets/:file`은 `Assets.at("/public", file)`로, `GET /favicon.ico`는 `images/favicon.ico`로 연결된다.
 * 템플릿이 참조하는 CSS와 JS 경로가 그대로 유효해야 화면이 v1.6과 같게 보인다.
 */
@AutoConfigureMockMvc
class StaticAssetsTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `레이아웃이 참조하는 스타일시트가 assets 경로로 제공된다`() {
        listOf(
            "bootstrap/css/bootstrap.css", "stylesheets/yobi.css", "stylesheets/usermenu.css",
            "stylesheets/yobicon/style.css", "javascripts/lib/select2/select2.css"
        ).forEach { path ->
            mockMvc.perform(get("/assets/$path")).andExpect(status().isOk)
        }
    }

    @Test
    fun `자바스크립트도 assets 경로로 제공된다`() {
        mockMvc.perform(get("/assets/javascripts/yona-layout.js")).andExpect(status().isOk)
    }

    @Test
    fun `favicon이 루트 경로로 제공된다`() {
        mockMvc.perform(get("/favicon.ico")).andExpect(status().isOk)
    }

    @Test
    fun `LESS에서 컴파일한 CSS에 v1_6의 선택자가 들어 있다`() {
        // 로그인 화면(login.scala.html)과 사용자 메뉴가 쓰는 선택자이다.
        mockMvc.perform(get("/assets/stylesheets/yobi.css"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(".login-form-wrap")))
        mockMvc.perform(get("/assets/stylesheets/usermenu.css")).andExpect(status().isOk)
        mockMvc.perform(get("/assets/stylesheets/filetype.css")).andExpect(status().isOk)
    }

    @Test
    fun `없는 자원은 404이다`() {
        mockMvc.perform(get("/assets/no/such/file.css")).andExpect(status().isNotFound)
    }

    @Test
    fun `CSS는 text css 형식으로 응답한다`() {
        mockMvc.perform(get("/assets/stylesheets/yobi.css"))
            .andExpect(content().contentTypeCompatibleWith("text/css"))
    }
}
