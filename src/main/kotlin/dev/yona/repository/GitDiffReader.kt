package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.ContentTooLargeException
import dev.yona.domain.vcs.DiffSource
import dev.yona.domain.vcs.FileChange
import dev.yona.domain.vcs.FileChangeType
import dev.yona.domain.vcs.TextDiffAlgorithm
import dev.yona.domain.vcs.VcsType
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.errors.LargeObjectException
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.io.NullOutputStream
import org.springframework.stereotype.Component

/**
 * Git 저장소의 커밋 사이 변경 목록을 JGit `DiffFormatter`로 구하는 [DiffReader] 구현이다. v1.6 `GitRepository.getDiff`, `getFileDiffs`
 * (`app/playRepository/GitRepository.java:731-775, 1451-1730`)의 변경 목록 쪽에 해당한다. 바뀐 파일마다 이름 바꿈과 복사를 찾는다.
 *
 * 풀 리퀘스트처럼 서로 다른 저장소의 커밋을 비교할 때는 두 저장소의 객체를 함께 읽는 판독기를 쓴다. 파일 내용은 변경 목록을 쓰는 동안 읽으므로
 * 저장소는 [DiffSource.close]에서 닫는다.
 */
@Component
class GitDiffReader(private val git: GitRepositories) : DiffReader {

    override val type: VcsType get() = VcsType.GIT

    private fun open(project: Project): Repository = git.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun commitDiff(project: Project, revision: String): DiffSource? {
        val repository = open(project)
        try {
            val id = repository.resolve(revision) ?: return null.also { repository.close() }
            val commit = try { RevWalk(repository).use { it.parseCommit(id) } } catch (e: org.eclipse.jgit.errors.MissingObjectException) { return null.also { repository.close() } }
            val parent = if (commit.parentCount > 0) commit.getParent(0).id else null
            return source(listOf(repository), repository, repository, parent, id)
        } catch (e: Exception) {
            repository.close()
            throw e
        }
    }

    override fun diff(project: Project, revA: String, revB: String): DiffSource {
        val repository = open(project)
        try {
            return source(listOf(repository), repository, repository, repository.resolve(revA), repository.resolve(revB))
        } catch (e: Exception) {
            repository.close()
            throw e
        }
    }

    override fun diff(projectA: Project, revA: String, projectB: Project, revB: String): DiffSource {
        val repoA = open(projectA)
        val repoB = try { open(projectB) } catch (e: Exception) { repoA.close(); throw e }
        try {
            return source(listOf(repoA, repoB), repoA, repoB, repoA.resolve(revA), repoB.resolve(revB))
        } catch (e: Exception) {
            repoA.close(); repoB.close()
            throw e
        }
    }

    /** 두 저장소의 객체를 차례로 찾아 읽는 판독기이다. */
    private class MultipleRepositoryObjectReader(private val readers: Collection<ObjectReader>) : ObjectReader() {
        override fun newReader(): ObjectReader = MultipleRepositoryObjectReader(readers)

        override fun resolve(id: AbbreviatedObjectId): Collection<ObjectId> = readers.flatMapTo(HashSet()) { it.resolve(id) }

        override fun open(objectId: AnyObjectId, typeHint: Int): ObjectLoader =
            readers.firstOrNull { it.has(objectId, typeHint) }?.open(objectId, typeHint) ?: throw org.eclipse.jgit.errors.MissingObjectException(objectId.copy(), typeHint)

        override fun getShallowCommits(): Set<ObjectId> = readers.flatMapTo(HashSet()) { it.shallowCommits }

        /** 읽는 저장소는 호출한 쪽이 닫으므로 여기서는 닫지 않는다. */
        override fun close() {}
    }

    /** 열어 둔 [repositories]를 닫는 책임을 지는 변경 목록이다. */
    private class GitDiffSource(
        override val commitA: String?, override val commitB: String?, override val changes: List<FileChange>, override val algorithm: TextDiffAlgorithm,
        private val resources: List<AutoCloseable>
    ) : DiffSource {
        override fun close() { resources.forEach { runCatching { it.close() } } }
    }

    private fun source(repositories: List<Repository>, repositoryA: Repository, repositoryB: Repository, commitA: ObjectId?, commitB: ObjectId?): DiffSource {
        val readerA = repositoryA.newObjectReader()
        val readerB = repositoryB.newObjectReader()
        val reader = MultipleRepositoryObjectReader(listOf(readerA, readerB))
        val formatter = DiffFormatter(NullOutputStream.INSTANCE)
        formatter.setReader(reader, repositoryA.config)
        formatter.isDetectRenames = true

        val treeA = commitA?.let { RevWalk(repositoryA).use { walk -> walk.parseTree(it) } }
        val treeB = commitB?.let { RevWalk(repositoryB).use { walk -> walk.parseTree(it) } }
        val parserA: AbstractTreeIterator = treeA?.let { CanonicalTreeParser().also { p -> p.reset(reader, it) } } ?: EmptyTreeIterator()
        val parserB: AbstractTreeIterator = treeB?.let { CanonicalTreeParser().also { p -> p.reset(reader, it) } } ?: EmptyTreeIterator()

        val changes = formatter.scan(parserA, parserB).map { entry ->
            val pathA = entry.getPath(DiffEntry.Side.OLD)
            val pathB = entry.getPath(DiffEntry.Side.NEW)
            val hasA = treeA != null && entry.changeType in A_SIDE_CHANGES
            val hasB = treeB != null && entry.changeType in B_SIDE_CHANGES
            FileChange(
                type = typeOf(entry.changeType), pathA = if (hasA) pathA else null, pathB = if (hasB) pathB else null,
                oldMode = entry.oldMode.bits, newMode = entry.newMode.bits,
                contentA = { if (hasA) read(repositoryA, pathA, treeA!!) else null },
                contentB = { if (hasB) read(repositoryB, pathB, treeB!!) else null }
            )
        }
        val algorithm = when (repositoryB.config.getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_ALGORITHM, DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)) {
            DiffAlgorithm.SupportedAlgorithm.MYERS -> TextDiffAlgorithm.MYERS
            else -> TextDiffAlgorithm.HISTOGRAM
        }
        return GitDiffSource(commitA?.name, commitB?.name, changes, algorithm, listOf(readerA, readerB) + repositories)
    }

    /** [tree] 안의 [path] 파일 내용이다. JGit의 큰 객체 한도를 넘으면 [ContentTooLargeException]이다. */
    private fun read(repository: Repository, path: String, tree: ObjectId): ByteArray = try {
        repository.open(TreeWalk.forPath(repository, path, tree).getObjectId(0)).bytes
    } catch (e: LargeObjectException) {
        throw ContentTooLargeException()
    }

    private fun typeOf(type: DiffEntry.ChangeType): FileChangeType = when (type) {
        DiffEntry.ChangeType.ADD -> FileChangeType.ADD
        DiffEntry.ChangeType.MODIFY -> FileChangeType.MODIFY
        DiffEntry.ChangeType.DELETE -> FileChangeType.DELETE
        DiffEntry.ChangeType.RENAME -> FileChangeType.RENAME
        DiffEntry.ChangeType.COPY -> FileChangeType.COPY
    }

    private companion object {
        val A_SIDE_CHANGES = listOf(DiffEntry.ChangeType.DELETE, DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY)
        val B_SIDE_CHANGES = listOf(DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY)
    }
}
