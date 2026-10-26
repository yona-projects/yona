package dev.yona.domain.pullrequest

import dev.yona.repository.diff.V16DiffAlgorithm
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeAlgorithm
import org.eclipse.jgit.merge.RecursiveMerger

/**
 * 병합 충돌 판정에 JGit 4.5.0(v1.6)의 diff 동작을 쓰는 재귀 병합기이다 (`MergeStrategy.RECURSIVE.newMerger(repository, true)`에 해당).
 *
 * 병합기는 저장소 설정 `diff.algorithm`(없으면 히스토그램)으로 `MergeAlgorithm`을 만든다. 여기서는 그 알고리즘을 [V16DiffAlgorithm]으로 감싸,
 * 중복 줄이 많은 입력에서 충돌 여부가 v1.6과 달라지는 일을 막는다.
 */
class V16RecursiveMerger(repository: Repository) : RecursiveMerger(repository, true) {
    init {
        val algorithm = DiffAlgorithm.getAlgorithm(
            repository.config.getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_ALGORITHM, DiffAlgorithm.SupportedAlgorithm.HISTOGRAM))
        mergeAlgorithm = MergeAlgorithm(V16DiffAlgorithm(algorithm))
    }
}
