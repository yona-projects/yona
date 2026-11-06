package dev.yona.repository.hg

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Mercurial(X-7) 지원에 쓰는 hg4j 의존성이 클래스패스에 올라와 있고, 병합 커밋 공개 진입점이 든 1.1.1 이상인지 확인한다.
 *
 * 1.1.0에는 [MERGE_COMMIT] 클래스가 없다. 클래스 이름을 문자열로 찾는 것은 의존성이 빠졌을 때 컴파일 오류가 아니라 시험 실패로 드러나게 하기 위해서이다.
 */
class Hg4jDependencyTest {

    @Test
    fun `hg4j 1_1_1 이상의 병합 커밋 진입점이 클래스패스에 있다`() {
        assertThat(runCatching { Class.forName(MERGE_COMMIT) }.isSuccess).describedAs("$MERGE_COMMIT 클래스").isTrue()
        assertThat(runCatching { Class.forName(MERGE_COMMIT_RESULT) }.isSuccess).describedAs("$MERGE_COMMIT_RESULT 클래스").isTrue()
    }

    @Test
    fun `서버 쪽 HTTP와 SSH 서빙 클래스가 있다`() {
        for (name in listOf("io.github.search5.hg4j.transport.HgHttpWireServer", "io.github.search5.hg4j.transport.HgSshWireServer")) {
            assertThat(runCatching { Class.forName(name) }.isSuccess).describedAs("$name 클래스").isTrue()
        }
    }

    /**
     * 내장 SSH 서버(X-3)가 쓰는 Apache MINA SSHD의 두 모듈은 버전이 같아야 한다. hg4j가 `sshd-common`을 직접 요구해서, 한 모듈만 Spring의 버전
     * 관리 규칙으로 고정되면 `sshd-core`와 `sshd-common`의 버전이 서로 다르게 올라온다.
     */
    @Test
    fun `sshd-core와 sshd-common의 버전이 같다`() {
        fun version(className: String): String {
            val path = Class.forName(className).protectionDomain.codeSource.location.path
            return Regex("""-(\d+\.\d+\.\d+)\.jar""").find(path)?.groupValues?.get(1) ?: error("버전을 읽지 못했다: $path")
        }
        assertThat(version("org.apache.sshd.server.SshServer")).isEqualTo(version("org.apache.sshd.common.util.buffer.Buffer"))
    }

    private companion object {
        const val MERGE_COMMIT = "io.github.search5.hg4j.api.MergeCommitCommand"
        const val MERGE_COMMIT_RESULT = "io.github.search5.hg4j.api.MergeCommitResult"
    }
}
