package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Mercurial 푸시(체인지그룹과 이름표 이동)를 저장소 종류와 무관한 [RefUpdate]로 바꾸는 일을 실제 `hg` 저장소에서 확인한다(X-7 6단계). */
class HgPushUpdatesTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgCommitReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File
    private lateinit var c0: String
    private lateinit var c1: String
    private lateinit var c2: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgCommitReader(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
        c0 = HgTestSupport.commit(dir, "first", mapOf("a.txt" to "one\n"), "2026-11-01 10:00 +0900")
        c1 = HgTestSupport.commit(dir, "second", mapOf("a.txt" to "one\ntwo\n"), "2026-11-02 10:00 +0900")
        c2 = HgTestSupport.commit(dir, "third", mapOf("b.txt" to "b\n"), "2026-11-03 10:00 +0900")
    }

    private fun withChangelog(block: (io.github.search5.hg4j.storage.Revlog) -> Unit) = repos.open("tester", "proj").use { block(HgRevisions.changelog(it)) }

    @Test
    fun `이름표 이동은 옛 값과 새 값이 모두 있으면 갱신이고 조상이 아니면 강제 갱신이다`() = withChangelog { log ->
        assertThat(HgPushUpdates.bookmarkType(log, c0, c2)).isEqualTo(RefUpdateType.UPDATE)
        assertThat(HgPushUpdates.bookmarkType(log, c2, c0)).isEqualTo(RefUpdateType.FORCE_UPDATE)
        assertThat(HgPushUpdates.bookmarkType(log, "", c1)).isEqualTo(RefUpdateType.CREATE)
        assertThat(HgPushUpdates.bookmarkType(log, c1, "")).isEqualTo(RefUpdateType.DELETE)
    }

    @Test
    fun `이름표 이동은 refs heads 아래 ref로 바뀌고 빈 값은 영 번호가 된다`() = withChangelog { log ->
        val created = HgPushUpdates.bookmark(log, "release", "", c1)
        assertThat(created).isEqualTo(RefUpdate("refs/heads/release", RefUpdate.NULL_ID, c1, RefUpdateType.CREATE))
        val deleted = HgPushUpdates.bookmark(log, "release", c1, "")
        assertThat(deleted).isEqualTo(RefUpdate("refs/heads/release", c1, RefUpdate.NULL_ID, RefUpdateType.DELETE))
    }

    @Test
    fun `이름표가 가리키지 않는 새 머리는 이름 붙은 브랜치 ref로 올린다`() = withChangelog { log ->
        val updates = HgPushUpdates.of(log, nodes = listOf(c1, c2), moves = emptyList())
        assertThat(updates).containsExactly(RefUpdate("refs/heads/default", c0, c2, RefUpdateType.UPDATE))
    }

    @Test
    fun `이름표가 이미 가리키는 머리는 이름표 갱신으로만 센다`() = withChangelog { log ->
        val move = HgPushUpdates.bookmark(log, "main", c0, c2)
        val updates = HgPushUpdates.of(log, nodes = listOf(c1, c2), moves = listOf(move))
        assertThat(updates).containsExactly(move)
    }

    @Test
    fun `저장소의 첫 체인지셋을 올리면 옛 번호는 영 번호이고 만들기이다`() = withChangelog { log ->
        val updates = HgPushUpdates.of(log, nodes = listOf(c0, c1, c2), moves = emptyList())
        assertThat(updates).containsExactly(RefUpdate("refs/heads/default", RefUpdate.NULL_ID, c2, RefUpdateType.CREATE))
    }

    @Test
    fun `푸시가 가져온 커밋은 새 머리에서 옛 머리의 조상을 뺀 것이고 영 번호면 새 커밋 하나이다`() {
        assertThat(reader.pushedCommits(project, c0, c2).map { it.id }).containsExactly(c2, c1)
        assertThat(reader.pushedCommits(project, RefUpdate.NULL_ID, c2).map { it.id }).containsExactly(c2)
        assertThat(reader.pushedCommits(project, c2, c2)).isEmpty()
    }
}
