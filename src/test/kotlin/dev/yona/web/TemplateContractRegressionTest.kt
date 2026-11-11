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

    @Test
    fun `2단계 수단 목록의 마지막 사용이 비어 있으면 사용한 적 없음으로 보인다 (usersec-F10, 새로 등록한 보안 키 행이 빈 칸이었다)`() {
        val list = template("user/edit-two-factor.html")
        assertTrue(Regex("m\\.lastUsedText[^>]*apiToken\\.neverUsed|apiToken\\.neverUsed[^>]*m\\.lastUsedText").containsMatchIn(list), "빈 마지막 사용을 그대로 보인다")
    }

    @Test
    fun `관리자 권한 회수가 보호 규칙으로 403이 되면 화면을 다시 읽어 안내를 보인다 (site-F3, 아무 반응이 없었다)`() {
        val users = template("site/user-list.html")
        assertTrue(users.contains("site-admin-toggle"), "권한 단추를 가려낼 클래스가 없다")
        assertTrue(Regex("request:error[\\s\\S]{0,160}403[\\s\\S]{0,80}reload").containsMatchIn(users), "403일 때 다시 읽지 않는다")
    }

    @Test
    fun `LDAP와 OIDC 연결 확인 단추 옆에 저장한 설정으로 확인한다는 안내가 있다 (site-F16, 입력만 하고 눌러 저장 전 값이 확인된 줄 알았다)`() {
        val sso = template("site/sso.html")
        assertEquals(2, Regex("#\\{site\\.sso\\.check\\.saved}").findAll(sso).count(), "LDAP와 OIDC 확인 단추 각각에 안내가 있어야 한다")
    }

    @Test
    fun `사이드바 고정과 복원은 현재 주소의 쿼리도 함께 넘긴다 (shell-F6, v1_6은 pathname만 넘겨 목록의 필터와 쪽이 사라졌다)`() {
        val layout = template("layout/layout.html")
        assertFalse(layout.contains("\"?path=\" + window.location.pathname"), "pathname만 넘긴다")
        assertEquals(2, Regex("encodeURIComponent\\(window\\.location\\.pathname \\+ window\\.location\\.search\\)").findAll(layout).count())
    }

    @Test
    fun `본문 링크의 바깥 링크 판정에서 mailto와 tel은 새 창으로 열지 않는다 (shell-F26, v1_6은 빈 새 탭이 떴다)`() {
        val scripts = template("common/scripts.html")
        assertTrue(Regex("match\\(\"\\^\\[\\^\\./#]\"\\)\\s*&&\\s*!/\\^\\(mailto\\|tel\\):/i\\.test\\(linkUrl\\)").containsMatchIn(scripts), "mailto와 tel을 제외하지 않는다")
    }

    @Test
    fun `검색 결과는 searchType이 auto일 때 주소를 실제 종류로 바꾸어 쪽 링크가 종류를 바꾸지 않는다 (search-F6, v1_6은 다음 쪽에서 다른 종류로 풀렸다)`() {
        val search = template("search/partial-search.html")
        val replace = search.indexOf("params.set('searchType', [[\${current}]])")
        assertTrue(replace > 0, "auto를 실제 종류로 바꾸지 않는다")
        assertTrue(replace < search.indexOf("th:switch=\"\${current}\""), "쪽 링크가 만들어지기 전(결과 조각을 그리기 전)에 바꿔야 한다")
    }

    @Test
    fun `웹훅 형식을 JSON으로 바꾸면 푸시 포함 체크는 상태 값(prop)으로 켜고 끈다 (project-F20, attr로 바꾸어 이미 눌러 본 뒤에는 체크가 꺼진 채 잠겼다)`() {
        val hooks = template("project/webhooks.html")
        assertFalse(hooks.contains("attr('checked'"), "체크 속성은 처음 상태만 바꾸고 사용자가 누른 뒤에는 상태가 바뀌지 않는다")
        assertTrue(hooks.contains("prop('checked', true)") && hooks.contains("prop('checked', false)"), "체크 상태를 prop으로 바꾼다")
    }

    @Test
    fun `코드 접근이 막힌 사용자에게는 코드와 풀 리퀘스트 단축키도 없다 (project-F6, v1_6은 탭만 숨기고 c, p 키는 권한 없음 화면으로 보냈다)`() {
        val menu = template("project/menu.html")
        assertTrue(Regex("th:if=\"\\$\\{view\\.menuSetting\\.code and view\\.codeAccessible}\"\\]\\*/\\s*,\"C\"").containsMatchIn(menu), "코드 단축키가 코드 접근 권한을 보지 않는다")
        assertTrue(Regex("th:if=\"\\$\\{view\\.menuSetting\\.pullRequest and view\\.vcs\\.pullRequests and view\\.codeAccessible}\"\\]\\*/\\s*,\"P\"").containsMatchIn(menu), "풀 리퀘스트 단축키가 코드 접근 권한을 보지 않는다")
    }

    @Test
    fun `커밋 화면에서도 파일 머리를 누르면 그 파일의 diff가 접히고 펴진다 (code-F8, v1_6은 풀 리퀘스트 변경 탭에만 처리기가 있었다)`() {
        val diff = template("code/diff.html")
        assertTrue(diff.contains("\$('.diff-partial-meta').click(") && diff.contains(".diff-partial-code').toggle()"), "커밋 화면에 파일 머리 접기 처리기가 없다")
    }

    @Test
    fun `조직 화면의 고정 영문 문구는 메시지 키이다 (organization-F10, v1_6은 created, by, at, group을 번역하지 않았다)`() {
        assertFalse(template("organization/list.html").contains("                            created <strong"))
        assertFalse(template("organization/list.html").contains("You do not have permission to view this project's information"))
        val view = template("organization/view.html")
        assertFalse(view.contains("name-tag\">by <a"))
        assertFalse(view.contains("title=\"Group Project\""))
        assertFalse(template("organization/header.html").contains(">group</span>"))
    }

    @Test
    fun `조직 머리글의 가입 신청 단추는 ul 바로 아래가 아니라 li 안에 있다 (organization-F6, v1_6은 ul 안에 button과 div를 바로 넣었다)`() {
        val header = template("organization/header.html")
        assertTrue(Regex("<ul class=\"project-util\">\\s*<li>\\s*<button").containsMatchIn(header))
    }

    @Test
    fun `사이트 데이터 가져오기의 제출 단추에는 글자가 있다 (site-F13, v1_6은 value가 없어 브라우저 기본 글자로 그려졌다)`() {
        assertTrue(template("site/data.html").contains("<input type=\"submit\" th:value=\"#{site.data.import}\">"))
    }

    @Test
    fun `게시판 메뉴가 꺼진 프로젝트의 글 목록에는 글쓰기 단추가 없다 (board-F10, N 키와 글 보기의 글쓰기는 이미 같은 조건이다)`() {
        val list = template("board/list.html")
        val wrapper = Regex("<div class=\"float-end\"([^>]*)>\\s*<a th:href=\"\\$\\{base \\+ '/postform'}\"[^>]*#\\{post\\.write}").find(list)
        assertTrue(wrapper != null, "글쓰기 단추를 찾지 못했다")
        assertTrue(wrapper.groupValues[1].contains("projectView.menuSetting.board"), "게시판 메뉴가 꺼져도 글쓰기 단추가 보인다")
    }

    @Test
    fun `안내 글 상자는 좁은 화면에서 폭을 넓힌다 (welcome-F4, v1_6은 50% 고정이라 모바일에서 글이 좁았다, 고쳤다)`() {
        for (name in listOf("welcome/secret.html", "welcome/restart.html")) {
            val html = template(name)
            assertTrue(Regex("@media[^{]*max-width[^{]*\\{[^}]*\\.secret-box\\s*\\{[^}]*width:\\s*(9\\d|100)%").containsMatchIn(html), "$name: 좁은 화면 규칙이 없다")
        }
    }

    @Test
    fun `도움말의 프로젝트 단축키 안내는 실제로 등록되는 조건과 같다 (shell-F5, v1_6은 메뉴가 꺼져도 B I C M을 안내했다)`() {
        val keymap = template("help/keymap.html")
        fun guard(key: String, label: String): String {
            val at = Regex("<span class=\"ybtn ybtn-small\">$key</span>\\s*<span[^>]*th:text=\"#\\{$label}\"").find(keymap)?.range?.first ?: -1
            assertTrue(at > 0, "$key 안내를 찾지 못했다")
            return keymap.substring(maxOf(0, at - 200), at)
        }
        assertTrue(guard("B", "menu.board").contains("menuSetting.board"), "B")
        assertTrue(guard("I", "menu.issue").contains("menuSetting.issue"), "I")
        assertTrue(guard("M", "milestone").contains("menuSetting.milestone"), "M")
        val code = guard("C", "menu.code")
        assertTrue(code.contains("menuSetting.code") && code.contains("codeAccessible"), "C")
    }

    @Test
    fun `토큰과 OAuth 앱을 만드는 폼은 제출 단추를 한 번만 누르게 한다 (oauth-F3, usersec-F16, 같은 요청을 다시 보내면 중복으로 만들어졌다)`() {
        assertTrue(Regex("<form id=\"token-create\"[^>]*data-submit-once").containsMatchIn(template("user/edit-token-new.html")), "토큰 만들기")
        assertTrue(Regex("<form id=\"oauth-app-form\"[^>]*data-submit-once").containsMatchIn(template("oauth/partial-apps.html")), "OAuth 앱 만들기와 수정")
    }

    @Test
    fun `구성원 추가 입력의 아이디 규칙은 한글을 허용한다 (usersec-F3, organization-F5, 서버 규칙과 같다)`() {
        for (name in listOf("project/members.html", "organization/members.html")) {
            assertTrue(template(name).contains("pattern=\"^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣-]+)*$\""), "$name: 한글을 거부하는 규칙이다")
        }
    }

    @Test
    fun `보안 화면의 저장 단추 옆에 어느 설정이 저장되는지 안내가 있다 (site-F19, 세 폼이 서로 독립이라 다른 영역의 입력은 저장되지 않는다)`() {
        val security = template("site/security.html")
        assertEquals(3, Regex("#\\{site\\.security\\.saveScope\\(").findAll(security).count(), "세 영역마다 저장 범위 안내가 있어야 한다")
    }
}
