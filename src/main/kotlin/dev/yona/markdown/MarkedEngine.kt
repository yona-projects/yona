package dev.yona.markdown

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory
import javax.script.Invocable
import javax.script.ScriptEngine
import org.springframework.stereotype.Component

/**
 * v1.6이 쓰던 `public/javascripts/lib/marked.js`를 JVM 안의 JavaScript 엔진(Nashorn)에서 그대로 실행해 마크다운을 HTML로 바꾼다.
 *
 * v1.6 `utils.Markdown`은 같은 파일을 JDK 8의 Nashorn에서 옵션 `{gfm, tables, breaks: true, pedantic: false, sanitize: false, smartLists}`로
 * 실행했다. 다른 마크다운 구현(commonmark 등)은 강조 겹침 순서, 줄바꿈, 제목 번호 같은 세부 결과가 달라 공식 v1.16.0과 같은 화면을 낼 수 없었다.
 * Nashorn은 JDK 15에서 JDK 밖으로 나왔으므로 독립 배포판(`org.openjdk.nashorn:nashorn-core`)을 쓴다.
 *
 * 엔진은 한 번만 만들고(처음 쓸 때) 스크립트 엔진이 스레드 안전하지 않으므로 호출을 직렬화한다.
 */
@Component
class MarkedEngine {

    private val engine: ScriptEngine by lazy {
        NashornScriptEngineFactory().scriptEngine.also { engine ->
            // v1.6이 쓴 JDK 8의 Nashorn은 ES5.1 규칙이라 `splice(시작)`처럼 인자가 하나면 아무것도 지우지 않는다(deleteCount가 0).
            // 독립 배포판은 ES2015 규칙(끝까지 지움)이라 표 한 줄의 남는 칸 처리가 달라지므로 ES5 동작으로 맞춘다.
            engine.eval(ES5_SPLICE)
            val script = checkNotNull(javaClass.getResourceAsStream(SCRIPT_RESOURCE)) { "$SCRIPT_RESOURCE 리소스가 없습니다." }
                .readBytes().toString(Charsets.UTF_8)
            engine.eval(script)
        }
    }

    /**
     * 마크다운 [source]를 정화 전의 HTML로 바꾼다. 빈 문자열은 그대로 돌려준다.
     * 옵션은 v1.6과 같다(`headerIds`는 기본값 true이다).
     */
    @Synchronized
    fun render(source: String): String {
        if (source.isEmpty()) return source
        val options = engine.eval(OPTIONS)
        return try {
            (engine as Invocable).invokeFunction("marked", source, options) as String
        } catch (e: javax.script.ScriptException) {
            // v1.6 `renderByMarked`는 변환 중 스크립트 오류가 나면 기록하고 원문을 이스케이프해 `<pre>`로 감싸 돌려준다
            log.error("[Markdown] Failed to render: {}", source, e)
            "<pre>" + org.apache.commons.lang.StringEscapeUtils.escapeHtml(source) + "</pre>"
        }
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(MarkedEngine::class.java)
        const val SCRIPT_RESOURCE = "/markdown/marked.js"
        const val ES5_SPLICE = "(function(){var s=Array.prototype.splice;Array.prototype.splice=function(a){return arguments.length===1?s.call(this,a,0):s.apply(this,arguments);};})();"
        const val OPTIONS = "new Object({gfm: true, tables: true, breaks: true, headerIds: true, pedantic: false, sanitize: false, smartLists: true})"
    }
}
