package dev.yona.domain.upgrade

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 설정 변환 명령의 입출력, 덮어쓰기 방지, 파일 권한, 사용법 오류를 검증한다. */
class V16ConfigMainTest {

    @TempDir
    lateinit var dir: Path

    private fun conf(text: String) = dir.resolve("application.conf").also { Files.writeString(it, text) }

    @Test
    fun `변환한 파일을 소유자 전용 권한으로 쓰고 보고를 출력한다`() {
        val input = conf("application.secret = \"s\"\nmy.custom.key = 1\napplication.use.social.login.only = false")
        val output = dir.resolve("out.properties")
        val lines = mutableListOf<String>()
        assertEquals(0, V16ConfigMain.run(listOf(input.toString(), output.toString()), lines::add))
        assertTrue(Files.readString(output).contains("yona.secret=s"))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(output)))
        assertTrue(lines.any { "my.custom.key" in it }, "알 수 없는 키를 알려야 한다")
        assertTrue(lines.any { "application.use.social.login.only" in it }, "없어진 기능을 알려야 한다")
        assertTrue(lines.any { "yona.secret" in it }, "비밀 키가 들어 있음을 알려야 한다")
    }

    @Test
    fun `출력이 이미 있으면 덮어쓰지 않고 force를 주면 덮어쓴다`() {
        val input = conf("application.siteName = \"A\"")
        val output = dir.resolve("out.properties").also { Files.writeString(it, "old") }
        assertEquals(1, V16ConfigMain.run(listOf(input.toString(), output.toString()), {}))
        assertEquals("old", Files.readString(output))
        assertEquals(0, V16ConfigMain.run(listOf(input.toString(), output.toString(), "--force"), {}))
        assertTrue(Files.readString(output).contains("yona.site-name=A"))
    }

    @Test
    fun `입력이 없거나 인자가 모자라면 실패 코드를 돌려준다`() {
        assertEquals(1, V16ConfigMain.run(listOf(dir.resolve("none.conf").toString(), dir.resolve("o").toString()), {}))
        assertEquals(2, V16ConfigMain.run(listOf("only-one"), {}))
    }
}
