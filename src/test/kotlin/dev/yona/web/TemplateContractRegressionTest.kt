package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계약 조사의 재현 검증(`ui-contract/verification/`)에서 확인된 v2 신규 결함이 템플릿에 다시 들어오지 않게 지킨다.
 * 마크업 한 줄이 원인인 결함은 화면을 그리지 않고 소스로 확인한다(브라우저 동작은 Playwright로 확인했다).
 */
class TemplateContractRegressionTest {

    private fun template(path: String) = File("src/main/resources/templates/$path").readText()

    @Test
    fun `추천자 모달의 이메일 복사 단추는 id가 아니라 클래스이다 (issue-F20, 모달이 둘이면 id가 겹쳐 둘째 단추가 복사되지 않았다)`() {
        assertFalse(template("issue/partial-voter-list.html").contains("id=\"copyEmailBtn\""), "추천자 목록의 단추가 id를 쓴다")
        assertTrue(template("issue/partial-voter-list.html").contains("copy-email-btn"))
        val view = template("issue/view.html")
        assertFalse(view.contains("\$(\"#copyEmailBtn\")"), "처리기가 첫 번째 id 요소에만 걸린다")
        assertTrue(view.contains("\".copy-email-btn\""), "클래스로 문서에 위임해 모든 단추에 건다")
    }

    @Test
    fun `익명의 글 변경 이력 링크는 모달을 열지 않고 로그인 주소로 이동한다 (board-F15, v1_6은 이동했다)`() {
        val link = Regex("<a th:href=\"'/users/loginform\\?redirectUrl=' \\+ \\$\\{base \\+ '/post/' \\+ post\\.number}\"[^>]*>").find(template("board/view.html"))
        assertTrue(link != null, "익명용 변경 이력 링크를 찾지 못했다")
        assertFalse(link.value.contains("data-bs-toggle"), "모달 대상이 없는데 모달 토글이 붙어 있다: ${link.value}")
    }

    @Test
    fun `검색 결과 강조는 결과 영역 안의 요소만 고친다 (search-F4, 밖의 title 요소의 링크가 글자로 바뀌어 사라졌다)`() {
        val search = template("search/partial-search.html")
        assertFalse(search.contains("\$('.search-content-body, .title').each("), "문서 전체의 .title을 고친다")
        assertEquals(1, Regex("\\$\\('\\.search-result-wrap \\.search-content-body, \\.search-result-wrap \\.title'\\)\\.each\\(").findAll(search).count())
    }

    @Test
    fun `Subversion 커밋 화면의 브랜치 토글 글자는 고른 브랜치이다 (code-F5, 늘 HEAD로 고정되어 있었다)`() {
        val svn = template("code/svn-diff.html")
        assertFalse(svn.contains("? 'HEAD' : 'HEAD'"), "선택한 브랜치와 상관없이 HEAD를 보인다")
        assertTrue(svn.contains("branches.?[selected]"), "고른 브랜치(selected)의 이름을 쓴다")
    }
}
