package dev.yona.repository.diff

import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.Sequence
import org.eclipse.jgit.diff.SequenceComparator
import org.eclipse.jgit.diff.Subsequence
import org.eclipse.jgit.diff.SubsequenceComparator

/**
 * JGit 4.5.0의 `DiffAlgorithm.diff` 동작을 재현하는 어댑터이다 (v1.6 `GitRepository.getFileDiffs`가 쓰던 결과).
 *
 * JGit 4.7부터 `diff` 끝에서 모든 INSERT와 DELETE 편집을 가능한 한 뒤로 미는 `normalize`를 적용한다.
 * v1.6이 쓴 4.5.0은 마지막 편집이 INSERT일 때 그 하나만 뒤로 밀었으므로, 같은 입력이라도 편집 위치(삭제된 줄 번호 등)가 달라질 수 있다.
 * 이 클래스는 `diffNonCommon`은 [delegate]에 맡기고 `diff`의 꼬리 처리만 4.5.0 방식으로 되돌린다. 라이브러리 자체는 고치지 않는다.
 *
 * `HistogramDiff`와 `MyersDiff`의 `diffNonCommon`은 4.5.0과 7.8이 같다. JGit을 올릴 때는 `V16DiffAlgorithmTest`가 이 전제를 확인한다.
 *
 * @property delegate 실제 비교를 맡을 알고리즘. 저장소 설정 `diff.algorithm`으로 고른다
 */
class V16DiffAlgorithm(private val delegate: DiffAlgorithm) : DiffAlgorithm() {

    override fun <S : Sequence> diffNonCommon(cmp: SequenceComparator<in S>, a: S, b: S): EditList =
        delegate.diffNonCommon(cmp, a, b)

    override fun <S : Sequence> diff(cmp: SequenceComparator<in S>, a: S, b: S): EditList {
        val region = cmp.reduceCommonStartEnd(a, b, Edit(0, a.size(), 0, b.size()))
        return when (region.type) {
            Edit.Type.INSERT, Edit.Type.DELETE -> EditList.singleton(region)
            Edit.Type.EMPTY -> EditList(0)
            else -> {
                if (region.lengthA == 1 && region.lengthB == 1) return EditList.singleton(region)
                val subsequences = SubsequenceComparator(cmp)
                val sa = Subsequence.a(a, region)
                val sb = Subsequence.b(b, region)
                val edits = Subsequence.toBase(diffNonCommon(subsequences, sa, sb), sa, sb)
                val last = edits[edits.size - 1]
                if (last.type == Edit.Type.INSERT) {
                    while (last.endB < b.size() && cmp.equals(b, last.beginB, b, last.endB)) last.shift(1)
                }
                edits
            }
        }
    }
}
