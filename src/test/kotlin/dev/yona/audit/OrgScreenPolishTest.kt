package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 조직(그룹) 화면 다듬기(A-577~A-579, 선생님 지시)의 화면 구조를 지키는 구조 검사이다. 템플릿 파일을 직접 읽는다.
 * 사용자 설정과 사이트 관리가 같이 쓰는 조각(`oauth/partial-apps`, `audit/list`)은 조직 화면만 바뀌도록 조직 쪽 틀(`org-oauth`, `org-audit`)로 범위를 좁힌다.
 */
class OrgScreenPolishTest {

    private fun text(path: String) = File("src/main/resources/templates/$path").readText()
    private fun doc(path: String) = Jsoup.parse(text(path))

    @Test
    fun `새 그룹 만들기는 입력란이 폭을 채우고 설명란이 넉넉하다(A-577)`() {
        val d = doc("organization/create.html")
        assertTrue(d.selectFirst("input#name")!!.hasClass("w-100"))
        val descr = d.selectFirst("textarea#descr")!!
        assertTrue(descr.hasClass("w-100")); assertEquals("5", descr.attr("rows"))
        assertNotNull(d.selectFirst(".form-wrap.new-project.org-create"))
    }

    @Test
    fun `조직 감사 로그와 게시판 목록은 조직 쪽 틀로 범위를 좁힌다(A-577)`() {
        assertNotNull(doc("organization/audit.html").selectFirst("div.org-audit"), "감사 목록 조각은 사용자와 사이트도 쓰므로 조직 쪽 틀 안에서만 단추 색을 바꾼다")
        assertNotNull(doc("organization/board-list.html").selectFirst(".search-wrap.org-list-filter"))
    }

    @Test
    fun `그룹 삭제는 프로젝트 삭제처럼 위험 구역 틀과 이름 재입력 확인 창을 쓴다(A-578)`() {
        val d = doc("organization/delete-form.html")
        assertNotNull(d.selectFirst("section#danger-delete.danger-section h3.danger-title"))
        assertNotNull(d.selectFirst("section#danger-delete .cu-desc p"), "무엇이 지워지는지 설명이 있다")
        assertNotNull(d.selectFirst("section#danger-delete .danger-actions a#btnDelete.ybtn-danger[data-bs-toggle=modal]"))
        val modal = d.selectFirst("#alertDeletion")!!
        assertNotNull(modal.selectFirst("h3")); assertNotNull(modal.selectFirst(".modal-body p")); assertNotNull(modal.selectFirst("input#deleteConfirmName"))
        assertTrue(modal.selectFirst("button#btnDeleteExec")!!.hasAttr("disabled"), "이름을 입력하기 전에는 실행 단추가 꺼져 있다")
        assertTrue(text("organization/delete-form.html").contains("organization.Delete.js"), "삭제 요청은 모듈이 한다")
    }

    @Test
    fun `조직의 OAuth 화면은 조직 쪽 틀 안에 있고 앱 목록은 카드이다(A-579)`() {
        for (f in listOf("organization/oauth-apps.html", "organization/oauth-app-form.html", "organization/oauth-app-secret.html"))
            assertNotNull(doc(f).selectFirst(".two-factor-page.org-oauth"), "$f: 조직 쪽 틀")
        val partial = text("oauth/partial-apps.html")
        assertTrue(partial.contains("oauth-cards") && partial.contains("mine(apps, urls, userAppsEnabled, titleKey, emptyKey, cards)"), "카드 목록은 조직만 켜는 매개변수이다")
        assertTrue(text("user/edit-oauth-apps.html").contains("'oauth.apps.mine.empty', false)"), "사용자 설정의 목록은 표 그대로이다")
        assertTrue(text("organization/oauth-apps.html").contains("'oauth.org.apps.empty', true)"))
        assertTrue(partial.contains("tf-scopes-title"), "범위 제목은 들여쓰기 규칙을 맞추기 위한 전용 클래스가 있다")
    }
}
