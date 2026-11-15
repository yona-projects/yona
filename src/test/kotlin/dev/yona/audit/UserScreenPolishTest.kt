package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사용자 설정 화면 다듬기(A-581~A-584, 선생님 지시)의 화면 구조를 지키는 구조 검사이다. 템플릿 파일을 직접 읽는다.
 */
class UserScreenPolishTest {

    private fun text(path: String) = File("src/main/resources/templates/$path").readText()
    private fun doc(path: String) = Jsoup.parse(text(path))
    private fun messages(file: String) = File("src/main/resources/$file").readLines().filter { it.contains(" = ") }.associate { it.substringBefore(" = ").trim() to it.substringAfter(" = ") }

    private val settingPages = listOf("edit", "edit-password", "edit-emails", "edit-linked-accounts", "edit-notifications", "edit-token", "edit-token-new", "edit-token-edit", "edit-ssh", "edit-ssh-new",
        "edit-gpg", "edit-gpg-new", "edit-two-factor", "edit-two-factor-setup", "edit-two-factor-key", "edit-two-factor-codes", "edit-audit",
        "edit-oauth-apps", "edit-oauth-app-form", "edit-oauth-app-secret")

    @Test
    fun `사용자 설정 화면은 모두 같은 틀 안에서 꽉 찬 단추를 테마색으로 쓴다(A-581)`() {
        for (p in settingPages) assertNotNull(doc("user/$p.html").selectFirst(".page-wrap.shell.user-screen"), "user/$p.html: 사용자 설정 공통 틀")
    }

    @Test
    fun `토큰, SSH 키, GPG 키, 연결된 계정 목록은 표가 아니라 카드이다(A-581)`() {
        for ((f, id, row) in listOf(Triple("edit-token", "token-list", "token-row"), Triple("edit-ssh", "ssh-list", "ssh-row"), Triple("edit-gpg", "gpg-list", "gpg-row"), Triple("edit-linked-accounts", "linked-list", "linked-row"))) {
            val d = doc("user/$f.html")
            assertNotNull(d.selectFirst("ul#$id.item-cards li.item-card.$row"), "$f: 카드 목록")
            assertNull(d.selectFirst("table#$id"), "$f: 표는 없다")
        }
    }

    @Test
    fun `연결된 계정 설명은 싱글 사인온만 말하고 소셜 로그인은 말하지 않는다(A-581)`() {
        for (f in listOf("messages.properties", "messages_ko_KR.properties", "messages_ja_JP.properties", "messages_ru_RU.properties", "messages_uz_UZ.properties")) {
            val v = messages(f)["linked.description"].orEmpty()
            assertTrue(v.isNotBlank(), f)
            for (word in listOf("소셜", "social", "ソーシャル", "социальных", "ijtimoiy")) assertTrue(!v.contains(word, ignoreCase = true), "$f: '$word'이 남아 있다")
        }
    }

    @Test
    fun `새 SSH 키와 새 GPG 키 화면은 키 만드는 방법을 옆에 안내한다(A-582)`() {
        for ((f, form, cmd) in listOf(Triple("edit-ssh-new", "ssh-create", "ssh-keygen -t ed25519"), Triple("edit-gpg-new", "gpg-create", "gpg --full-generate-key"))) {
            val d = doc("user/$f.html")
            assertNotNull(d.selectFirst(".key-new-layout form#$form"), "$f: 양식은 왼쪽 열이다")
            val guide = d.selectFirst(".key-new-layout aside.key-guide")
            assertNotNull(guide, "$f: 안내는 오른쪽 열이다")
            assertTrue(guide!!.select("pre code").size >= 3, "$f: 생성, 확인, 내보내기 명령을 보인다")
            assertTrue(guide.text().contains(cmd), "$f: $cmd")
        }
    }

    @Test
    fun `알림 설정의 프로젝트 목록 위에 검색 상자가 있다(A-582)`() {
        val d = doc("user/edit-notifications.html")
        assertNotNull(d.selectFirst("input#notification-project-filter"))
        assertNotNull(d.selectFirst("#notification-project-empty"))
        assertTrue(text("user/edit-notifications.html").contains("user.NotificationProjects"), "검색은 모듈이 한다")
    }

    @Test
    fun `내 보안 기록에도 검색 양식이 있다(A-582)`() {
        assertTrue(text("user/edit-audit.html").contains("auditList(false, false, true)"), "다른 보안 기록 화면처럼 검색 양식을 보인다")
    }

    @Test
    fun `2단계 인증은 인증 앱과 보안 키(YubiKey, WebAuthn) 두 방식을 설명하는 카드로 고른다(A-583)`() {
        val d = doc("user/edit-two-factor.html")
        val cards = d.select(".tf-method-cards .tf-method-card")
        assertEquals(2, cards.size)
        assertNotNull(d.selectFirst("#method-totp form[action$=\"/user/two-factor/begin\"], #method-totp .tf-method-status"))
        assertNotNull(d.selectFirst("#method-security-key a#security-key-new"))
        for (c in cards) assertNotNull(c.selectFirst(".tf-method-help"), "각 방식에 설명이 있다")
        assertNull(d.selectFirst(".tf-actions .btn-success"), "초록 단추는 없다")
    }

