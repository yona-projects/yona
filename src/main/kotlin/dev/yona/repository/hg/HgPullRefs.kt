package dev.yona.repository.hg

import io.github.search5.hg4j.api.BookmarkCommand
import io.github.search5.hg4j.api.PhaseCommand
import io.github.search5.hg4j.api.PullCommand
import org.springframework.stereotype.Component

/**
 * 풀 리퀘스트의 숨은 참조를 Mercurial 저장소에 두는 도구이다(X-7 8a, `decisions.md` D-87). Git은 받는 저장소 안에 `refs/yobi/pull/<번호>/head`,
 * `merged`를 두지만 Mercurial에는 ref가 없다. 그래서 보내는 쪽 체인지셋을 받는 저장소로 가져와 **secret 단계**로 두고(hg4j가 secret과 그것을
 * 가리키는 이름표를 `clone`, `pull`에서 숨긴다) **예약 이름표** `yobi/pull/<번호>/head`, `merged`로 가리킨다. 예약 이름표는 브랜치 목록에 나오지
 * 않고 푸시도 거부한다([HgRepositories.RESERVED_BOOKMARK_PREFIX]).
 *
 * 풀 리퀘스트를 닫아도 secret 체인지셋은 지우지 않는다(이름표만 지운다). 병합하면 [publish]로 공개 단계로 올린다.
 */
@Component
class HgPullRefs(private val repositories: HgRepositories) {

    /** [number]번 풀 리퀘스트에서 보내는 쪽 머리를 가리키는 예약 이름표이다. */
    fun headRef(number: Long): String = "${HgRepositories.RESERVED_BOOKMARK_PREFIX}pull/$number/head"

    /** [number]번 풀 리퀘스트의 병합 결과를 가리키는 예약 이름표이다. */
    fun mergedRef(number: Long): String = "${HgRepositories.RESERVED_BOOKMARK_PREFIX}pull/$number/merged"

    /**
     * 보내는 저장소([fromOwner]/[fromName])의 체인지셋 [node]까지를 받는 저장소([toOwner]/[toName])로 가져오고, **이번에 새로 들어온** 체인지셋을
     * secret 단계로 바꾸고, 예약 이름표 [ref]가 [node]를 가리키게 한다(있으면 옮긴다). 받는 저장소에 이미 있던 체인지셋의 단계는 바꾸지 않는다.
     *
     * @return 가져온 체인지셋의 노드(40자)
     * @throws java.io.IOException 보내는 저장소를 읽거나 가져오지 못했을 때
     */
    fun fetch(toOwner: String, toName: String, fromOwner: String, fromName: String, node: String, ref: String): String {
        val source = repositories.directoryOf(fromOwner, fromName).canonicalPath
        repositories.open(toOwner, toName).use { hg ->
            val before = HgRevisions.changelog(hg).revisionCount
            PullCommand(hg.repository).setSource(source).setRevisions(listOf(node)).call()
            val changelog = HgRevisions.changelog(hg)
            // 이번에 새로 들어온 체인지셋 가운데 새 체인지셋 안에 부모가 없는 것(뿌리)만 secret으로 한다. 후손은 따라서 secret이 된다
            val added = (before until changelog.revisionCount).toSet()
            for (rev in added) {
                val changeset = HgChangesets.read(changelog, rev) ?: continue
                if (changeset.parents.none { it in added }) {
                    PhaseCommand(hg.repository).setRevision(changeset.node).setPhase(PHASE_SECRET).setForce(true).call()
                }
            }
            BookmarkCommand(hg.repository).setBookmarkName(ref).setRevision(node).setForce(true).call()
        }
        return node
    }

    /** 예약 이름표 [ref]를 지운다. 체인지셋은 지우지 않는다(D-87 2번). 없는 이름표를 지워도 오류가 없다. */
    fun delete(owner: String, name: String, ref: String) {
        repositories.open(owner, name).use { hg ->
            if (ref in HgRevisions.allBookmarks(hg)) BookmarkCommand(hg.repository).setBookmarkName(ref).setDelete(true).call()
        }
    }

    /** 체인지셋 [node]와 그 조상을 공개 단계로 올려 `clone`, `pull`에 보이게 한다. 병합할 때 쓴다. */
    fun publish(owner: String, name: String, node: String) {
        repositories.open(owner, name).use { hg ->
            PhaseCommand(hg.repository).setRevision(node).setPhase(PHASE_PUBLIC).call()
        }
    }

    private companion object {
        const val PHASE_PUBLIC = 0
        const val PHASE_SECRET = 2
    }
}
