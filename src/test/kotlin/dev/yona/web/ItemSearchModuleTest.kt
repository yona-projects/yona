package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 템플릿의 `data-toggle="item-search"` 입력란(마일스톤 상세의 이슈 거르기, 그룹 홈의 프로젝트 거르기)을 처리하는 스크립트가 있어야 한다.
 * v1.6은 `jquery.search.js`가 입력란에 처음 초점이 갈 때 키 입력으로 `data-item` 요소를 `data-value`로 거르게 했다.
 * 이 파일이 "참조 없는 파일"로 오인되어 지워져(A-410) 입력란이 동작하지 않게 되었다.
 *
 * 원본: v1.6 `public/javascripts/lib/jquery/jquery.search.js`, `milestone/view.scala.html`, `organization/view.scala.html`.
 */
class ItemSearchModuleTest {

    private val templates = File("src/main/resources/templates")
    private val scripts = File("src/main/resources/static/assets/javascripts/esm")

    @Test
    fun `item-search 입력란을 쓰는 템플릿이 있으면 그것을 처리하는 모듈을 진입점이 불러온다`() {
        val users = templates.walkTopDown().filter { it.extension == "html" && it.readText().contains("data-toggle=\"item-search\"") }.toList()
        assertTrue(users.isNotEmpty(), "item-search를 쓰는 템플릿을 찾지 못했다")
        assertTrue(File(scripts, "ui/ItemSearch.js").isFile, "ui/ItemSearch.js가 없다")
        assertTrue(File(scripts, "index.js").readText().contains("./ui/ItemSearch.js"), "index.js가 ItemSearch.js를 불러오지 않는다")
    }
}
