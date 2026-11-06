package dev.yona.repository.hg

import dev.yona.domain.vcs.VcsType
import dev.yona.repository.RepositoryLifecycle
import org.springframework.stereotype.Component

/**
 * Mercurial 저장소의 수명을 [HgRepositories]에 맡기는 [RepositoryLifecycle] 구현이다. 기본 위치는 이름표로 정하고 바꾸는 설정이 없으므로
 * 바꾸기는 아무 일도 하지 않는다. 포크는 [HgRepositories.cloneLocal]이고 원격 가져오기는 7b에서 더한다.
 */
@Component
class HgLifecycle(private val hg: HgRepositories) : RepositoryLifecycle {

    override val type: VcsType get() = VcsType.MERCURIAL

    override fun create(owner: String, name: String) = hg.create(owner, name)

    override fun delete(owner: String, name: String) = hg.delete(owner, name)

    override fun move(owner: String, name: String, newOwner: String, newName: String): Boolean = hg.move(owner, name, newOwner, newName)

    override fun refNames(owner: String, name: String): List<String> = hg.refNames(owner, name)

    override fun defaultBranch(owner: String, name: String): String = hg.defaultBranch(owner, name)

    override fun setDefaultBranch(owner: String, name: String, target: String) = Unit

    override fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) = hg.cloneLocal(owner, name, forkOwner, forkName)
}
