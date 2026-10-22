package dev.yona.repository.diff

import dev.yona.domain.project.Project
import dev.yona.io.CharsetDetection
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
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
import org.springframework.stereotype.Service

/**
 * 커밋과 커밋, 또는 서로 다른 저장소의 커밋 사이의 파일별 차이를 계산한다. v1.6 `GitRepository.getDiff`, `getFileDiffs`
 * (`app/playRepository/GitRepository.java:731-775, 1451-1730`)에 해당한다.
 *
 * 바뀐 파일마다 이름 바꿈과 복사를 찾고(`DiffFormatter`), 텍스트 파일은 히스토그램 알고리즘(저장소 설정 `diff.algorithm`이 있으면 그것)으로
 * 바뀐 곳 목록(`EditList`)을 구한다. 이진 파일은 내용 없이 표시만 한다. 파일이 너무 크거나(500KB, 5,000줄) 전체 차이가 커지면(이후 파일은 크기 초과 표시)
 * 내용을 싣지 않고, 파일이 2,000개를 넘으면 거기서 멈춘다.
 *
 * 풀 리퀘스트처럼 서로 다른 저장소의 커밋을 비교할 때는 두 저장소의 객체를 함께 읽는 판독기를 쓴다.
 */
@Service
class DiffService(private val repositories: RepositoryService) {

    /** [revision]의 커밋이 첫 부모와 비교해 바꾼 파일들이다. 첫 커밋이면 모든 파일이 추가이다. 리비전을 풀 수 없으면 `null`이다. */
    fun commitDiff(project: Project, revision: String): List<FileDiff>? = repositories.openRepository(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        val commit = try { RevWalk(repository).use { it.parseCommit(id) } } catch (e: org.eclipse.jgit.errors.MissingObjectException) { return null }
        val parent = if (commit.parentCount > 0) commit.getParent(0).id else null
        fileDiffs(repository, repository, parent, id)
    }

    /** 같은 저장소의 [revA]에서 [revB]로 바뀐 파일들이다. */
    fun diff(project: Project, revA: String, revB: String): List<FileDiff> = repositories.openRepository(project).use { repository ->
        fileDiffs(repository, repository, repository.resolve(revA), repository.resolve(revB))
    }

