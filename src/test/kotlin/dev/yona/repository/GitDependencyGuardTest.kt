package dev.yona.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * JGit 저장소를 직접 여는 곳(`openGitRepository`)이 Git 전용 하위 시스템에만 남아 있는지 지킨다(X-7 공통화 2단계). 종류와 무관한 읽기
 * (이력, 코드, README, 브랜치와 태그)는 `VcsBackends`의 읽기 구현을 거쳐야 하므로, 새 호출 지점이 생기면 이 시험이 실패한다.
 * 남은 호출 지점은 공통화 3~4단계와 풀 리퀘스트 단계(8단계)에서 하나씩 계약으로 바뀌고, 그때마다 이 목록에서 뺀다.
 */
class GitDependencyGuardTest {

    @Test
    fun `저장소를 JGit으로 직접 여는 곳은 허용한 Git 전용 하위 시스템뿐이다`() {
        val root = File("src/main/kotlin")
        val users = root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""\bopenGitRepository(ForHttp)?\(""").containsMatchIn(it.readText()) }
            .map { it.name }.toSortedSet()
        assertThat(users).describedAs("Git 전용이 아닌 곳에서 openGitRepository를 새로 부르면 안 된다").isEqualTo(ALLOWED.toSortedSet())
    }

    private companion object {
        /** `openGitRepository`를 아직 직접 부르는 파일이다(정의한 곳 포함). */
        val ALLOWED = listOf(
            "RepositoryService.kt",
            // 온라인 커밋(ONLINE_EDITING)
            "OnlineCommit.kt",
            // Git 서빙(4단계). 푸시 전후 처리는 3단계에서 종류와 무관하게 바뀌었다
            "GitHttpService.kt", "GitSshHandler.kt",
            // 풀 리퀘스트(8단계)
            "GitPullRequestBackend.kt", "NotificationReceivers.kt",
            // 커밋 해시 자동 링크
            "AutoLinkRenderer.kt"
        )
    }
}
