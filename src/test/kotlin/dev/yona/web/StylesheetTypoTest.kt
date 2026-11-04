package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SCSS 원본의 오타 수정을 확인한다. v1.6에서 옮겨 온 두 곳의 오타가 있었다(C159, C160).
 */
class StylesheetTypoTest {

    @Test
    fun `box-sizing 믹스인의 ms 접두 속성 이름이 올바르다 (C159)`() {
        val css = File("app/assets/stylesheets/scss/_mixins.scss").readText()
        assertFalse(css.contains("-ms-sbox-izing"), "v1.6의 오타가 남아 있다")
        assertTrue(css.contains("-ms-box-sizing:\$param;"))
    }

    @Test
    fun `머티리얼 아이콘 글꼴의 ttf 경로에 점이 하나이다 (C160)`() {
        val css = File("app/assets/stylesheets/scss/_usermenu.scss").readText()
        assertFalse(css.contains("material-icon..ttf"), "v1.6의 오타가 남아 있다")
        assertTrue(css.contains("url('fonts/material-icon.ttf') format('truetype')"))
    }
}
