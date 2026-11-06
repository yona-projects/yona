package dev.yona.repository.diff

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.ContentTooLargeException
import dev.yona.domain.vcs.DiffSource
import dev.yona.domain.vcs.FileChangeType
import dev.yona.domain.vcs.TextDiffAlgorithm
import dev.yona.io.CharsetDetection
import dev.yona.repository.VcsBackends
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.springframework.stereotype.Service

/**
 * 커밋과 커밋, 또는 서로 다른 저장소의 커밋 사이의 파일별 차이를 계산한다. v1.6 `GitRepository.getDiff`, `getFileDiffs`
 * (`app/playRepository/GitRepository.java:731-775, 1451-1730`)에 해당한다.
 *
 * 바뀐 파일 목록은 저장소 종류별 [dev.yona.repository.DiffReader]가 구한다([VcsBackends]가 프로젝트의 종류로 고른다). 이 서비스는 그 목록과 파일 내용만으로
 * 글을 비교하므로 어느 종류든 같은 차이가 나온다. 텍스트 파일은 히스토그램 알고리즘(저장소 설정 `diff.algorithm`이 있으면 그것)으로
 * 바뀐 곳 목록(`EditList`)을 구한다. 이진 파일은 내용 없이 표시만 한다. 파일이 너무 크거나(500KB, 5,000줄) 전체 차이가 커지면(이후 파일은 크기 초과 표시)
 * 내용을 싣지 않고, 파일이 2,000개를 넘으면 거기서 멈춘다.
 */
@Service
class DiffService(private val backends: VcsBackends, private val metrics: dev.yona.observability.VcsMetrics) {

    /** [revision]의 커밋이 첫 부모와 비교해 바꾼 파일들이다. 첫 커밋이면 모든 파일이 추가이다. 리비전을 풀 수 없으면 `null`이다. */
    fun commitDiff(project: Project, revision: String): List<FileDiff>? = metrics.operation(project.vcs, "diff") {
        backends.diff(project.vcs).commitDiff(project, revision)?.use { fileDiffs(it) }
    }

    /** 같은 저장소의 [revA]에서 [revB]로 바뀐 파일들이다. */
    fun diff(project: Project, revA: String, revB: String): List<FileDiff> = metrics.operation(project.vcs, "diff") {
        backends.diff(project.vcs).diff(project, revA, revB).use { fileDiffs(it) }
    }

    /** [projectA]의 [revA]에서 [projectB]의 [revB]로 바뀐 파일들이다(포크 사이의 비교). */
    fun diff(projectA: Project, revA: String, projectB: Project, revB: String): List<FileDiff> = metrics.operation(projectA.vcs, "diff") {
        backends.diff(projectA.vcs).diff(projectA, revA, projectB, revB).use { fileDiffs(it) }
    }

    private fun fileDiffs(source: DiffSource): List<FileDiff> {
        val result = ArrayList<FileDiff>()
        var size = 0
        var lines = 0
        for (change in source.changes) {
            val fileDiff = FileDiff()
            fileDiff.commitA = source.commitA
            fileDiff.commitB = source.commitB
            fileDiff.changeType = change.type
            fileDiff.oldMode = change.oldMode
            fileDiff.newMode = change.newMode

            var rawA: ByteArray? = null
            if (change.pathA != null) {
                fileDiff.pathA = change.pathA
                try {
                    rawA = change.contentA()
                    if (rawA != null) {
                        fileDiff.isBinaryA = dev.yona.io.RawTextBinary.isBinary(rawA)
                        fileDiff.a = if (fileDiff.isBinaryA) null else text(rawA)
                    }
                } catch (e: ContentTooLargeException) {
                    fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED)
                }
            }
            var rawB: ByteArray? = null
            if (change.pathB != null) {
                fileDiff.pathB = change.pathB
                try {
                    rawB = change.contentB()
                    if (rawB != null) {
                        fileDiff.isBinaryB = dev.yona.io.RawTextBinary.isBinary(rawB)
                        fileDiff.b = if (fileDiff.isBinaryB) null else text(rawB)
                    }
                } catch (e: ContentTooLargeException) {
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
            if (a != null && b != null && !(fileDiff.isBinaryA || fileDiff.isBinaryB) && change.type in listOf(FileChangeType.MODIFY, FileChangeType.RENAME)) {
                val algorithm = V16DiffAlgorithm(DiffAlgorithm.getAlgorithm(
                    if (source.algorithm == TextDiffAlgorithm.MYERS) DiffAlgorithm.SupportedAlgorithm.MYERS else DiffAlgorithm.SupportedAlgorithm.HISTOGRAM
                ))
                fileDiff.editList = algorithm.diff(RawTextComparator.DEFAULT, a, b)
                fileDiff.hunks()?.let { size += it.totalSize; lines += it.totalLines }
            }
            if (b != null && !fileDiff.isBinaryB && change.type == FileChangeType.ADD) {
                lines += b.size()
                size += rawB!!.size
            }
            if (a != null && !fileDiff.isBinaryA && change.type == FileChangeType.DELETE) {
                lines += a.size()
                size += rawA!!.size
            }
            if (result.size > DIFF_FILE_LIMIT) break
            result.add(fileDiff)
        }
        return result
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
    }
}
