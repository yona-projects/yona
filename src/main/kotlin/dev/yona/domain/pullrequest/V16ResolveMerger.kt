package dev.yona.domain.pullrequest

import dev.yona.repository.diff.V16DiffAlgorithm
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.merge.MergeAlgorithm
import org.eclipse.jgit.merge.ResolveMerger

/**
 * 기준 트리를 직접 정해 세 방향 병합을 하는 병합기이다. 리베이스 병합(X-28, `decisions.md` D-41)이 커밋 하나씩 다시 적용할 때 쓴다.
 *
 * 충돌 판정은 [V16RecursiveMerger]와 같이 JGit 4.5.0(v1.6)의 diff 동작을 따르도록 [V16DiffAlgorithm]으로 감싼 알고리즘을 쓴다.
 */
class V16ResolveMerger(repository: Repository) : ResolveMerger(repository, true) {
    init {
        val algorithm = DiffAlgorithm.getAlgorithm(
            repository.config.getEnum(ConfigConstants.CONFIG_DIFF_SECTION, null, ConfigConstants.CONFIG_KEY_ALGORITHM, DiffAlgorithm.SupportedAlgorithm.HISTOGRAM))
        mergeAlgorithm = MergeAlgorithm(V16DiffAlgorithm(algorithm))
    }
}
