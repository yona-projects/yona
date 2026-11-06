package dev.yona.repository.hg

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.DiffSource
import dev.yona.domain.vcs.FileChange
import dev.yona.domain.vcs.FileChangeType
import dev.yona.domain.vcs.TextDiffAlgorithm
import dev.yona.domain.vcs.VcsType
import dev.yona.repository.DiffReader
import io.github.search5.hg4j.api.CommitCommand
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.ManifestCommand
import org.springframework.stereotype.Component
import java.io.File

/**
 * Mercurial 저장소의 커밋 사이 변경 목록을 매니페스트(파일 목록) 비교로 구하는 [DiffReader] 구현이다(X-7, D-86). 글 비교는 하지 않는다.
 * 그것은 종류와 무관하게 [dev.yona.repository.diff.DiffService]가 한다.
 *
 * - 양쪽 매니페스트에서 파일의 노드와 실행, 심볼릭 링크 표시가 다른 것을 찾는다: 한쪽에만 있으면 추가나 삭제, 다르면 수정(권한만 바뀌어도 수정).
 * - 이름 바꿈과 복사는 Git처럼 내용을 닮은 정도로 찾지 않고 Mercurial이 커밋에 남긴 **복사 기록**으로 안다. 추가된 파일의 파일 이력(filelog)에
 *   복사 원본이 있고 그 원본이 앞쪽에 있으면, 원본이 같은 비교에서 사라졌을 때 이름 바꿈, 남아 있으면 복사이다.
 * - 병합 체인지셋은 첫 부모와 비교한다. 서로 다른 저장소(포크)의 두 커밋도 비교할 수 있다.
 * - 모드 비트는 일반 파일 `0x81A4`(100644), 실행 파일 `0x81ED`(100755), 심볼릭 링크 `0xA000`(120000)이다.
 */
@Component
class HgDiffReader(private val hg: HgRepositories) : DiffReader {

    override val type: VcsType get() = VcsType.MERCURIAL

    private fun open(project: Project) = hg.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun commitDiff(project: Project, revision: String): DiffSource? {
        val h = open(project)
        try {
            val node = HgRevisions.resolve(h, revision) ?: return null.also { h.close() }
            val changelog = HgRevisions.changelog(h)
            val rev = HgChangesets.revOf(changelog, node)
            if (rev < 0) return null.also { h.close() }
            val parent = HgChangesets.read(changelog, rev)?.parents?.firstOrNull()?.let { HgChangesets.read(changelog, it)?.node }
            return source(h, parent, h, node)
        } catch (e: Exception) {
            h.close()
            throw e
        }
    }

    override fun diff(project: Project, revA: String, revB: String): DiffSource {
        val h = open(project)
        try {
            return source(h, HgRevisions.resolve(h, revA), h, HgRevisions.resolve(h, revB))
        } catch (e: Exception) {
            h.close()
            throw e
        }
    }

    override fun diff(projectA: Project, revA: String, projectB: Project, revB: String): DiffSource {
        val a = open(projectA)
        val b = try { open(projectB) } catch (e: Exception) { a.close(); throw e }
        try {
            return source(a, HgRevisions.resolve(a, revA), b, HgRevisions.resolve(b, revB))
        } catch (e: Exception) {
            a.close(); b.close()
            throw e
        }
    }

    private class HgDiffSource(
        override val commitA: String?, override val commitB: String?, override val changes: List<FileChange>, private val handles: List<Hg>
    ) : DiffSource {
        override val algorithm: TextDiffAlgorithm get() = TextDiffAlgorithm.HISTOGRAM
        override fun close() { handles.forEach { runCatching { it.close() } } }
    }

    private fun source(a: Hg, nodeA: String?, b: Hg, nodeB: String?): DiffSource {
        val before = nodeA?.let { manifest(a, it) } ?: emptyMap()
        val after = nodeB?.let { manifest(b, it) } ?: emptyMap()
        val added = after.keys.filter { it !in before }
        val removed = before.keys.filter { it !in after }
        // 이름 바꿈과 복사: 추가된 파일의 복사 원본이 앞쪽에 있으면 원본에서 온 것이다
        val copies = added.mapNotNull { path -> copySource(b, path, after.getValue(path).nodeHex)?.takeIf { it in before }?.let { path to it } }.toMap()
        val renamedSources = copies.filter { (_, from) -> from in removed }.values.toSet()

        val changes = ArrayList<FileChange>()
        for (path in (before.keys + after.keys).toSortedSet()) {
            val old = before[path]
            val new = after[path]
            when {
                old != null && new != null -> if (old.nodeHex != new.nodeHex || mode(old) != mode(new)) changes += change(FileChangeType.MODIFY, path, path, a, nodeA, old, b, nodeB, new)
                new != null -> {
                    val from = copies[path]
                    changes += when {
                        from == null -> change(FileChangeType.ADD, null, path, a, nodeA, null, b, nodeB, new)
                        from in renamedSources -> change(FileChangeType.RENAME, from, path, a, nodeA, before.getValue(from), b, nodeB, new)
                        else -> change(FileChangeType.COPY, from, path, a, nodeA, before.getValue(from), b, nodeB, new)
                    }
                }
                old != null -> if (path !in renamedSources) changes += change(FileChangeType.DELETE, path, null, a, nodeA, old, b, nodeB, null)
            }
        }
        return HgDiffSource(nodeA, nodeB, changes, if (a === b) listOf(a) else listOf(a, b))
    }

    private fun change(
        type: FileChangeType, pathA: String?, pathB: String?, a: Hg, nodeA: String?, old: ManifestCommand.ManifestEntry?, b: Hg, nodeB: String?, new: ManifestCommand.ManifestEntry?
    ) = FileChange(
        type, pathA, pathB, old?.let { mode(it) } ?: 0, new?.let { mode(it) } ?: 0,
        { if (pathA != null && nodeA != null) a.cat().setFile(pathA).setRevision(nodeA).call() else null },
        { if (pathB != null && nodeB != null) b.cat().setFile(pathB).setRevision(nodeB).call() else null }
    )

    private fun manifest(h: Hg, node: String): Map<String, ManifestCommand.ManifestEntry> = h.manifest().setRevision(node).call().associateBy { it.path }

    private fun mode(entry: ManifestCommand.ManifestEntry): Int = when {
        entry.isSymlink -> 0xA000
        entry.isExecutable -> 0x81ED
        else -> 0x81A4
    }

    /** [path]의 파일 이력에서 노드 [fileNode]가 복사해 온 원본 경로이다. 복사한 것이 아니면 `null`이다. */
    private fun copySource(h: Hg, path: String, fileNode: String): String? = try {
        val store = h.repository.storeDir
        val index = CommitCommand.getFilelogIndex(store, path)
        val revlog = h.repository.getRevlog(index, File(index.path.removeSuffix(".i") + ".d"))
        val rev = revlog.findRevision(io.github.search5.hg4j.util.NodeIdUtil.fromHex(fileNode))
        if (rev < 0) null else revlog.getRevisionMetadata(rev)?.get("copy")
    } catch (e: java.io.IOException) {
        null
    }
}
