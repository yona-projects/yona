package dev.yona.markdown

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

/**
 * v1.6이 쓰던 `marked.js`를 GraalJS에서 같은 옵션으로 실행하는 참조 구현이다(테스트 전용, D-17).
 *
 * v1.6 `utils.Markdown`은 옵션을 `{gfm: true, tables: true, breaks: true, pedantic: false, sanitize: false, smartLists: true}`로
 * 고정해 `marked(source, options)`를 호출했다(`headerIds`는 기본값 true). `commonmark-java` 결과와 이 결과를 비교해
 * v1.6과 달라지는 부분을 드러낸다.
 */
object MarkedReference {

    private val context: Context by lazy {
        Context.newBuilder("js").allowHostAccess(HostAccess.NONE).build().also { ctx ->
            val script = checkNotNull(javaClass.getResourceAsStream("/marked.js")) { "marked.js 리소스가 없습니다." }
                .readBytes().toString(Charsets.UTF_8)
            ctx.eval("js", script)
        }
    }

    private val render: Value by lazy {
        context.eval(
            "js",
            """(function(source) {
                 return marked(source, {gfm: true, tables: true, breaks: true, pedantic: false, sanitize: false, smartLists: true});
               })"""
        )
    }

    /** GraalJS 컨텍스트는 스레드 안전하지 않으므로 호출을 직렬화한다. */
    @Synchronized
    fun render(markdown: String): String = render.execute(markdown).asString()
}
