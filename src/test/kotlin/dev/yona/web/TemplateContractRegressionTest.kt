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

    @Test
    fun `풀 리퀘스트 타임라인을 Ajax로 다시 읽어도 라벨 글자색과 더 보기가 그대로 동작한다 (pullrequest-F13, F14)`() {
        val meta = File("src/main/resources/static/assets/javascripts/esm/service/pullrequest.Meta.js").readText()
        // 전체 화면 스크립트(view.html)가 쓰는 선택자와 같아야 다시 읽은 라벨에도 글자색이 붙는다
        assertTrue(meta.contains("\".event > .label\""), "다시 읽은 타임라인의 라벨을 `.label`로 찾지 않는다")
        assertFalse(meta.contains("\".event > .badge\""))
        val view = template("pullrequest/view.html")
        assertFalse(view.contains("\$(\"button.moreBtn\").on(\"click\""), "다시 읽은 타임라인의 더 보기 단추에는 처리기가 걸리지 않는다")
        assertTrue(view.contains("on(\"click\", \"button.moreBtn\""), "문서에 위임해 다시 그린 단추에도 건다")
    }

    @Test
    fun `코드 주고받기 목록의 담당자, 리뷰 요청, 라벨 선택을 바꾸면 검색 폼이 제출된다 (pullrequest-F10, 추가 필터가 제출되지 않았다)`() {
        val search = template("pullrequest/partial-search.html")
        for (id in listOf("assignees", "reviewRequested", "labelIds")) {
            assertTrue(Regex("\\$\\(['\"][^'\"]*#$id[^'\"]*['\"]\\)\\.on\\(['\"]change['\"]").containsMatchIn(search), "#$id 선택을 바꿔도 제출하지 않는다")
        }
    }

    @Test
    fun `위키 편집 화면은 저장하지 않고 떠나면 경고하고 복제 주소 복사는 결과를 알린다 (wiki-F4, wiki-F6)`() {
        val edit = template("wiki/edit.html")
        assertTrue(edit.contains("beforeunload"), "저장하지 않고 떠나는 것을 경고하지 않는다(게시판 글쓰기는 경고한다)")
        val shell = template("wiki/shell.html")
        assertTrue(shell.contains("wiki-clone-feedback") && shell.contains("data-copied") && shell.contains("data-failed"), "복사 성공과 실패를 알리지 않는다")
        assertTrue(shell.contains(".catch("), "복사 실패(권한 거부)를 처리하지 않는다")
    }

    @Test
    fun `감사 무결성 검증 단추는 응답 상태를 보고 실패를 알린다 (audit-F4, 403이면 아무 일도 없었다)`() {
        val audit = template("audit/list.html")
        assertTrue(audit.contains("data-failed=#{audit.verify.failed}"), "실패 문구가 없다")
        assertTrue(audit.contains("r.ok") && audit.contains(".catch("), "응답 상태를 보지 않고 실패를 처리하지 않는다")
    }
}
