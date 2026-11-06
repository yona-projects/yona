package dev.yona.repository.svn

import dev.yona.domain.vcs.VcsType
import dev.yona.repository.RepositoryLifecycle
import org.springframework.stereotype.Component

/**
 * Subversion 저장소의 수명을 [SvnRepositories]에 맡기는 [dev.yona.repository.RepositoryLifecycle] 구현이다. 브랜치는 `HEAD` 하나이고 기본
 * 브랜치를 바꾸지 못한다(v1.6 `setDefaultBranch`는 Subversion에서 아무 일도 하지 않는다). 포크와 원격 가져오기는 지원하지 않는다.
 */
@Component
class SvnLifecycle(private val svn: SvnRepositories) : RepositoryLifecycle {

    override val type: VcsType get() = VcsType.SUBVERSION

    override fun create(owner: String, name: String) = svn.create(owner, name)

    override fun delete(owner: String, name: String) = svn.delete(owner, name)

    override fun move(owner: String, name: String, newOwner: String, newName: String): Boolean = svn.move(owner, name, newOwner, newName)

    override fun refNames(owner: String, name: String): List<String> = svn.refNames()

    override fun defaultBranch(owner: String, name: String): String = svn.defaultBranch()

    override fun setDefaultBranch(owner: String, name: String, target: String) = Unit
}
