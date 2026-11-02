package dev.yona.domain.sshkey

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** 시험용 SSH 서명자이다. 실제 `ssh-keygen -Y sign`(네임스페이스 `git`)으로 서명하므로 `git`이 `gpg.format=ssh`로 만드는 서명과 같다. */
object TestSshSigner {

    /** `ssh-keygen`이 만든 키 한 벌이다. [file]은 개인키 경로이고 공개키는 `$file.pub`이다. */
    class Key(val file: File) {
        val publicLine: String get() = File(file.path + ".pub").readText().trim()
    }

    fun newKey(dir: File, name: String = "key-${System.nanoTime()}"): Key {
        val file = File(dir, name)
        val process = ProcessBuilder("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "signer", "-f", file.path).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "ssh-keygen 실패: $output" }
        return Key(file)
    }

    /** [data]에 서명한 ASCII armor(`-----BEGIN SSH SIGNATURE-----`...)를 돌려준다. */
    fun sign(key: Key, data: ByteArray): String {
        val work = Files.createTempDirectory("sshsig").toFile()
        try {
            val input = File(work, "msg").also { it.writeBytes(data) }
            val private = File(work, "key").also { key.file.copyTo(it) }
            Files.setPosixFilePermissions(private.toPath(), PosixFilePermissions.fromString("rw-------"))
            val process = ProcessBuilder("ssh-keygen", "-Y", "sign", "-n", "git", "-f", private.path, input.path).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
            check(process.waitFor() == 0) { "ssh-keygen -Y sign 실패: $output" }
            return File(input.path + ".sig").readText()
        } finally {
            work.deleteRecursively()
        }
    }
}
