package dev.yona.support

import java.io.File

/**
 * Mercurial 시험이 쓰는 도우미이다. 설치된 실제 `hg`로 저장소를 만들고 커밋해서, hg4j가 읽는 저장소가 실제 Mercurial이 만든 것과 같도록 한다.
 * `hg`가 없으면 [available]이 거짓이다. 시험은 [org.junit.jupiter.api.Assumptions.assumeTrue]로 건너뛴다.
 */
object HgTestSupport {

    /** 실제 `hg` 실행 파일을 쓸 수 있으면 `true`이다. */
    val available: Boolean by lazy { runCatching { ProcessBuilder("hg", "--version").start().waitFor() == 0 }.getOrDefault(false) }

    /** [dir]에서 `hg [args]`를 실행하고 출력(오류 포함)을 돌려준다. 실패하면 [IllegalStateException]이다. */
    fun hg(dir: File, vararg args: String): String {
        val builder = ProcessBuilder(listOf("hg") + args.toList()).directory(dir).redirectErrorStream(true)
        builder.environment()["HGPLAIN"] = "1"
        builder.environment()["HGUSER"] = "Tester <tester@example.com>"
        val process = builder.start()
        val out = String(process.inputStream.readBytes())
        check(process.waitFor() == 0) { "hg ${args.joinToString(" ")} 실패: $out" }
        return out
    }

    /** [dir]에 저장소를 만든다. */
    fun init(dir: File) {
        dir.mkdirs()
        hg(dir, "init", ".")
    }

    /**
     * [files]를 쓰고(값이 `null`이면 지운다) 추가, 삭제한 뒤 [message]로 커밋한다. [date]는 `YYYY-MM-DD HH:MM +ZZZZ` 꼴이다. 커밋한 노드(40자)를 돌려준다.
     */
    fun commit(dir: File, message: String, files: Map<String, String?>, date: String = "2026-11-01 10:00 +0900", user: String? = null): String {
        for ((path, content) in files) {
            val file = File(dir, path)
            if (content == null) {
                hg(dir, "remove", "-f", path)
            } else {
                file.parentFile.mkdirs(); file.writeText(content)
            }
        }
        hg(dir, "addremove")
        val args = mutableListOf("commit", "-m", message, "-d", date)
        if (user != null) args += listOf("-u", user)
        hg(dir, *args.toTypedArray())
        return hg(dir, "log", "-r", ".", "--template", "{node}")
    }

    /** 현재 위치(또는 [rev])에 이름표를 만든다. */
    fun bookmark(dir: File, name: String, rev: String? = null) { hg(dir, *(listOf("bookmark", name) + (rev?.let { listOf("-r", it) } ?: emptyList())).toTypedArray()) }

    /** [rev]에 태그를 단다(태그를 단 커밋이 하나 더 생긴다). */
    fun tag(dir: File, name: String, rev: String, date: String = "2026-11-03 10:00 +0900") { hg(dir, "tag", "-r", rev, "-d", date, "-m", "태그 $name", name) }

    /** 파일 이름을 바꾼다(복사 기록이 커밋에 남는다). */
    fun rename(dir: File, from: String, to: String) { File(dir, to).parentFile.mkdirs(); hg(dir, "rename", from, to) }

    /** 실제 `hg verify`가 통과하면 `true`이다. */
    fun verifies(dir: File): Boolean = runCatching { hg(dir, "verify"); true }.getOrDefault(false)
}
