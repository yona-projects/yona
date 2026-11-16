package dev.yona.ssh.svn

import org.junit.jupiter.api.Assumptions
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * `ra_svn` 서버를 실제 `svn` 클라이언트로 시험하는 도구이다. 서버는 TCP 소켓에서 받고, 클라이언트는 `svn+ra://` 터널(설정 파일의 `[tunnels]`)로 파이썬 중계기를 거쳐
 * 소켓에 연결한다. 프로토콜은 `svn+ssh`와 같고 SSH 인증만 빠진다. `svn`, `svnadmin`이 없으면 시험을 건너뛴다.
 */
class RaSvnTestServer(private val work: File, private val resolver: RaSvnResolver, private val user: String = "alice") : AutoCloseable {

    private val socket = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    private val connections = java.util.Collections.synchronizedList(mutableListOf<Socket>())
    private val acceptor = thread(isDaemon = true, name = "ra-svn-test-accept") {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (e: Exception) { break }
            connections += client
            thread(isDaemon = true, name = "ra-svn-test-session") {
                client.use { RaSvnServer(it.getInputStream(), it.getOutputStream(), user, resolver).serve() }
            }
        }
    }
    private val configDir = File(work, "svn-config").also { it.mkdirs() }

    init {
        val relay = File(work, "relay.py")
        relay.writeText(
            """
            #!/usr/bin/env python3
            import socket, os, threading
            s = socket.create_connection(("127.0.0.1", ${socket.localPort}))
            log = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "ra-dialogue.log"), "ab", buffering=0)
            def up():
                while True:
                    d = os.read(0, 65536)
                    if not d: break
                    log.write(b"C>S " + d + b"\n")
                    s.sendall(d)
                try: s.shutdown(socket.SHUT_WR)
                except OSError: pass
            threading.Thread(target=up, daemon=True).start()
            while True:
                d = s.recv(65536)
                if not d: break
                log.write(b"S>C " + d + b"\n")
                os.write(1, d)
            """.trimIndent()
        )
        relay.setExecutable(true)
        File(configDir, "config").writeText("[tunnels]\nra = ${relay.path}\n")
    }

    /** `svn+ra://` 주소이다. [path]는 `/소유자/이름/...`이다. */
    fun url(path: String): String = "svn+ra://localhost$path"

    class Result(val exit: Int, val output: String)

    fun svn(vararg args: String, dir: File = work): Result {
        val out = File(work, "svn-out-${System.nanoTime()}.txt")
        val process = ProcessBuilder(listOf("svn", "--config-dir", configDir.path, "--non-interactive") + args).directory(dir).redirectErrorStream(true).redirectOutput(out).also { it.environment()["LC_ALL"] = "C.UTF-8" }.start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("시간 초과: ${args.toList()}\n대화:\n${dialogue()}")
        }
        return Result(process.exitValue(), out.readText())
    }

    /** 중계기가 기록한 클라이언트와 서버의 대화이다(실패 원인을 볼 때 쓴다). */
    fun dialogue(): String = File(work, "ra-dialogue.log").takeIf { it.exists() }?.readText(Charsets.UTF_8)?.takeLast(6000).orEmpty()

    override fun close() {
        socket.close(); connections.forEach { runCatching { it.close() } }
    }

    companion object {
        /** `svn`과 `svnadmin`이 없으면 시험을 건너뛴다. */
        fun assumeSubversionInstalled() {
            val available = listOf("svn", "svnadmin").all { runCatching { ProcessBuilder(it, "--version", "--quiet").redirectErrorStream(true).start().also { p -> p.inputStream.readBytes() }.waitFor() == 0 }.getOrDefault(false) }
            Assumptions.assumeTrue(available, "svn과 svnadmin이 설치되어 있어야 한다")
        }

        /** [directory]에 저장소를 만들고 리비전 1(`/trunk`와 `/trunk/a.txt`)을 커밋한다. 파일을 직접 쓰는 `file://` 접근이라 서버를 거치지 않는다. */
        fun createRepository(directory: File, work: File): String {
            check(ProcessBuilder("svnadmin", "create", directory.path).start().waitFor() == 0)
            val url = "file://${directory.absolutePath}"
            val wc = File(work, "seed-wc-${directory.name}")
            fun run(vararg a: String) = check(ProcessBuilder("svn", *a).directory(work).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0) { a.toList().toString() }
            run("checkout", "-q", url, wc.path)
            File(wc, "trunk").mkdirs(); File(wc, "trunk/a.txt").writeText("hello\n")
            run("add", "-q", "${wc.path}/trunk")
            run("commit", "-q", "-m", "첫 커밋", wc.path)
            return url
        }
    }
}
