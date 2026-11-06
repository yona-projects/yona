package dev.yona.repository.svn

import dev.yona.domain.vcs.VcsType
import dev.yona.repository.VcsBackend
import org.springframework.stereotype.Component

/** Subversion 저장소를 다루는 [VcsBackend] 구현 묶음이다(SVNKit). */
@Component
class SvnBackend(override val lifecycle: SvnLifecycle, override val commits: SvnCommitReader) : VcsBackend {

    override val type: VcsType get() = VcsType.SUBVERSION
}
