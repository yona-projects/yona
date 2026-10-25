package dev.yona.audit

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertEquals

/**
 * v1.6 `conf/routes`의 모든 (메서드, 경로)가 v2 요청 매핑에 있는지 대조한다(동등성 점검 1단계).
 *
 * `audit/v16-routes.txt`는 v1.6 라우트 파일에서 뽑은 스냅샷이다. 경로 매개변수는 이름과 정규식을 버리고 위치만 비교한다.
 * [KNOWN_GAPS]는 아직 이식하지 못했거나 의도적으로 매핑하지 않은 항목이며, 구현할 때마다 여기서 지운다.
 * 목록이 실제 누락과 정확히 같아야 통과하므로 구현했는데 목록에 남아 있어도, 새로 누락돼도 실패한다.
 */
class RouteParityTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mapping: RequestMappingHandlerMapping

    private fun normalize16(path: String) = path
        .replace(Regex("""\$[A-Za-z_]+<[^>]*>"""), "{}")
        .replace(Regex("""[:*][A-Za-z_]+"""), "{}")
        // `/?`는 끝의 슬래시가 있어도 없어도 된다는 뜻이다. 없는 쪽으로 맞춘다.
        .removeSuffix("/?")

    private fun normalize2(path: String) = path
        .replace(Regex("""\{\*?[A-Za-z_]+(:[^{}]*(\{[^}]*\}[^{}]*)*)?\}"""), "{}")

    private fun v2Routes(): Set<Pair<String, String>> = mapping.handlerMethods.keys.flatMap { info ->
        val methods = info.methodsCondition.methods.map { it.name }.ifEmpty { listOf("ANY") }
        val patterns = info.pathPatternsCondition?.patternValues ?: emptySet()
        methods.flatMap { m -> patterns.flatMap { p -> listOf(m to normalize2(p)) + slashForm(m, p) } }
    }.toSet()

    /** `/경로/{*나머지}`는 나머지가 빈 끝 슬래시 주소(`/경로/`)도 처리한다. v1.6은 이를 따로 적었다. */
    private fun slashForm(method: String, pattern: String): List<Pair<String, String>> =
        if (Regex("""/\{\*[A-Za-z_]+\}$""").containsMatchIn(pattern)) listOf(method to normalize2(pattern.replace(Regex("""\{\*[A-Za-z_]+\}$"""), ""))) else emptyList()

    @Test
    fun `v1_6 라우트 중 v2에 없는 것은 알려진 누락 목록과 같다`() {
        val v2 = v2Routes()
        val v16 = javaClass.getResourceAsStream("/audit/v16-routes.txt")!!.bufferedReader().readLines()
            .map { it.split(" ") }.map { it[0] to normalize16(it[1]) }.toSet()
        val missing = v16.filterNot { it in v2 || ("ANY" to it.second) in v2 }.map { "${it.first} ${it.second}" }.sorted()
        assertEquals(KNOWN_GAPS.sorted(), missing)
    }

    companion object {
        /**
         * 의도해서 매핑하지 않는 항목이다.
         * - `/assets/` 아래 모든 경로(GET): 정적 자원 처리기가 맡는다.
         * - `/svn/` 아래 모든 경로: 컨트롤러가 아니라 `SvnDavFilter`가 처리한다(WebDAV 메서드를 디스패처가 501로 거부한다, A-98).
         * - `POST /!svn-fake/sevice/`: v1.6에서 `SvnApp.service`로 역방향 주소를 만들려는 가짜 경로이며 실제 저장소 주소가 아니다.
         */
        private val INTENTIONAL = listOf(
            "GET /assets/{}",
            "GET /svn/{}", "POST /svn/{}", "PUT /svn/{}", "DELETE /svn/{}", "HEAD /svn/{}", "OPTIONS /svn/{}",
            "POST /!svn-fake/sevice/",
        )

        /** 아직 이식하지 못한 항목. */
        private val PENDING = emptyList<String>()

        val KNOWN_GAPS = INTENTIONAL + PENDING
    }
}
