package dev.yona.repository

import dev.yona.domain.vcs.VcsType
import org.springframework.stereotype.Component

/** Git 저장소를 다루는 [VcsBackend] 구현 묶음이다(JGit). */
@Component
class GitBackend(override val lifecycle: GitLifecycle, override val commits: GitCommitReader) : VcsBackend {

    override val type: VcsType get() = VcsType.GIT
}
