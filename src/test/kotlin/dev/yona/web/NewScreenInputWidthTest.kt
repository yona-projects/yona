package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * v2에서 새로 만든 화면(v1.6에 대응하는 화면이 없다)의 글 입력란이 폭 클래스를 가졌는지 지킨다(`decisions.md` A-450).
 *
 * `_page.scss`의 A-377 규칙은 폭 클래스(`w-25`, `w-50`, `w-75`, `w-100`, `w-auto`)나 `col-*`, `input-*` 클래스가 없는 글 입력란(`type`이 `text`, `email`, `password`이거나 없는 것)을
 * v1.6처럼 206px로 고정한다. v1.6을 옮긴 화면에는 의도한 모양이지만, 새로 만든 화면은 칸 너비를 채우거나 화면이 정한 최대 너비를 따라야 한다.
 * 의도해서 좁게 두는 입력란(2단계 인증 코드와 보안 키 이름, 로그인 화면)은 [intentionallyNarrow]에 이유와 함께 적는다.
 */
class NewScreenInputWidthTest {

    /** v1.6에 대응 화면이 없는 템플릿이다. */
    private val newScreens = listOf(
        "audit/list", "oauth/partial-apps", "project/deploykey-new", "user/edit-gpg-new", "user/edit-ssh-new", "user/edit-two-factor-key",
        "user/edit-two-factor-setup", "user/device", "user/partial-token-form", "wiki/edit", "wiki/shell", "user/login-two-factor", "site/sso"
    )

    /** 의도해서 206px(또는 전용 좁은 폭)로 두는 입력란이다. 템플릿 이름 to 입력란 식별(id나 class 일부). */
    private val intentionallyNarrow = mapOf(
        "user/edit-two-factor-key" to "security-key-name",   // 짧은 이름. 화면 전용 클래스 tf-narrow(최대 260px)
        "user/edit-two-factor-setup" to "two-factor-code",   // 6자리 코드 입력란
        "user/login-two-factor" to "two-factor-code",        // 로그인 화면의 v1.6 입력란 모양(.login-form-wrap)
        "wiki/shell" to "wiki-clone-url"                      // 입력 묶음(input-group) 안에서 남은 폭을 채운다
    )

    private val widthClasses = setOf("w-25", "w-50", "w-75", "w-100", "w-auto", "title", "project-clone-url", "select2-search__field")

    private fun templates() = File("src/main/resources/templates")

    @Test
    fun `새 화면의 글 입력란은 폭 클래스를 가진다`() {
        val offenders = mutableListOf<String>()
        for (name in newScreens) {
            val text = File(templates(), "$name.html").readText()
            for (tag in Regex("<input\\b[^>]*>").findAll(text).map { it.value }) {
                val classes = Regex("class=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty().split(' ').filter { it.isNotBlank() }
                if ("form-control" !in classes) continue
                val type = Regex("\\btype=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
                if (type != null && type !in setOf("text", "email", "password")) continue
                if (classes.any { it in widthClasses || it.startsWith("col-") || it.startsWith("input-") }) continue
                val id = Regex("\\bid=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()
                if (intentionallyNarrow[name]?.let { key -> id == key || classes.any { it == key } } == true) continue
                offenders += "$name: ${id.ifEmpty { classes.joinToString(".") }}"
            }
        }
        assertEquals(emptyList(), offenders, "폭 클래스가 없어 206px로 고정되는 새 화면의 글 입력란")
    }
}
