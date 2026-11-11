package dev.yona.repository.hg

import dev.yona.domain.vcs.VcsType
import dev.yona.repository.RepositoryLifecycle
import org.springframework.stereotype.Component

/**
 * Mercurial 저장소의 수명을 [HgRepositories]에 맡기는 [RepositoryLifecycle] 구현이다. 기본 위치는 이름표로 정하고 바꾸는 설정이 없으므로
 * 바꾸기는 아무 일도 하지 않는다. 포크는 [HgRepositories.cloneLocal]이고 원격 가져오기는 [HgRepositories.cloneRemote]이다.
 */
@Component
class HgLifecycle(private val hg: HgRepositories) : RepositoryLifecycle {

    override val type: VcsType get() = VcsType.MERCURIAL

    override fun create(owner: String, name: String) = hg.create(owner, name)

    override fun delete(owner: String, name: String) = hg.delete(owner, name)

    override fun move(owner: String, name: String, newOwner: String, newName: String): Boolean = hg.move(owner, name, newOwner, newName)

    override fun refNames(owner: String, name: String): List<String> = hg.refNames(owner, name)

    override fun defaultBranch(owner: String, name: String): String = hg.defaultBranch(owner, name)

    /**
     * 기본 브랜치를 [target]으로 정한다(`refs/heads/<북마크>` 또는 북마크 이름). 없는 북마크나 가상 이름 `HEAD`는 [IllegalArgumentException]이다.
     * 북마크 이름을 기억하는 방식이라 그 북마크에 새 커밋이 쌓이면 기본 위치도 따라간다([HgDefaultBranch]).
     */
    override fun setDefaultBranch(owner: String, name: String, target: String) {
        val bookmark = target.removePrefix(HgRepositories.HEADS)
        hg.open(owner, name).use { h ->
            require(bookmark != "HEAD" && bookmark in HgRevisions.bookmarks(h)) { "기본으로 정할 수 없는 브랜치이다: $target" }
            HgDefaultBranch.write(h, bookmark)
        }
    }

    override fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String) = hg.cloneLocal(owner, name, forkOwner, forkName)

    override fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?) = hg.cloneRemote(owner, name, url, authId, authPw)
}
