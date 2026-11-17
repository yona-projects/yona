package dev.yona.domain.upgrade

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `tools/h2-export/H2Export.java`를 검증한다. v1.6의 H2 파일(H2 1.3 PageStore)을 옛 H2 jar로 읽어 데이터 가져오기 형식의 JSON으로 쓰는 변환기이다.
 * 옛 H2는 시험 클래스패스의 H2 2.x와 충돌하므로 Gradle의 별도 설정(`h2Legacy`)으로 받아 하위 프로세스에서 실행한다. 옛 DB는 시험용 스키마(`h2-export/v16-fixture.sql`)로 만든다.
 * 시각은 JVM 시간대로 읽으므로 UTC로 고정해 결과를 결정적으로 한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class H2ExportToolTest {

    /** 시험 전에 한 번 만드는 작업 폴더이다(`@TempDir` 필드는 `@BeforeAll`보다 늦게 채워지므로 직접 만들고 끝나면 지운다). */
    private lateinit var work: File
    private lateinit var legacyJar: String
    private lateinit var classes: File
    private lateinit var oldDb: File
    private val javaBin = File(System.getProperty("java.home"), "bin/java").path
    private val mapper = JsonMapper.builder().build()

    @BeforeAll
    fun prepare() {
        work = Files.createTempDirectory("h2-export-test").toFile()
        legacyJar = System.getProperty("yona.test.h2legacy").orEmpty()
        assumeTrue(legacyJar.isNotBlank() && File(legacyJar).isFile, "옛 H2 jar(h2Legacy)가 없어 건너뛴다")
        // 1) 변환기를 컴파일한다(Java 8 호환 소스)
        classes = File(work, "classes").also { it.mkdirs() }
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "JDK가 필요하다" }
        check(compiler.run(null, null, null, "-encoding", "UTF-8", "--release", "8", "-d", classes.path, "tools/h2-export/H2Export.java") == 0) { "H2Export 컴파일 실패" }
        // 2) 옛 형식의 DB를 만든다
        oldDb = File(work, "v16").also { it.mkdirs() }
        val script = File("src/test/resources/h2-export/v16-fixture.sql").absolutePath
        val result = run(listOf(javaBin, "-Duser.timezone=UTC", "-cp", legacyJar, "org.h2.tools.RunScript", "-url", "jdbc:h2:file:${File(oldDb, "yona").path}", "-user", "", "-password", "", "-script", script, "-options", "CHARSET", "'UTF-8'"))
        check(result.first == 0 && File(oldDb, "yona.h2.db").isFile) { "옛 DB를 만들지 못했다: ${result.second}" }
    }

    @AfterAll
    fun cleanup() { if (::work.isInitialized) work.deleteRecursively() }

    private fun run(command: List<String>): Pair<Int, String> {
        val output = File(work, "run-${System.nanoTime()}.log")
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output).start()
        check(process.waitFor(120, TimeUnit.SECONDS)) { "시간 초과: $command" }
        return process.exitValue() to output.readText()
    }

    private fun export(db: File, out: File): Pair<Int, String> =
        run(listOf(javaBin, "-Duser.timezone=UTC", "-cp", "${classes.path}${File.pathSeparator}$legacyJar", "H2Export", db.path, out.path))

    private fun md5(file: File) = MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun exported(): JsonNode {
        val out = File(work, "out-${System.nanoTime()}.json")
        val result = export(File(oldDb, "yona.h2.db"), out)
        assertEquals(0, result.first, result.second)
        return mapper.readTree(out.readText(Charsets.UTF_8))
    }

    private fun epoch(local: LocalDateTime) = local.toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `테이블마다 행의 배열이고 행의 키는 소문자 컬럼 이름이다`() {
        val json = exported()
        assertEquals(2, json["N4USER"].size())
        assertEquals(2, json["ISSUE"].size())
        assertEquals(1, json["ISSUE_VOTER"].size())
        assertEquals(0, json["EMPTY_THING"].size(), "빈 테이블은 빈 배열이다")
        assertEquals(setOf("id", "login_id", "name", "is_guest", "created_date", "state", "token"), json["N4USER"][0].propertyNames().toSet())
    }

    @Test
    fun `값은 정수는 숫자, 시각과 날짜는 에폭 밀리초, 불린은 참과 거짓, 문자열과 긴 문자열은 문자열이다`() {
        val admin = exported()["N4USER"][0]
        assertEquals(1L, admin["id"].longValue())
        assertEquals("admin", admin["login_id"].asString())
        assertTrue(admin["is_guest"].booleanValue())
        assertEquals(epoch(LocalDateTime.of(2026, 1, 2, 3, 4, 5)), admin["created_date"].longValue())
        assertEquals(1, admin["state"].intValue())
        assertEquals("tok-1", admin["token"].asString(), "옛 사용자 토큰도 파일에 담긴다(내보내기 파일에는 없던 값이다)")
        val issue = exported()["ISSUE"][0]
        assertEquals(2, issue["state"].intValue(), "TINYINT도 숫자이다")
        assertEquals(7, issue["level_no"].intValue(), "SMALLINT도 숫자이다")
        assertEquals(LocalDate.of(2026, 3, 4).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), issue["due_date"].longValue(), "DATE는 에폭 밀리초이다")
    }

    @Test
    fun `DB의 NULL은 JSON null이고 불린의 NULL만 거짓이다 (v1_6 내보내기와 같다)`() {
        val kim = exported()["N4USER"][1]
        assertTrue(kim["created_date"].isNull)
        assertTrue(kim["token"].isNull)
        assertTrue(kim["is_guest"].isBoolean && !kim["is_guest"].booleanValue(), "불린의 NULL은 false이다")
        val second = exported()["ISSUE"][1]
        assertTrue(second["body"].isNull)
        assertTrue(second["due_date"].isNull)
        assertEquals(9L, second["milestone_id"].longValue())
    }

    @Test
    fun `줄바꿈, 따옴표, 역슬래시, 한글, 이모지가 든 본문을 그대로 옮긴다`() {
        val issue = exported()["ISSUE"][0]
        assertEquals("첫 이슈", issue["title"].asString())
        assertEquals("line1\na \\ b \"q\" 한글 😀", issue["body"].asString())
        assertEquals("한글 \"사용자\"", exported()["N4USER"][1]["name"].asString())
    }

    @Test
    fun `시퀀스가 있는 테이블은 가장 큰 번호 더하기 1을 시퀀스 필드로 쓰고 없는 테이블에는 쓰지 않는다`() {
        val json = exported()
        assertEquals(6L, json["N4USER_SEQ"].longValue(), "가장 큰 번호 5 + 1")
        assertEquals(4L, json["ISSUE_SEQ"].longValue(), "가장 큰 번호 3 + 1")
        assertEquals(1L, json["EMPTY_THING_SEQ"].longValue(), "행이 없으면 1")
        assertFalse(json.has("ISSUE_VOTER_SEQ"), "시퀀스가 없는 조인 테이블에는 시퀀스 필드가 없다")
    }

    @Test
    fun `원본 파일은 바뀌지 않고 옆에 잠금 파일도 남지 않는다`() {
        val before = md5(File(oldDb, "yona.h2.db"))
        val filesBefore = oldDb.list()!!.toSet()
        exported()
        assertEquals(before, md5(File(oldDb, "yona.h2.db")), "원본이 바뀌었다")
        assertEquals(filesBefore, oldDb.list()!!.toSet(), "원본 옆에 파일이 생겼다")
    }

    @Test
    fun `확장자를 뺀 경로도 받고 없는 파일은 2, 이미 H2 2점대 형식이면 3으로 끝낸다`() {
        val out = File(work, "noext.json")
        assertEquals(0, export(File(oldDb, "yona"), out).first)
        assertNotNull(mapper.readTree(out.readText(Charsets.UTF_8))["ISSUE"])
        assertEquals(2, export(File(work, "없는/yona.h2.db"), File(work, "x.json")).first)
        val v2 = File(work, "v2").also { it.mkdirs() }.also { File(it, "yona.mv.db").writeText("x") }
        val result = export(File(v2, "yona.h2.db"), File(work, "y.json"))
        assertEquals(3, result.first, result.second)
        assertTrue(result.second.contains("H2 2.x"), result.second)
    }
}
