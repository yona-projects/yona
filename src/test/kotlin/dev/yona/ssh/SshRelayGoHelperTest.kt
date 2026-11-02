package dev.yona.ssh

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 시스템 sshd 연동을 셸 훅 대신 **Go 실행 파일**(`tools/ssh-helper`, `yona-ssh auth`와 `yona-ssh relay`, D-73)로 검증한다. [SshRelayServerTest]의 시험을 모두 그대로 되풀이하므로
 * 셸 훅과 같은 동작(등록한 키의 복제와 푸시, 거부, 배포 키, 브랜치 보호, 감사 기록)임을 같은 시험으로 확인한다. Go가 없는 환경에서는 건너뛴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SshRelayGoHelperTest : SshRelayServerTest() {

    private lateinit var binary: File

    @BeforeAll
    fun buildHelper() {
        val go = listOf("go", "version").let { runCatching { ProcessBuilder(it).redirectErrorStream(true).start().also { p -> p.inputStream.readBytes() }.waitFor() == 0 }.getOrDefault(false) }
        assumeTrue(go, "Go 도구가 없어 건너뛴다")
        binary = File("build/tmp/yona-ssh-helper/yona-ssh").absoluteFile.also { it.parentFile.mkdirs() }
        val process = ProcessBuilder("go", "build", "-o", binary.path, ".").directory(File("tools/ssh-helper")).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(120, TimeUnit.SECONDS) && process.exitValue() == 0) { "go build 실패: $output" }
    }

    override fun hook(): String = "${binary.path} auth"
}
