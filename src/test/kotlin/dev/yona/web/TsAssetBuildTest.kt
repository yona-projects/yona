package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TypeScript 소스(`src/main/frontend/ts`)가 Gradle `compileTs`로 빌드되어 서버가 `/assets/javascripts/esm/` 아래에서 내려 주는지 확인한다(D-139).
 *
 * 원본은 `static/` 밖에 두므로(`AssetFilter`가 `.ts` 원문을 그대로 내려 주는 것을 피한다) 산출물만 서버에 실린다.
 */
@AutoConfigureMockMvc
class TsAssetBuildTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `TS로 쓴 core config 모듈이 esm 경로로 제공된다`() {
        mockMvc.perform(get("/assets/javascripts/esm/core/config.js"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("export function url")))
    }

    @Test
    fun `사이드바 숨기기 모듈이 esm 경로로 제공된다`() {
        mockMvc.perform(get("/assets/javascripts/esm/shell/sidebar.js"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("export function initSidebar")))
    }

    @Test
    fun `TS 원본은 제공되지 않는다`() {
        mockMvc.perform(get("/assets/javascripts/esm/core/config.ts")).andExpect(status().isNotFound)
    }

    @Test
    fun `TS 원본은 static 자원 폴더 밖에 있다`() {
        assertTrue(File("src/main/frontend/ts/core/config.ts").isFile)
        assertFalse(File("src/main/resources/static").walkTopDown().any { it.isFile && it.extension == "ts" }, "static 안에 .ts가 있으면 원문이 그대로 내려간다")
    }
}
