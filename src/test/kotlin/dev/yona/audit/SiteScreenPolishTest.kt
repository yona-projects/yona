package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리 화면 다듬기(A-593, A-594, 선생님 시안 수용)의 화면 구조를 지키는 구조 검사이다. 템플릿 파일을 직접 읽는다.
 */
class SiteScreenPolishTest {

    private fun text(path: String) = File("src/main/resources/templates/$path").readText()
    private fun doc(path: String) = Jsoup.parse(text(path))

    @Test
    fun `보안, SSO, LFS의 제목은 다른 사이트 관리 화면과 같은 제목 줄이다`() {
        for (f in listOf("security", "sso", "lfs", "data")) {
            val d = doc("site/$f.html")
            assertNotNull(d.selectFirst(".title_area h2.float-start"), "site/$f.html: 제목 줄")
        }
    }

    @Test
    fun `SSO 설정은 탭마다 구역 제목이 있고 저장과 연결 확인은 한 줄이다`() {
        val d = doc("site/sso.html")
        assertEquals(4, d.select("#sso-pane-ldap h5.sso-section").size, "LDAP: 서버 연결, 속성 이름, 조회용 계정, 로그인 옵션")
        assertTrue(d.select("#sso-pane-oidc h5.sso-section").size >= 4, "OIDC: IdP에 등록할 주소, 클레임 이름, 계정 맞추기, 고급")
        assertTrue(d.select("#sso-pane-saml2 h5.sso-section").size >= 5, "SAML: IdP에 등록할 값, 속성 이름, 계정 맞추기, 고급, 시간 검증")
        assertNotNull(d.selectFirst("#sso-pane-ldap .sso-actions button#ldap-check"), "LDAP의 연결 확인은 저장 옆 한 줄이다")
        assertNotNull(d.selectFirst("#sso-pane-oidc .sso-actions button#oidc-check"), "OIDC의 연결 확인은 저장 옆 한 줄이다")
        assertNotNull(d.selectFirst("#sso-pane-ldap .sso-actions + p.sso-check-help"), "연결 확인이 무엇을 하는지 한 줄로 알린다")
        assertNotNull(d.selectFirst("#sso-pane-oidc .sso-actions + p.sso-check-help"))
        assertNull(d.selectFirst("h5:not(.sso-section)"), "구역 제목은 모두 같은 클래스를 쓴다(IdP 메타데이터 가져오기는 예외 없이 구역이다)")
    }

    @Test
    fun `데이터는 경고 상자와 가져오기, 내보내기 카드로 나뉜다`() {
        val d = doc("site/data.html")
        assertNotNull(d.selectFirst(".alert-soft ul li"))
        assertEquals(2, d.select(".site-card").size)
        assertNotNull(d.selectFirst(".site-card.danger form#importForm button[type=submit].btn-outline-danger"), "가져오기는 교체를 알리는 위험 카드이다")
        assertNull(d.selectFirst("input[type=submit]"), "기본 모양의 제출 단추가 없다")
    }

    @Test
    fun `시스템 진단은 무엇을 점검하는지와 결과를 카드로 보인다`() {
        val d = doc("site/diagnostic.html")
        assertNotNull(d.selectFirst(".site-card ul.diag-checks li"))
        assertTrue(text("site/diagnostic.html").contains("alert-soft"), "문제가 있으면 상자로 보인다")
    }

    @Test
    fun `업데이트는 버전 카드와 알림 카드 두 장이다`() {
        val d = doc("site/update.html")
        assertNotNull(d.selectFirst("#update-version-card.site-card"))
        assertNotNull(d.selectFirst("#update-notice-card.site-card #update-notice-state"))
        assertNotNull(d.selectFirst("#update-notice-card form#update-notice-form button.btn-outline-primary"))
    }