    @Test
    fun `OAuth 앱 이전은 프로젝트 삭제처럼 위험 구역과 이름 재입력 확인 창을 쓴다(A-583)`() {
        val partial = Jsoup.parse(text("oauth/partial-apps.html"))
        assertNotNull(partial.selectFirst("section#oauth-transfer.danger-section"))
        assertNotNull(partial.selectFirst("#oauth-transfer a#btnTransfer[data-bs-toggle=modal], #oauth-transfer button#btnTransfer[data-bs-toggle=modal]"))
        val modal = partial.selectFirst("#alertTransfer")
        assertNotNull(modal)
        assertNotNull(modal!!.selectFirst("input#transferConfirmName"))
        assertTrue(modal.selectFirst("button#btnTransferExec")!!.hasAttr("disabled"))
        assertTrue(text("oauth/partial-apps.html").contains("oauth.Transfer"), "확인 절차는 모듈이 한다")
    }

    @Test
    fun `기기 인증 코드 입력은 가운데 큰 카드이다(A-584)`() {
        val d = doc("user/device.html")
        assertNotNull(d.selectFirst("form#device-code-form .device-card input#device-user-code.device-code-input"))
        assertNotNull(d.selectFirst("#device-consent.device-card"))
    }

    @Test
    fun `OAuth 앱 승인 화면은 가운데 큰 카드이다(A-584)`() {
        val d = doc("oauth2/consent.html")
        val card = d.selectFirst(".consent-card")
        assertNotNull(card)
        assertNotNull(card!!.selectFirst("form#consent-form button#consent-allow"))
        assertNotNull(card.selectFirst("form#consent-deny-form button#consent-deny"))
        assertNotNull(card.selectFirst("ul#oauth-scopes"))
    }

    @Test
    fun `빈 SSH 키 목록은 빈 상태 상자이고 새 키 단추가 상자 안에 있다(A-586)`() {
        val d = doc("user/edit-ssh.html")
        assertNotNull(d.selectFirst("div#ssh-empty.key-empty p.empty"))
        assertNotNull(d.selectFirst("div#ssh-empty.key-empty a#ssh-new"), "단추가 상자 안에 있다")
        assertEquals(2, d.select("a#ssh-new").size, "키가 있을 때의 단추는 목록 아래에 따로 있다")
    }

    @Test
    fun `보안 키 추가 화면은 카드 안에 입력란이 폭을 채운다(A-586)`() {
        val d = doc("user/edit-two-factor-key.html")
        assertNotNull(d.selectFirst(".key-add-card input#security-key-name.w-100"))
        assertNotNull(d.selectFirst(".key-add-card button#security-key-add"))
    }

    @Test
    fun `알림 설정은 선택한 프로젝트 이름을 표 위에 제목으로 보인다(A-586)`() {
        assertNotNull(doc("user/edit-notifications.html").selectFirst("h4#notification-project-title"))
    }

    @Test
    fun `비밀번호 변경은 입력란을 넓히고 재 설정을 눈에 띄는 카드로 보인다(A-586)`() {
        val d = doc("user/edit-password.html")
        assertTrue(d.select("#frmPassword input[type=password]").none { it.hasClass("w-25") }, "좁은 폭 클래스를 쓰지 않는다")
        assertEquals("@{/lostPassword}", d.selectFirst(".pw-reset-card a.pw-reset-btn")?.attr("th:href"))
    }

    @Test
    fun `내 파일 화면은 화면 이름을 제목으로 보인다(A-586)`() {
        assertNotNull(doc("user/files.html").selectFirst(".shell-main h3.shell-page-title"))
    }

    @Test
    fun `오류 화면의 홈 단추와 확인된 사용자의 로그인 단추는 테마 단추이다(A-586)`() {
        for (f in listOf("error/notfound-default.html", "error/badrequest-default.html", "error/forbidden-default.html", "error/internal-server-error-default.html"))
            assertNotNull(doc(f).selectFirst("a.ybtn.ybtn-primary"), "$f: 홈 단추는 테마색(ybtn-primary)이다")
        for (f in listOf("error/notfound-default.html", "error/badrequest-default.html")) assertNull(doc(f).selectFirst("a.ybtn-info"), "$f: 파란 단추가 없다")
        assertEquals("@{/users/loginform}", doc("user/verified.html").selectFirst("a.ybtn.ybtn-primary")?.attr("th:href"), "확인된 사용자에서 로그인으로 간다")
    }

    @Test
    fun `토큰 다시 만들기의 안내와 만료 사이에 여백이 있다(A-586)`() {
        assertNotNull(doc("user/edit-token-edit.html").selectFirst("#token-regenerate p.token-help.mb-3"))
    }

    @Test
    fun `빈 GPG 키 목록도 빈 상태 상자이고 새 키 단추가 상자 안에 있다(A-587)`() {
        val d = doc("user/edit-gpg.html")
        assertNotNull(d.selectFirst("div#gpg-empty.key-empty p.empty"))
        assertNotNull(d.selectFirst("div#gpg-empty.key-empty a#gpg-new"), "단추가 상자 안에 있다")
        assertEquals(2, d.select("a#gpg-new").size, "키가 있을 때의 단추는 목록 아래에 따로 있다")
    }

    @Test
    fun `OAuth 앱의 비밀 값 새로 만들기는 소유자 이전처럼 위험 구역이다(A-588)`() {
        val partial = Jsoup.parse(text("oauth/partial-apps.html"))
        assertNotNull(partial.selectFirst("section#oauth-secret-regenerate.danger-section h3.danger-title"))
        assertNotNull(partial.selectFirst("section#oauth-secret-regenerate .danger-actions button.btn-danger"))
        assertNull(partial.selectFirst("button.btn-secondary"), "회색 단추가 없다")
    }
}

