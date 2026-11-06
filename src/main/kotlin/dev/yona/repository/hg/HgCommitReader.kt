package dev.yona.repository.hg

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.repository.CommitInfo
import dev.yona.repository.CommitReader
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Mercurial 저장소의 커밋을 hg4j로 읽는 [CommitReader] 구현이다(X-7, D-86). 결과는 Git, Subversion과 같은 [CommitInfo]이다.
 *
 * - 커밋 번호는 40자 노드이고 짧은 번호는 12자이다(Mercurial의 관례). 리비전 번호(`0`, `1`…)는 저장소마다 달라 공유되지 않으므로 결과에 쓰지 않는다.
 * - 사용자 문자열은 `이름 <이메일>`로 읽는다. 이메일이 없으면 작성자는 문자열 전체이고 이메일은 비어 있다. Mercurial에는 커미터가 따로 없어 작성자와 같다.
 * - 이력은 리비전에서 거슬러 오른 조상을 최근 리비전 순으로 모은다. 경로를 주면 그 파일이나 폴더를 바꾼 체인지셋만 모은다(병합 체인지셋은 바꾼
 *   파일 목록이 거의 비어 있어 경로를 주면 빠진다).
 */
@Component
class HgCommitReader(private val hg: HgRepositories) : CommitReader {

    override val type: VcsType get() = VcsType.MERCURIAL

    private fun open(project: Project) = hg.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? = open(project).use { h ->
        val node = HgRevisions.resolve(h, revision) ?: return null
        val changelog = HgRevisions.changelog(h)
        val start = HgChangesets.revOf(changelog, node)
        if (start < 0) return null
        // 최근 리비전 순으로 조상을 걷고, 경로로 거른 뒤 앞 쪽만큼 건너뛰고 한 쪽만 읽는다
        val wanted = path?.trim('/')?.takeIf { it.isNotEmpty() }
        HgChangesets.ancestors(changelog, start).sortedDescending().asSequence()
            .mapNotNull { HgChangesets.read(changelog, it) }
            .filter { wanted == null || it.files.any { file -> file == wanted || file.startsWith("$wanted/") } }
            .drop(page * size).take(size).map { info(it) }.toList()
    }

    override fun commit(project: Project, revision: String): CommitInfo? = open(project).use { h ->
        val node = HgRevisions.resolve(h, revision) ?: return null
        val changelog = HgRevisions.changelog(h)
        val rev = HgChangesets.revOf(changelog, node)
        if (rev < 0) null else HgChangesets.read(changelog, rev)?.let { info(it) }
    }

    override fun commits(project: Project, ids: Collection<String>): Map<String, CommitInfo> {
        if (ids.isEmpty()) return emptyMap()
        return open(project).use { h ->
            val changelog = HgRevisions.changelog(h)
            ids.distinct().mapNotNull { id ->
                val node = runCatching { HgRevisions.resolve(h, id) }.getOrNull() ?: return@mapNotNull null
                val rev = HgChangesets.revOf(changelog, node)
                if (rev < 0) null else HgChangesets.read(changelog, rev)?.let { id to info(it) }
            }.toMap()
        }
    }

    /** 푸시가 가져온 커밋이다: [newId]에서 [oldId]의 조상을 뺀 것을 최근 순으로 돌려준다. [oldId]가 영 번호면 Git과 같이 새 커밋 하나이다. */
    override fun pushedCommits(project: Project, oldId: String, newId: String): List<CommitInfo> = open(project).use { h ->
        val changelog = HgRevisions.changelog(h)
        val end = HgChangesets.revOf(changelog, newId)
        if (end < 0) return emptyList()
        if (oldId == dev.yona.domain.vcs.RefUpdate.NULL_ID) return listOfNotNull(HgChangesets.read(changelog, end)?.let { info(it) })
        val start = HgChangesets.revOf(changelog, oldId)
        val known = if (start < 0) emptySet() else HgChangesets.ancestors(changelog, start)
        (HgChangesets.ancestors(changelog, end) - known).sortedDescending().mapNotNull { HgChangesets.read(changelog, it)?.let { c -> info(c) } }
    }

    override fun parentOf(project: Project, revision: String): CommitInfo? = open(project).use { h ->
        val node = HgRevisions.resolve(h, revision) ?: return null
        val changelog = HgRevisions.changelog(h)
        val rev = HgChangesets.revOf(changelog, node)
        if (rev < 0) return null
        val first = HgChangesets.read(changelog, rev)?.parents?.firstOrNull() ?: return null
        HgChangesets.read(changelog, first)?.let { info(it) }
    }

    private fun info(changeset: HgChangeset): CommitInfo {
        val (name, email) = HgAuthors.parse(changeset.author)
        val time = Date(changeset.timestamp * 1000L)
        val message = changeset.message
        return CommitInfo(
            id = changeset.node, shortId = changeset.node.take(SHORT_ID), shortMessage = shortMessage(message), message = message,
            authorName = name, authorEmail = email, authorDate = time, committerName = name, committerEmail = email, commitTime = time,
            parentCount = changeset.parents.size
        )
    }

    /** 메시지의 첫 문단이다. 줄바꿈은 공백으로 바꾼다(Git의 `shortMessage`와 같다). */
    private fun shortMessage(message: String): String = message.substringBefore("\n\n").replace('\n', ' ').trim()

    private companion object {
        /** Mercurial의 관례인 짧은 커밋 번호 길이이다. */
        const val SHORT_ID = 12
    }
}

/** Mercurial 사용자 문자열(`이름 <이메일>`)을 읽는다. */
object HgAuthors {

    private val NAMED = Regex("""^(.*?)\s*<([^<>]*)>\s*$""")

    /** `(이름, 이메일)`이다. 이메일이 없으면 이름은 문자열 전체이고 이메일은 비어 있다. 이메일 주소만 있으면 이름과 이메일이 모두 그 주소이다. */
    fun parse(author: String): Pair<String, String> {
        val named = NAMED.find(author)
        if (named != null) return named.groupValues[1].trim().ifEmpty { named.groupValues[2] } to named.groupValues[2]
        val bare = author.trim()
        return bare to if (bare.contains('@') && !bare.contains(' ')) bare else ""
    }
}