    @Test
    fun `대량 메일은 카드 안에서 받는 사람을 고르고 선택 수를 알린다`() {
        val d = doc("site/mass-mail.html")
        assertNotNull(d.selectFirst(".mess-mail-wrap .site-card"))
        assertEquals(2, d.select(".mess-mail-wrap label.mail-type-option input[name=mailingType]").size, "라디오는 카드형 선택지이다")
        assertNotNull(d.selectFirst("#project-list-wrap #selected-projects"))
        assertNotNull(d.selectFirst("#selected-count"), "선택한 프로젝트 수를 보인다")
        assertNotNull(d.selectFirst("p.mail-help"), "메일 쓰기는 메일 프로그램을 연다는 안내가 있다")
    }

    @Test
    fun `비밀번호 재설정은 확인 창과 결과 창을 거치고 삭제 창 제목에는 공백이 있다`() {
        val d = doc("site/user-list.html")
        for (id in listOf("resetPasswordConfirm", "resetPasswordResult", "alertDeletionWrap")) {
            assertEquals("button", d.selectFirst("#$id .modal-header")!!.child(0).tagName(), "$id: 닫기 단추가 제목 앞에 있어야 공통 CSS가 오른쪽 끝으로 보낸다(A-537)")
        }
        assertEquals(1, d.select("#alertDeletionWrap .modal-footer button, #alertDeletionWrap .modal-footer a").size, "사용자 삭제 창은 프로젝트 삭제 창처럼 실행 단추 하나이다")
        assertNotNull(d.selectFirst("#alertDeletionWrap .modal-footer button#accountToggleBtn.ybtn-danger"))
        assertNotNull(d.selectFirst("#resetPasswordConfirm.modal #resetPasswordGo"), "먼저 확인 창")
        assertNotNull(d.selectFirst("#resetPasswordResult.modal #resetPasswordValue"), "결과 창에 새 비밀번호")
        assertNotNull(d.selectFirst("#resetPasswordResult #resetPasswordCopy"))
        assertNull(d.selectFirst("script#dismissable-alerts"), "목록 안에 끼워 넣던 알림은 없어졌다")
        assertTrue(Regex("""id="userInfo"></span><span class="ms-1""").containsMatchIn(text("site/user-list.html")), "사용자 삭제 제목의 이름과 글 사이에 공백")
    }

    @Test
    fun `프로젝트 삭제는 이름을 다시 입력해야 삭제 단추가 켜진다`() {
        val d = doc("site/project-list.html")
        assertNotNull(d.selectFirst("#alertDeletionWrap #project-name-confirm"))
        assertEquals("disabled", d.selectFirst("#projectDeleteBtn")!!.attr("disabled"))
        assertNotNull(d.selectFirst("button[data-toggle=delete-project].ybtn-outline-danger, button[data-toggle=delete-project].btn-outline-danger"), "목록의 삭제 단추는 가볍게")
        assertNotNull(d.selectFirst("#alertDeletionWrap .modal-title"))
        for (id in listOf("alertDeletionWrap")) {
            assertEquals("button", d.selectFirst("#$id .modal-header")!!.child(0).tagName(), "닫기 단추가 제목 앞에 있어야 공통 CSS가 오른쪽 끝으로 보낸다(A-537)")
        }
    }

    @Test
    fun `프로젝트 목록의 로고 칸에는 로고만 있고 글자가 없다`() {
        val a = doc("site/project-list.html").selectFirst("a.list-avatar")!!
        assertEquals("", a.ownText().trim(), "로고 링크 안에 owner/name 글자가 없어야 한다")
        assertTrue(a.children().all { it.tagName() == "img" })
    }

    @Test
    fun `감사 로그 표에는 상세 열이 없다`() {
        val d = doc("audit/list.html")
        assertEquals(6, d.select("table.audit-table thead th").size)
        assertNull(d.selectFirst(".audit-details"))
        assertEquals("6", d.selectFirst("tr.audit-empty td")!!.attr("colspan"))
    }
}

