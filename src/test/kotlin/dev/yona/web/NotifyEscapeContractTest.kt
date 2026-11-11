package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 알림창(`$yona.alert`)과 알림 토스트(`$yona.notify`)는 넘긴 글을 HTML로 해석한다. 라벨 이름, 프로젝트 이름, 사용자 이름처럼 사용자가 정한 값을 그대로 이어 붙여 넘기면
 * 그 값에 든 태그가 실행된다(shell-F27, 선생님 결정 2026-11-11: 사용자 값을 넘기는 호출부만 `$yona.escapeHtml`로 감싼다). 호출부를 소스에서 확인한다.
 */
class NotifyEscapeContractTest {

    private val js = File("src/main/resources/static/assets/javascripts/esm")

    private fun source(path: String) = File(js, path).readText()

    @Test
    fun `사용자 값을 이어 붙여 알리는 호출부는 escapeHtml로 감싼다`() {
        val sites = mapOf(
            "TitleHeadAutoCompletion.js" to "\$yona.notify('Label: ' + \$yona.escapeHtml(item.name)",
            "Subtask.js" to "\$yona.escapeHtml(targetProjectName)",
            "service/detectChange.js" to "\$yona.escapeHtml(data.commentAuthorName)",
            "service/issue.Assginee.js" to "\$yona.escapeHtml(response.assignee.name)",
            "service/pullrequest.Meta.js" to "\$yona.escapeHtml(response.assignee.name)",
            "service/issue.Sharer.js" to "\$yona.escapeHtml(response.sharer)"
        )
        for ((file, expected) in sites) assertTrue(source(file).contains(expected), "$file: 사용자 값이 escapeHtml 없이 알림에 들어간다 ($expected)")
        assertTrue(Regex("escapeHtml\\(failureReason\\(failure\\)\\)").findAll(source("Usermenu.js")).count() == 3, "Usermenu.js: 실패 이유는 서버 본문이라 escapeHtml로 감싼다")
    }

    @Test
    fun `템플릿 안 스크립트도 서버 응답 글을 escapeHtml로 감싸 알린다`() {
        val templates = File("src/main/resources/templates")
        for (file in listOf("index/notifications.html", "issue/my-list.html", "layout/migration-layout.html", "site/user-list.html")) {
            val text = File(templates, file).readText()
            val raw = Regex("\\\$yona\\.(alert|notify)\\(\"[^\"]*\" \\+ (data|JSON\\.parse)[^)]*\\)").findAll(text).filterNot { it.value.contains("escapeHtml") }.toList()
            assertTrue(raw.isEmpty(), "$file: 서버 응답을 그대로 알린다 ${raw.map { it.value }}")
        }
    }
}
