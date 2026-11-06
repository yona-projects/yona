package dev.yona.repository.hg

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.repository.RefHead
import dev.yona.repository.RefReader
import dev.yona.repository.TagAuthor
import dev.yona.repository.TagCreation
import dev.yona.repository.TagInfo
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Mercurial 저장소의 브랜치와 태그를 hg4j로 읽는 [RefReader] 구현이다(X-7, D-86). Yona는 Mercurial의 **이름표(bookmark)를 브랜치로** 보인다.
 * 이름 붙은 브랜치(named branch)는 커밋에 영구히 새겨지고 닫을 수만 있어 Git 브랜치와 맞지 않으므로 목록에 두지 않는다.
 *
 * - 브랜치 이름은 `refs/heads/<이름표>`이다. 기본 브랜치는 `main`, `master`, `@` 이름표 가운데 먼저 있는 것이고 없으면 없다.
 * - 태그는 `refs/tags/<태그>`이다. Mercurial 태그는 `.hgtags` 파일의 한 줄이라 주석 태그의 메시지와 태그 단 사람이 없다(경량 태그로 본다).
 * - 이름표는 지울 수 있다. 태그 만들기와 지우기는 작업 파일이 없는 서버 저장소에서 쓸 수 없어(`.hgtags`를 커밋해야 한다) 아직 지원하지 않는다.
 */
@Component
class HgRefReader(private val hg: HgRepositories) : RefReader {

    override val type: VcsType get() = VcsType.MERCURIAL

    private fun open(project: Project) = hg.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun branches(project: Project): List<RefHead> = open(project).use { h ->
        val changelog = HgRevisions.changelog(h)
        HgRevisions.bookmarks(h).mapNotNull { (name, node) -> head(changelog, HgRepositories.HEADS + name, node) }.sortedByDescending { it.committerDate }
    }

    override fun headBranch(project: Project): RefHead? = open(project).use { h ->
        val bookmarks = HgRevisions.bookmarks(h)
        val name = HgRepositories.DEFAULT_BOOKMARKS.firstOrNull { it in bookmarks } ?: return null
        head(HgRevisions.changelog(h), HgRepositories.HEADS + name, bookmarks.getValue(name))
    }

    override fun deleteBranch(project: Project, branchName: String) {
        open(project).use { h -> h.bookmark().setBookmarkName(branchName.removePrefix(HgRepositories.HEADS)).setDelete(true).call() }
    }

    override fun createBranch(project: Project, branchName: String, startPoint: String) {
        open(project).use { h -> h.bookmark().setBookmarkName(branchName).setRevision(startPoint).call() }
    }

    private fun head(changelog: io.github.search5.hg4j.storage.Revlog, name: String, node: String): RefHead? {
        val rev = HgChangesets.revOf(changelog, node)
        val changeset = (if (rev < 0) null else HgChangesets.read(changelog, rev)) ?: return null
        return RefHead(name, changeset.node, changeset.node.take(SHORT_ID), Date(changeset.timestamp * 1000L))
    }

    override fun tags(project: Project): List<TagInfo> = open(project).use { h ->
        val changelog = HgRevisions.changelog(h)
        HgRevisions.tags(h).mapNotNull { (name, node) -> tagInfo(changelog, name, node) }
            .sortedWith(compareBy<TagInfo>({ it.sortDate == null }, { -(it.sortDate?.time ?: 0L) }, { it.shortName }))
    }

    override fun findTag(project: Project, shortName: String): TagInfo? = open(project).use { h ->
        val node = HgRevisions.tags(h)[shortName] ?: return null
        tagInfo(HgRevisions.changelog(h), shortName, node)
    }

    private fun tagInfo(changelog: io.github.search5.hg4j.storage.Revlog, name: String, node: String): TagInfo? {
        val rev = HgChangesets.revOf(changelog, node)
        val changeset = if (rev < 0) null else HgChangesets.read(changelog, rev)
        return TagInfo(
            name = HgRepositories.TAGS + name, shortName = name, annotated = false, message = null, taggerName = null, taggerDate = null,
            commitId = changeset?.node, commitDate = changeset?.let { Date(it.timestamp * 1000L) }
        )
    }

    override fun createTag(project: Project, name: String, target: String?, message: String?, tagger: TagAuthor): TagCreation =
        throw UnsupportedOperationException("Mercurial 저장소의 태그 만들기는 아직 지원하지 않습니다.")

    override fun deleteTag(project: Project, shortName: String): Boolean =
        throw UnsupportedOperationException("Mercurial 저장소의 태그 지우기는 아직 지원하지 않습니다.")

    private companion object {
        const val SHORT_ID = 12
    }
}
