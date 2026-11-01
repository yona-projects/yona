package dev.yona.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 버전 관리(git, svn, 앞으로 Mercurial) 공통 지표이다(로드맵 X-10 C5c, `decisions.md` D-64, A-320). 이름은 VCS 중립(`yona.vcs.*`)이고 `vcs` 태그(`git`, `svn`, `hg`)만
 * 다르다. X-7(Mercurial)은 같은 계측 지점에 `hg`만 더하면 된다. 태그는 모두 고정된 낮은 카디널리티 값이며 프로젝트, 사용자, 경로 이름은 넣지 않는다.
 *
 * | 지표 | 태그 | 설명 |
 * |---|---|---|
 * | `yona.vcs.request` | `vcs`, `service`, `target`(code, wiki), `result` | 저장소 서비스 요청의 시간과 횟수 |
 * | `yona.vcs.request.active` | `vcs`, `service`, `target` | 진행 중인 요청 수(긴 요청이 스레드를 점유하는지) |
 * | `yona.vcs.transfer` | `vcs`, `target`, `direction`(in, out) | 전송한 바이트 |
 * | `yona.vcs.pushes` | `vcs`, `kind`(branch, tag, other), `result`(ok, rejected), `reason` | 푸시한 ref 수 |
 * | `yona.vcs.hook.duration` | `hook`, `result` | 푸시 뒤 훅의 시간과 실패 |
 * | `yona.vcs.operation.duration` | `vcs`, `op`, `result` | 코드 보기, 이력, diff 같은 저장소 작업의 시간 |
 */
@Component
class VcsMetrics(private val registry: MeterRegistry) {

    private val active = ConcurrentHashMap<List<String>, AtomicInteger>()

    /** 진행 중인 요청 하나이다. 블록 안에서 [result]를 바꿀 수 있다. 예외가 나면 `error`이다. */
    class Request internal constructor(var result: String = OK, var target: String = "code")

    /** [vcs]의 [service] 요청을 실행하며 시간과 진행 중인 수를 남긴다. 예외는 `error`로 기록하고 다시 던진다. */
    fun request(vcs: String, service: String, target: String = "code", block: (Request) -> Unit) {
        val running = active.computeIfAbsent(listOf(vcs, service, target)) {
            AtomicInteger().also { value -> Gauge.builder("yona.vcs.request.active", value) { it.get().toDouble() }.tags("vcs", vcs, "service", service, "target", target).description("진행 중인 저장소 서비스 요청 수").register(registry) }
        }
        running.incrementAndGet()
        val sample = Timer.start(registry)
        val request = Request(target = target)
        try {
            block(request)
        } catch (e: Throwable) {
            request.result = "error"
            throw e
        } finally {
            sample.stop(Timer.builder("yona.vcs.request").tags("vcs", vcs, "service", service, "target", request.target, "result", request.result).description("저장소 서비스 요청 시간").register(registry))
            running.decrementAndGet()
        }
    }

    /** 전송한 [bytes]를 [direction](`in`: 클라이언트에서 서버로, `out`: 서버에서 클라이언트로)에 더한다. 0 이하는 무시한다. */
    fun transfer(vcs: String, target: String, direction: String, bytes: Long) {
        if (bytes <= 0) return
        Counter.builder("yona.vcs.transfer").tags("vcs", vcs, "target", target, "direction", direction).baseUnit("bytes").description("저장소 서비스가 전송한 바이트").register(registry).increment(bytes.toDouble())
    }

    /** 읽은 바이트를 `in` 방향으로 세는 입력 스트림이다. */
    fun countedInput(vcs: String, target: String, input: InputStream): InputStream = object : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) transfer(vcs, target, "in", 1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { transfer(vcs, target, "in", it.toLong()) }
    }

    /** 쓴 바이트를 `out` 방향으로 세는 출력 스트림이다. */
    fun countedOutput(vcs: String, target: String, output: OutputStream): OutputStream = object : FilterOutputStream(output) {
        override fun write(b: Int) { out.write(b); transfer(vcs, target, "out", 1) }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); transfer(vcs, target, "out", len.toLong()) }
    }

    /** 푸시한 [ref] 하나의 결과를 센다. 거부한 이유는 고정된 값(`ruleset_force`, `reserved` 등)만 [reason]에 준다. */
    fun push(vcs: String, ref: String, result: String, reason: String = "none") {
        val kind = when {
            ref.startsWith("refs/heads/") -> "branch"
            ref.startsWith("refs/tags/") -> "tag"
            else -> "other"
        }
        Counter.builder("yona.vcs.pushes").tags("vcs", vcs, "kind", kind, "result", result, "reason", reason).description("푸시한 ref 수").register(registry).increment()
    }

    /** 푸시 뒤 훅 [hook]을 실행하며 시간과 성공 여부를 남긴다. 예외는 기록하고 다시 던진다. */
    fun hook(hook: String, block: () -> Unit) {
        val sample = Timer.start(registry)
        var result = "success"
        try {
            block()
        } catch (e: Throwable) {
            result = "failure"
            throw e
        } finally {
            sample.stop(Timer.builder("yona.vcs.hook.duration").tags("hook", hook, "result", result).description("푸시 뒤 훅 실행 시간").register(registry))
        }
    }

    /** 프로젝트의 VCS 이름([projectVcs])으로 저장소 작업 [op]을 실행하며 시간을 남긴다. */
    fun <T> operation(projectVcs: String?, op: String, block: () -> T): T {
        val sample = Timer.start(registry)
        var result = "success"
        try {
            return block()
        } catch (e: Throwable) {
            result = "failure"
            throw e
        } finally {
            sample.stop(Timer.builder("yona.vcs.operation.duration").tags("vcs", vcsOf(projectVcs), "op", op, "result", result).description("저장소 작업 시간").register(registry))
        }
    }

    companion object {
        const val OK = "ok"

        /** 알려진 WebDAV 메서드(svn 서비스 이름)이다. 태그 값을 이 집합으로 제한한다. */
        private val SVN_METHODS = setOf("OPTIONS", "GET", "HEAD", "POST", "PUT", "DELETE", "PROPFIND", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK", "REPORT", "CHECKOUT", "MERGE", "MKACTIVITY", "CHECKIN", "VERSION-CONTROL", "UNCHECKOUT", "MKWORKSPACE", "UPDATE", "LABEL", "BASELINE-CONTROL", "SEARCH")

        /** HTTP 상태를 결과 태그 값으로 바꾼다. */
        fun resultOf(status: Int): String = when {
            status in 200..399 -> OK
            status == 401 || status == 403 || status == 429 -> "denied"
            status == 404 -> "not_found"
            status in 400..499 -> "client_error"
            else -> "error"
        }

        /** 프로젝트의 `vcs` 값(`GIT`, `Subversion`, `Mercurial`)을 태그 값으로 바꾼다. */
        fun vcsOf(projectVcs: String?): String = when (projectVcs) {
            "GIT" -> "git"
            "Subversion" -> "svn"
            "Mercurial" -> "hg"
            else -> "other"
        }

        /** svn 요청의 서비스 이름이다. 알려진 WebDAV 메서드만 쓰고 나머지는 `other`이다(공격자가 태그 값을 늘리지 못하게). */
        fun svnServiceOf(method: String): String = method.uppercase().takeIf { it in SVN_METHODS } ?: "other"
    }
}
