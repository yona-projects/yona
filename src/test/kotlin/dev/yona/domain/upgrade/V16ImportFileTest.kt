package dev.yona.domain.upgrade

import dev.yona.domain.site.DataService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 이관 명령의 "가져오기 파일" 단계([V16ImportFile])를 검증한다. v1.6의 H2 파일은 H2 2.x가 읽지 못하므로 옛 H2로 읽어 데이터 가져오기 형식의 JSON으로 바꾼 뒤(`tools/h2-export`),
 * 이관 명령이 그 파일을 먼저 가져온다. 가져오기는 테이블을 비우고 다시 채우므로 시작 상태를 저장했다가 끝나면 되돌린다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-v16-import-file"])
class V16ImportFileTest : AbstractIntegrationTest() {

    @Autowired private lateinit var importFile: V16ImportFile
    @Autowired private lateinit var service: DataService
    @Autowired private lateinit var migration: V16DataMigration

    @TempDir lateinit var work: File
    private lateinit var baseline: ByteArray

    @BeforeEach
    fun saveBaseline() { baseline = ByteArrayOutputStream().also { service.export(it) }.toByteArray() }

    @AfterEach
    fun restoreBaseline() { service.import(ByteArrayInputStream(baseline)) }

    @Test
    fun `내보내기 모양의 파일을 가져오고 가져온 테이블을 돌려준다`() {
        val file = File(work, "export.json").also { it.writeBytes(baseline) }
        val report = importFile.import(file.path)
        assertTrue(report.imported.isNotEmpty(), "가져온 테이블이 있어야 한다")
        assertTrue(report.ignored.isEmpty(), "v2가 만든 파일에는 무시한 테이블이 없어야 한다: ${report.ignored}")
    }

    @Test
    fun `v2가 모르는 테이블은 가져오지 않고 행이 있는 것만 알린다`() {
        // 옛 DB에는 v2가 쓰지 않는 테이블(스키마 변경 기록, 쓰이지 않는 연결 테이블)이 있다. 조용히 버리지 않고 행이 있는 것을 알린다
        val json = String(baseline, Charsets.UTF_8).trimEnd().removeSuffix("}") + ""","PLAY_EVOLUTIONS":[{"id":1},{"id":2}],"ISSUE_EXPLICIT_WATCHER":[]}"""
        val file = File(work, "with-unknown.json").also { it.writeText(json) }
        val report = importFile.import(file.path)
        assertEquals(mapOf("PLAY_EVOLUTIONS" to 2), report.ignored, "행이 있는 모르는 테이블만, 빈 테이블은 알리지 않는다")
    }

    @Test
    fun `파일에 든 옛 사용자 토큰은 가져온 뒤 보정이 새 토큰 표로 옮겨 같은 값이 계속 동작한다`() {
        // 내보내기 파일에는 없던 n4user.token을 옛 H2를 직접 읽는 변환기는 파일에 담는다. 가져오기가 그 값을 읽고 보정(2단계)이 api_token으로 옮기는지 확인한다
        val legacy = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789+/AbCdEf="
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val tree = mapper.readTree(baseline) as tools.jackson.databind.node.ObjectNode
        val users = tree.get("N4USER") as tools.jackson.databind.node.ArrayNode
        assertTrue(users.size() > 0, "사용자가 하나는 있어야 한다")
        (users.get(0) as tools.jackson.databind.node.ObjectNode).put("token", legacy)
        val file = File(work, "with-token.json").also { it.writeText(mapper.writeValueAsString(tree)) }
        importFile.import(file.path)
        assertEquals(1, migration.pending().legacyTokens, "가져온 사용자 토큰이 보정 대상이어야 한다")
        assertEquals(1, migration.migrate().tokensMigrated)
        assertEquals(0, migration.pending().legacyTokens)
    }

    @Test
    fun `없는 파일은 경로를 알려 주고 실패한다`() {
        val missing = File(work, "없는-파일.json").path
        val error = assertFailsWith<V16ImportException> { importFile.import(missing) }
        assertTrue(error.message!!.contains(missing), error.message)
    }

    @Test
    fun `가져오지 못한 테이블이 있으면 어느 테이블인지 알리며 실패한다`() {
        // PROJECT의 필수 값이 없는 행이라 그 테이블의 가져오기가 실패한다
        val file = File(work, "broken.json").also { it.writeText("""{"PROJECT":[{"id":1}]}""") }
        val error = assertFailsWith<V16ImportException> { importFile.import(file.path) }
        assertTrue(error.message!!.contains("PROJECT"), error.message)
        assertEquals(listOf("PROJECT"), error.failed.map { it.uppercase() })
    }
}