    /** [projectA]의 [revA]에서 [projectB]의 [revB]로 바뀐 파일들이다(포크 사이의 비교). */
    fun diff(projectA: Project, revA: String, projectB: Project, revB: String): List<FileDiff> =
        repositories.openRepository(projectA).use { repoA ->
            repositories.openRepository(projectB).use { repoB -> fileDiffs(repoA, repoB, repoA.resolve(revA), repoB.resolve(revB)) }
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

    private fun fileDiffs(repositoryA: Repository, repositoryB: Repository, commitA: ObjectId?, commitB: ObjectId?): List<FileDiff> {
        val readerA = repositoryA.newObjectReader()
        val readerB = repositoryB.newObjectReader()
        try {
            val reader = MultipleRepositoryObjectReader(listOf(readerA, readerB))
            val formatter = DiffFormatter(NullOutputStream.INSTANCE)
            formatter.setReader(reader, repositoryA.config)
            formatter.isDetectRenames = true

            val treeA = commitA?.let { RevWalk(repositoryA).use { walk -> walk.parseTree(it) } }
            val treeB = commitB?.let { RevWalk(repositoryB).use { walk -> walk.parseTree(it) } }
            val parserA: AbstractTreeIterator = treeA?.let { CanonicalTreeParser().also { p -> p.reset(reader, it) } } ?: EmptyTreeIterator()
            val parserB: AbstractTreeIterator = treeB?.let { CanonicalTreeParser().also { p -> p.reset(reader, it) } } ?: EmptyTreeIterator()

            val result = ArrayList<FileDiff>()
            var size = 0
            var lines = 0
            for (entry in formatter.scan(parserA, parserB)) {
                val fileDiff = FileDiff()
                fileDiff.commitA = commitA?.name
                fileDiff.commitB = commitB?.name
                fileDiff.changeType = entry.changeType
                fileDiff.oldMode = entry.oldMode
                fileDiff.newMode = entry.newMode
                val pathA = entry.getPath(DiffEntry.Side.OLD)
                val pathB = entry.getPath(DiffEntry.Side.NEW)

                var rawA: ByteArray? = null
                if (treeA != null && entry.changeType in A_SIDE_CHANGES) {
                    fileDiff.pathA = pathA
                    try {
                        rawA = repositoryA.open(TreeWalk.forPath(repositoryA, pathA, treeA).getObjectId(0)).bytes
                        fileDiff.isBinaryA = RawText.isBinary(rawA)
                        fileDiff.a = if (fileDiff.isBinaryA) null else text(rawA)
                    } catch (e: LargeObjectException) {
                        fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED)
                    }
                }
                var rawB: ByteArray? = null
                if (treeB != null && entry.changeType in B_SIDE_CHANGES) {
                    fileDiff.pathB = pathB
                    try {
                        rawB = repositoryB.open(TreeWalk.forPath(repositoryB, pathB, treeB).getObjectId(0)).bytes
                        fileDiff.isBinaryB = RawText.isBinary(rawB)
                        fileDiff.b = if (fileDiff.isBinaryB) null else text(rawB)
                    } catch (e: LargeObjectException) {
                        fileDiff.addError(FileDiff.Error.B_SIZE_EXCEEDED)
                    }
                }

                if (size > DIFF_SIZE_LIMIT || lines > DIFF_LINE_LIMIT) {
                    fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED)
                    result.add(fileDiff)
                    continue
                }

                val a = fileDiff.a
                val b = fileDiff.b
                if (a != null && b != null && !(fileDiff.isBinaryA || fileDiff.isBinaryB) && entry.changeType in listOf(DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.RENAME)) {
                    val algorithm = DiffAlgorithm.getAlgorithm(
                        repositoryB.config.getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_ALGORITHM, DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                    )
                    fileDiff.editList = algorithm.diff(RawTextComparator.DEFAULT, a, b)
                    fileDiff.hunks()?.let { size += it.totalSize; lines += it.totalLines }
                }
                if (b != null && !fileDiff.isBinaryB && entry.changeType == DiffEntry.ChangeType.ADD) {
                    lines += b.size()
                    size += rawB!!.size
                }
                if (a != null && !fileDiff.isBinaryA && entry.changeType == DiffEntry.ChangeType.DELETE) {
                    lines += a.size()
                    size += rawA!!.size
                }
                if (result.size > DIFF_FILE_LIMIT) break
                result.add(fileDiff)
            }
            return result
        } finally {
            readerA.close()
            readerB.close()
        }
    }

    /** 내용에서 문자 집합을 추정해 읽은 뒤 UTF-8 바이트로 다시 만든 텍스트이다(v1.6 그대로 기본 문자 집합으로 바이트를 만든다). */
    private fun text(raw: ByteArray): RawText = RawText(String(raw, charset(CharsetDetection.detect(raw))).toByteArray())

    companion object {
        /** 전체 차이의 글자 수 한도이다(파일 한도의 3배). 넘으면 이후 파일은 내용을 싣지 않는다. */
        const val DIFF_SIZE_LIMIT = 3 * FileDiff.SIZE_LIMIT

        /** 전체 차이의 줄 수 한도이다. */
        const val DIFF_LINE_LIMIT = 3 * FileDiff.LINE_LIMIT

        /** 한 번에 비교하는 파일 수 한도이다. */
        const val DIFF_FILE_LIMIT = 2000

        private val A_SIDE_CHANGES = listOf(DiffEntry.ChangeType.DELETE, DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY)
        private val B_SIDE_CHANGES = listOf(DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.MODIFY, DiffEntry.ChangeType.RENAME, DiffEntry.ChangeType.COPY)
    }
}
