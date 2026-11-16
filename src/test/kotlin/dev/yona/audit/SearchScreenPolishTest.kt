package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 검색 화면 다듬기(A-598, 선생님 지시)의 화면 구조를 지키는 구조 검사이다. 템플릿 파일을 직접 읽는다.
 */
class SearchScreenPolishTest {

    private fun text(path: String) = File("src/main/resources/templates/$path").readText()
    private fun doc(path: String) = Jsoup.parse(text(path))

    @Test
    fun `검색창은 입력란과 테마색 검색 단추가 한 줄 묶음이다`() {
        val d = doc("search/partial-search.html")
        assertNotNull(d.selectFirst("form#searchInnerForm.search-form input#searchKeyword"))
        assertNotNull(d.selectFirst("form#searchInnerForm.search-form button.ybtn-primary[type=submit]"))
    }

    @Test
    fun `검색 결과가 없으면 네 조각 모두 안내 글을 보인다`() {
        for (f in listOf("items", "projects", "users", "milestones")) {
            val d = doc("search/partial-$f.html")
            assertNotNull(d.selectFirst(".empty-result .empty-result-text"), "partial-$f: 결과 없음 안내")
            assertNotNull(d.selectFirst(".empty-result .empty-result-hint"), "partial-$f: 결과 없음 보조 안내")
        }
    }

    @Test
    fun `검색된 건수는 세 자리마다 쉼표로 끊어 보인다`() {
        val t = text("search/partial-search.html")
        for (k in listOf("counts.issues", "counts.users", "counts.projects", "counts.posts", "counts.milestones", "counts.issueComments", "counts.postComments", "counts.reviews", "search.currentCount")) {
            assertTrue(Regex("""formatInteger\($k, ?1, ?'COMMA'\)""").containsMatchIn(t), "$k 는 formatInteger로 보인다")
        }
    }

    @Test
    fun `프로젝트 결과의 프로젝트 이름은 보라 태그가 아니라 일반 링크이다`() {
        val d = doc("search/partial-projects.html")
        assertEquals(0, d.select("a.project-link").size, "project-link(보라 태그) 클래스를 쓰지 않는다")
        assertNotNull(d.selectFirst("a.search-project-name"))
    }
}
