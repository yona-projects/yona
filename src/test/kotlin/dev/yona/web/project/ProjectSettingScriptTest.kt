package dev.yona.web.project

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 프로젝트 설정 화면 스크립트(`yobi.project.Setting.js`)에서 v1.6 리뷰어 수 토글 코드가 빠졌는지 확인한다(`decisions.md` D-45, D-46).
 * 리뷰어 사용과 리뷰어 수 입력은 화면에서 없어졌으므로 그것을 켜고 끄던 코드는 쓸 곳이 없다. 메뉴 설정에 따라 리뷰어 영역(승인 해제 설정)을 보이고 숨기는 코드는 남아야 한다.
 */
class ProjectSettingScriptTest {

    private val script = File("src/main/resources/static/assets/javascripts/service/yobi.project.Setting.js").readText()

    @Test
    fun `리뷰어 수 토글 코드는 없다`() {
        listOf("welReviewerCount ", "welReviewerCount=", "welReviewerCountDisable", "_toggleReviewerCount", "reviewer-count", "reviewerCountDisable", "#welReviewerCount").forEach {
            assertFalse(script.contains(it), "$it 는 쓰이지 않는다")
        }
    }

    @Test
    fun `메뉴 설정에 따라 리뷰어 영역을 보이고 숨기는 코드는 남아 있다`() {
        assertTrue(script.contains("welReviewerCountSettingPanel"))
        assertTrue(script.contains("#reviewerCountSettingPanel"))
        assertTrue(script.contains(".show()") && script.contains(".hide()"))
    }
}
