package dev.yona.web

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * JS 전역 이름 `yobi`와 `$yobi`를 `yona`와 `$yona`로 바꾼 일(D-93)을 지키는 시험이다.
 *
 * 바뀐 것은 JS 이름과 `yobi.*.js` 파일 이름이다. 바뀌지 않은 것은 CSS 파일(`yobi.css`), 아이콘 글꼴(`yobicon`),
 * 메시지 키(`title.yobi.feedback`), 외부 주소(`yobi.io`, `yobi.navercorp.com`)이다. 쿠키, 인증 영역, 웹훅 User-Agent, 백업 파일 이름,
 * 숨은 참조 접두어는 D-93 보완(선생님 지시)으로 `yona`가 되었다.
 */
class YobiNamespaceRemovedTest {
    private val assets = File("src/main/resources/static/assets/javascripts")
    private val templates = File("src/main/resources/templates")

    /** 옛 이름이 남았는지 보는 대상: 템플릿, 직접 쓴 JS 폴더 세 곳, 번들. 벤더(`lib`)는 보지 않는다. */
    private fun sources(): List<File> =
        (listOf(templates) + listOf("common", "service", "esm", "template").map { File(assets, it) })
            .flatMap { it.walkTopDown().filter { f -> f.isFile && (f.extension == "html" || f.extension == "js") }.toList() } +
            listOf("yona-layout.js", "yona-common.js").map { File(assets, it) }

    private val dollar = Regex("""\${'$'}yobi\b""")
    // `yobi.io`, `yobi.css`, `yobi.token` 같은 바뀌지 않는 것과 `title.yobi.feedback`처럼 앞에 이름이 붙은 것은 뺀다.
    private val namespace = Regex("""(?<![\w.\-/#{'"])yobi\.(?!io\b|css|scss|navercorp|feedback\b)[A-Za-z]""")
    private val fileRef = Regex("""(common|service|template)/yobi\.""")

    @Test
    fun `직접 쓴 JS와 템플릿과 번들에 옛 이름이 남지 않는다`() {
        val left = sources().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                if (dollar.containsMatchIn(line) || namespace.containsMatchIn(line) || fileRef.containsMatchIn(line)) "${f.path}:${i + 1}" else null
            }
        }
        assertEquals(emptyList<String>(), left.take(20), "yobi 이름이 남은 곳(전체 ${left.size}곳)")
    }

    @Test
    fun `yobi 점 이름의 JS 파일이 없다`() {
        val left = listOf("common", "service", "esm", "template").flatMap { d ->
            File(assets, d).listFiles { f -> f.name.startsWith("yobi.") }?.map { it.path } ?: emptyList()
        }
        assertEquals(emptyList<String>(), left, "yona.*.js로 바뀌어야 한다")
    }

    @Test
    fun `바뀌지 않아야 하는 것은 그대로다`() {
        assertTrue(File(templates, "layout/layout.html").readText().contains("stylesheets/yobi.css"), "CSS 파일 이름 yobi.css는 그대로다(빌드가 만든다)")
        assertTrue(File("src/main/kotlin/dev/yona/security/YonaAuthenticationFilter.kt").readText().contains("\"yona.token\""), "로그인 유지 쿠키 이름은 yona.token이다")
    }
}
