package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 열을 가진 `.row`가 flex로 배치되는지 지키는 구조 검사이다(`decisions.md` D-55, X-38h-2).
 *
 * 열의 폭과 간격은 Bootstrap 2의 유동 그리드(2.1277% 간격) 그대로이고 배치 방식만 float에서 flex로 바뀌었다.
 * 열 폭의 합이 12를 넘는 행은 float이 넘친 열을 앞쪽 빈 공간 아래로 내리는 방식이 flex와 달라 `row-float` 표식으로 float 배치를 그대로 둔다.
 */
class GridFlexTest {

    private val grid = File("app/assets/stylesheets/scss/_grid.scss").readText()

    @Test
    fun `열을 가진 행은 flex 컨테이너이다`() {
        assertTrue(Regex("""&:has\(> \[class\*="col-lg-"]\) \{[^}]*display: flex;[^}]*flex-wrap: wrap;""").containsMatchIn(grid))
        assertTrue(grid.contains(""":where(.row:has(> [class*="col-lg-"])) {"""))
    }

    @Test
    fun `열 폭의 합이 12를 넘는 사용자 목록 행은 float 배치를 유지한다`() {
        assertTrue(grid.contains("&.row-float:has(> [class*=\"col-lg-\"])"))
        assertTrue(File("src/main/resources/templates/site/user-list.html").readText().contains("""class="row row-float listitem""""))
    }
}
