package dev.yona.ssh

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 훅 스크립트와 서버가 나누는 공유 비밀 값(X-3b, `decisions.md` D-67)을 검증한다. 설정에 있으면 그 값, 없으면 파일에 한 번 만들어 두고 계속 쓴다. */
class SshInternalSecretTest {

    @TempDir lateinit var dir: File

    private fun secret(relay: YonaProperties.Ssh.Relay) = SshInternalSecret(YonaProperties(dataDir = dir.path, ssh = YonaProperties.Ssh(relay = relay)), dev.yona.repository.DataDirectory(YonaProperties(dataDir = dir.path)))

    @Test
    fun `설정한 비밀 값이 있으면 그 값을 쓰고 파일을 만들지 않는다`() {
        assertEquals("configured", secret(YonaProperties.Ssh.Relay(internalSecret = "configured")).value)
        assertTrue(!File(dir, "ssh/internal-secret").exists())
    }

    @Test
    fun `설정이 없으면 파일에 한 번 만들어 두고 다시 쓴다`() {
        val first = secret(YonaProperties.Ssh.Relay()).value
        assertTrue(first.length >= 32, first)
        assertEquals(first, File(dir, "ssh/internal-secret").readText().trim())
        assertEquals(first, secret(YonaProperties.Ssh.Relay()).value, "다시 시작해도 같은 값")
    }

    @Test
    fun `만든 파일은 소유자만 읽고 쓴다`() {
        secret(YonaProperties.Ssh.Relay()).value
        val permissions = Files.getPosixFilePermissions(File(dir, "ssh/internal-secret").toPath())
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun `파일 경로를 정하면 그 경로를 쓴다`() {
        val path = File(dir, "custom/secret.txt")
        val value = secret(YonaProperties.Ssh.Relay(internalSecretPath = path.path)).value
        assertEquals(value, path.readText().trim())
        assertNotEquals("", value)
    }
}
