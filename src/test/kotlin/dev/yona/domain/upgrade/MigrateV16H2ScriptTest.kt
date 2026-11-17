package dev.yona.domain.upgrade

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `scripts/migrate-v16-h2.sh`의 입력 검증을 확인한다. 변환기를 돌리거나 서버를 띄우기 전에 잘못된 입력을 알아차려야 하며(종료 코드 2, 아무것도 바꾸지 않음), DB를 비우고 교체하는
 * 동작이라 `--yes` 없이는 시작하지 않는다. 실제 변환과 가져오기는 [H2ExportToolTest]와 [V16ImportFileTest]가, 전체 흐름은 실제 v1.6 파일로 한 리허설이 확인했다.
 */
class MigrateV16H2ScriptTest {

    @TempDir lateinit var work: File

    private val script = File("scripts/migrate-v16-h2.sh").absolutePath

    /** 스크립트를 실행하고 (종료 코드, 출력)을 돌려준다. 입력은 터미널이 아니다. */
    private fun run(vararg args: String): Pair<Int, String> {
        val output = File(work, "out-${System.nanoTime()}.log")
        val process = ProcessBuilder(listOf("bash", script) + args).redirectErrorStream(true).redirectOutput(output).redirectInput(ProcessBuilder.Redirect.from(File("/dev/null"))).start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과" }
        return process.exitValue() to output.readText()
    }

    private val jar get() = File(work, "yona.jar").also { it.writeText("not a real jar") }.path

    /** v1.6 설치 폴더 모양: `yona.h2.db`와 `lib/com.h2database.h2-1.3.176.jar`. */
    private fun install(h2: Boolean = true, lib: Boolean = true, mv: Boolean = false): File = File(work, "v16-${System.nanoTime()}").also { dir ->
        File(dir, "lib").mkdirs()
        if (h2) File(dir, "yona.h2.db").writeText("x")
        if (mv) File(dir, "yona.mv.db").writeText("x")
        if (lib) File(dir, "lib/com.h2database.h2-1.3.176.jar").writeText("x")
    }

    @Test
    fun `인자가 없거나 jar가 없으면 사용법을 알리고 2로 끝난다`() {
        val none = run()
        assertEquals(2, none.first); assertTrue(none.second.contains("사용법"), none.second)
        assertEquals(2, run("/없는/yona.jar", "--v16-dir=${install().path}").first)
    }

    @Test
    fun `v1_6 폴더도 H2 파일도 주지 않으면 2로 끝난다`() {
        val result = run(jar)
        assertEquals(2, result.first); assertTrue(result.second.contains("--v16-dir") && result.second.contains("--h2-file"), result.second)
    }

    @Test
    fun `H2 파일이 없으면 경로를 알리고 2로 끝난다`() {
        val dir = install(h2 = false)
        val result = run(jar, "--v16-dir=${dir.path}", "--yes")
        assertEquals(2, result.first); assertTrue(result.second.contains("${dir.path}/yona.h2.db"), result.second)
    }

    @Test
    fun `이미 H2 2점대 형식이면 변환이 필요 없다고 알리고 2로 끝난다`() {
        val result = run(jar, "--v16-dir=${install(h2 = false, mv = true).path}", "--yes")
        assertEquals(2, result.first); assertTrue(result.second.contains("H2 2.x"), result.second)
    }

    @Test
    fun `옛 H2 jar가 없으면 알리고 2로 끝난다`() {
        val result = run(jar, "--v16-dir=${install(lib = false).path}", "--yes")
        assertEquals(2, result.first); assertTrue(result.second.contains("옛 H2 jar"), result.second)
    }

    @Test
    fun `yes 없이 터미널이 아니면 대상 DB를 비우기 전에 멈춘다`() {
        val result = run(jar, "--v16-dir=${install().path}")
        assertEquals(2, result.first); assertTrue(result.second.contains("--yes"), result.second)
    }
}
