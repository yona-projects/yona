package dev.yona.repository

import dev.yona.domain.vcs.VcsType
import org.springframework.stereotype.Component

/**
 * 한 종류의 저장소를 만들고 지우고 옮기고 참조를 알려 주는 계약이다. [RepositoryService]가 프로젝트의 `vcs` 값으로 구현을 골라 쓴다
 * (Mercurial 도입을 위한 공통화 2a, D-86). 종류마다 `if (vcs == ...)`로 가르던 자리를 구현 추가로 바꾼다.
 *
 * 소유자와 이름은 URL 디코딩이 끝난 값이다. 지원하지 않는 일([cloneLocal], [cloneRemote])은 기본으로 [UnsupportedOperationException]이다.
 */
interface RepositoryLifecycle {

    /** 이 구현이 맡는 저장소 종류. */
    val type: VcsType

    /** 저장소를 새로 만든다. */
    fun create(owner: String, name: String)

    /** 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun delete(owner: String, name: String)

    /** 저장소를 다른 소유자나 이름의 위치로 옮긴다. 원래 위치에 없으면 성공이고, 옮기지 못하면 `false`이다. */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean

    /** 저장소의 브랜치, 태그 등 참조 이름이다. */
    fun refNames(owner: String, name: String): List<String>

    /** 저장소의 기본 브랜치 이름이다. */
    fun defaultBranch(owner: String, name: String): String

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다. 바꿀 수 없는 종류는 아무 일도 하지 않는다. */
    fun setDefaultBranch(owner: String, name: String, target: String)

    /** 원본 저장소를 포크 저장소로 복제한다. */
    fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String): Unit =
        throw UnsupportedOperationException("지원하지 않는 VCS입니다: ${type.columnValue}")

    /** 다른 저장소를 주소로 가져와 복제한다. */
    fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?): Unit =
        throw UnsupportedOperationException("지원하지 않는 VCS입니다: ${type.columnValue}")
}

/**
 * 저장소 종류별 구현을 `project.vcs` 열 값으로 찾는 등록부이다. 새 종류는 [RepositoryLifecycle] 구현을 빈으로 등록하기만 하면 된다.
 */
@Component
class VcsBackends(lifecycles: List<RepositoryLifecycle>) {

    private val lifecycles: Map<VcsType, RepositoryLifecycle> = lifecycles.associateBy { it.type }

    /**
     * [vcs] 열 값의 수명 구현이다.
     *
     * @throws UnsupportedOperationException 모르는 값이거나 구현이 없을 때
     */
    fun lifecycle(vcs: String?): RepositoryLifecycle =
        VcsType.of(vcs)?.let { lifecycles[it] } ?: throw UnsupportedOperationException("지원하지 않는 VCS입니다: $vcs")

    companion object {
        /** 단위 시험용으로 구현을 직접 묶는다. */
        fun of(vararg lifecycles: RepositoryLifecycle) = VcsBackends(lifecycles.toList())
    }
}
