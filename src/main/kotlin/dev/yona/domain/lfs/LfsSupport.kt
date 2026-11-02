package dev.yona.domain.lfs

import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService

/**
 * 프로젝트가 Git LFS를 지원하는지를 한 곳에서 정한다(`decisions.md` D-79 15번). 지금은 Git 저장소만 지원하고, Mercurial(X-7)을 붙일 때 이 목록에 더한다.
 * Subversion은 지원하지 않는다.
 */
object LfsSupport {

    /** [project]의 저장소 종류가 LFS를 지원하면 `true`이다. */
    fun supports(project: Project): Boolean = project.vcs == RepositoryService.VCS_GIT
}
