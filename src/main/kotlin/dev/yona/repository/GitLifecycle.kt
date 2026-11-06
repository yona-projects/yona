package dev.yona.repository

import dev.yona.domain.vcs.VcsType
import org.springframework.stereotype.Component

/** Git 저장소의 수명을 [GitRepositories]에 맡기는 [RepositoryLifecycle] 구현이다. */
@Component
class GitLifecycle(private val git: GitRepositories) : RepositoryLifecycle {

    override val type: VcsType get() = VcsType.GIT

    override fun create(owner: String, name: String) = git.create(owner, name)

    override fun delete(owner: String, name: String) = git.delete(owner, name)

    override fun move(owner: String, name: String, newOwner: String, newName: String): Boolean = git.move(owner, name, newOwner, newName)

    override fun refNames(owner: String, name: String): List<String> = git.refNames(owner, name)

    override fun defaultBranch(owner: String, name: String): String = git.defaultBranch(owner, name)

    override fun setDefaultBranch(owner: String, name: String, target: String) = git.setDefaultBranch(owner, name, target)

    override fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) = git.cloneLocal(owner, name, forkOwner, forkName)

    override fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?) = git.cloneRemote(owner, name, url, authId, authPw)
}
