package dev.yona.repository.hg

import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
import io.github.search5.hg4j.storage.Revlog

/**
 * Mercurial 푸시를 저장소 종류와 무관한 [RefUpdate]로 바꾸는 도우미이다(X-7 6단계, D-86).
 *
 * Git의 푸시는 ref마다 옛 번호와 새 번호가 한 묶음으로 오지만 Mercurial의 푸시는 두 가지가 따로 온다: 체인지셋이 도착하는 **체인지그룹**과, 이름표(bookmark)를
 * 옮기는 **pushkey**이다. 브랜치는 이름표로 보이므로(D-86 1번) 둘을 이렇게 합친다.
 *
 * - 이름표의 생성, 이동, 삭제는 `refs/heads/<이름표>`의 [RefUpdate]이다. 옛 값이 조상이 아닌 곳으로의 이동은 강제 갱신이다.
 * - 이름표가 가리키지 않는 새 머리는 그 머리의 이름 붙은 브랜치(named branch, 기본 `default`) 이름으로 `refs/heads/<브랜치>`에 올린다. 옛 번호는 이번
 *   푸시가 가져온 가장 오래된 체인지셋의 첫째 부모이고, 부모가 없으면(저장소의 첫 체인지셋) 영 번호이다.
 */
object HgPushUpdates {

    /** 이름표 [name]이 [old]에서 [new]로 옮겨진 것의 [RefUpdate]이다. 값이 빈 문자열이면 만들거나 지운 것이고 영 번호로 바뀐다. */
    fun bookmark(changelog: Revlog, name: String, old: String, new: String): RefUpdate =
        RefUpdate(refOf(name), old.ifEmpty { RefUpdate.NULL_ID }, new.ifEmpty { RefUpdate.NULL_ID }, bookmarkType(changelog, old, new))

    /**
     * 이름표 [name]의 ref 이름이다. 예약 이름표(`yobi/…`, 풀 리퀘스트의 숨은 참조)는 Git의 `refs/yobi/…`처럼 취급해 [dev.yona.repository.PushPolicy]가
     * 예약된 이름으로 거부하게 한다. 나머지는 `refs/heads/<이름표>`이다.
     */
    fun refOf(name: String): String =
        if (name.startsWith(HgRepositories.RESERVED_BOOKMARK_PREFIX)) "refs/$name" else "refs/heads/$name"

    /** 이름표의 이동 종류이다. 새 값이 옛 값의 후손이 아니면 강제 갱신이다. 새 값을 이 변경 로그에서 찾지 못하면(같은 푸시로 막 들어온 것) 일반 갱신이다. 옛 값을 이 저장소에서 찾지 못해도 강제 갱신으로 본다. */
    fun bookmarkType(changelog: Revlog, old: String, new: String): RefUpdateType = when {
        old.isEmpty() -> RefUpdateType.CREATE
        new.isEmpty() -> RefUpdateType.DELETE
        else -> {
            val oldRev = HgChangesets.revOf(changelog, old)
            val newRev = HgChangesets.revOf(changelog, new)
            when {
                // 이름표 이동 훅은 같은 푸시의 체인지셋이 반영되는 도중에 불려 방금 들어온 새 체인지셋을 변경 로그에서 못 찾는다(hg4j 보완 요청 중). 판정할 수 없으면
                // 일반 갱신으로 본다: 서버의 이름표 검증과 클라이언트의 `--force` 요구가 비빨리감기를 따로 막는다
                newRev < 0 -> RefUpdateType.UPDATE
                oldRev >= 0 && oldRev in HgChangesets.ancestors(changelog, newRev) -> RefUpdateType.UPDATE
                else -> RefUpdateType.FORCE_UPDATE
            }
        }
    }

    /**
     * 한 번의 푸시를 [RefUpdate] 목록으로 합친다.
     *
     * @param nodes 체인지그룹으로 새로 들어온 체인지셋의 40자 노드(적용된 순서)
     * @param moves 같은 푸시의 이름표 이동([bookmark]의 결과)
     * @return 이름표 이동 다음에, 이름표가 가리키지 않는 새 머리마다 하나씩이다
     */
    fun of(changelog: Revlog, nodes: List<String>, moves: List<RefUpdate>): List<RefUpdate> {
        val revs = nodes.map { HgChangesets.revOf(changelog, it) }.filter { it >= 0 }.toSet()
        val pointed = moves.map { it.new }.toSet()
        val heads = revs.filter { rev -> revs.none { other -> rev in (HgChangesets.read(changelog, other)?.parents ?: emptyList()) } }
        val updates = moves.toMutableList()
        for (head in heads.sorted()) {
            val changeset = HgChangesets.read(changelog, head) ?: continue
            if (changeset.node in pointed) continue
            val pushed = HgChangesets.ancestors(changelog, head).filter { it in revs }
            val oldest = pushed.min()
            val parent = HgChangesets.read(changelog, oldest)?.parents?.firstOrNull()
            val old = parent?.let { HgChangesets.read(changelog, it)?.node } ?: RefUpdate.NULL_ID
            updates += RefUpdate("refs/heads/${changeset.branch}", old, changeset.node, if (parent == null) RefUpdateType.CREATE else RefUpdateType.UPDATE)
        }
        return updates
    }
}
