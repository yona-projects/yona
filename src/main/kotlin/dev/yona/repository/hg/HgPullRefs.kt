package dev.yona.repository.hg

import io.github.search5.hg4j.api.BookmarkCommand
import io.github.search5.hg4j.api.PhaseCommand
import io.github.search5.hg4j.api.BundleCommand
import io.github.search5.hg4j.api.UnbundleCommand
import org.springframework.stereotype.Component

/**
 * 풀 리퀘스트의 숨은 참조를 Mercurial 저장소에 두는 도구이다(X-7 8a, `decisions.md` D-87). Git은 받는 저장소 안에 `refs/yobi/pull/<번호>/head`,
 * `merged`를 두지만 Mercurial에는 ref가 없다. 그래서 보내는 쪽 체인지셋을 받는 저장소로 가져와 **secret 단계**로 두고(hg4j가 secret과 그것을
 * 가리키는 이름표를 `clone`, `pull`에서 숨긴다) **예약 이름표** `yobi/pull/<번호>/head`, `merged`로 가리킨다. 예약 이름표는 브랜치 목록에 나오지
 * 않고 푸시도 거부한다([HgRepositories.RESERVED_BOOKMARK_PREFIX]).
 *
 * 체인지셋은 번들로만 옮긴다(`pull`은 보내는 쪽의 이름표까지 받는 저장소에 반영하므로 쓰지 않는다, [transfer]). 풀 리퀘스트를 닫아도 secret 체인지셋은 지우지 않는다(이름표만 지운다). 병합하면 [publish]로 공개 단계로 올린다.
 */
@Component
class HgPullRefs(private val repositories: HgRepositories) {

    /** [number]번 풀 리퀘스트에서 보내는 쪽 머리를 가리키는 예약 이름표이다. */
    fun headRef(number: Long): String = "${HgRepositories.RESERVED_BOOKMARK_PREFIX}pull/$number/head"

    /** [number]번 풀 리퀘스트의 병합 결과를 가리키는 예약 이름표이다. */
    fun mergedRef(number: Long): String = "${HgRepositories.RESERVED_BOOKMARK_PREFIX}pull/$number/merged"

    /**
     * 보내는 저장소([fromOwner]/[fromName])의 체인지셋 [node]까지를 받는 저장소([toOwner]/[toName])로 가져오고, **이번에 새로 들어온** 체인지셋을
     * secret 단계로 바꾸고, 예약 이름표 [ref]가 [node]를 가리키게 한다(있으면 옮긴다. [ref]가 `null`이면 이름표를 두지 않는다, 병합 가능 여부만 볼 때). 받는 저장소에 이미 있던 체인지셋의 단계는 바꾸지 않는다.
     *
     * @return 가져온 체인지셋의 노드(40자)
     * @throws java.io.IOException 보내는 저장소를 읽거나 가져오지 못했을 때
     */
    fun fetch(toOwner: String, toName: String, fromOwner: String, fromName: String, node: String, ref: String?): String {
        repositories.open(toOwner, toName).use { hg ->
            val target = HgRevisions.changelog(hg)
            val before = target.revisionCount
            // 받는 저장소에 이미 있는 체인지셋이면 가져올 것이 없다
            if (HgChangesets.revOf(target, node) < 0) transfer(hg, fromOwner, fromName, node, repositories.directoryOf(toOwner, toName))
            val changelog = HgRevisions.changelog(hg)
            // 이번에 새로 들어온 체인지셋 가운데 새 체인지셋 안에 부모가 없는 것(뿌리)만 secret으로 한다. 후손은 따라서 secret이 된다
            val added = (before until changelog.revisionCount).toSet()
            for (rev in added) {
                val changeset = HgChangesets.read(changelog, rev) ?: continue
                if (changeset.parents.none { it in added }) {
                    PhaseCommand(hg.repository).setRevision(changeset.node).setPhase(PHASE_SECRET).setForce(true).call()
                }
            }
            if (ref != null) BookmarkCommand(hg.repository).setBookmarkName(ref).setRevision(node).setForce(true).call()
        }
        return node
    }

    /**
     * 보내는 저장소의 [node]까지의 체인지셋을 번들로 만들어 받는 저장소([target])에 적용한다. `pull`을 쓰지 않는 까닭: `pull`은 실제 `hg pull`처럼
     * 보내는 쪽의 이름표를 받는 저장소에 반영해, 받는 저장소의 `main`이 포크의 머리로 병합 전에 저절로 앞서 나가고 포크의 다른 이름표가 들어온다. 번들은
     * 체인지셋만 옮긴다(원본 hg의 방식, `hg bundle --type none-v3 -r HEAD <받는 저장소>`와 `hg unbundle`. LFS 저장소는 v3 번들만 허용한다). 번들 범위는 받는 저장소([BundleCommand.setDestination])가
     * 이미 가진 것을 빼고 hg4j가 정한다.
     */
    private fun transfer(target: io.github.search5.hg4j.api.Hg, fromOwner: String, fromName: String, node: String, targetDir: java.io.File) {
        val file = java.io.File.createTempFile("yona-pull-", ".hg")
        try {
            repositories.open(fromOwner, fromName).use { fork ->
                // 새로 들어올 체인지셋이 가리키는 LFS 본문을 먼저 받는 저장소로 옮긴다(번들은 포인터만 옮긴다, D-90)
                copyLfsBlobs(fork, fromOwner, fromName, node, targetDir, target)
                BundleCommand(fork.repository).setType(BundleCommand.BundleType.NONE_V3).setOutputFile(file).setRevision(node).setDestination(targetDir.path).call()
            }
            UnbundleCommand(target.repository).setBundleFile(file).call()
        } finally {
            file.delete()
        }
    }

    /** 보내는 저장소에만 있는 체인지셋([node]까지)이 가리키는 LFS 본문을 [targetDir] 저장소로 옮긴다. */
    private fun copyLfsBlobs(fork: io.github.search5.hg4j.api.Hg, fromOwner: String, fromName: String, node: String, targetDir: java.io.File, target: io.github.search5.hg4j.api.Hg) {
        val source = repositories.directoryOf(fromOwner, fromName)
        if (!HgLfsBlobs.objectsDir(source).isDirectory) return
        val forkLog = HgRevisions.changelog(fork)
        val targetLog = HgRevisions.changelog(target)
        val head = HgChangesets.revOf(forkLog, node)
        if (head < 0) return
        for (rev in HgChangesets.ancestors(forkLog, head) + head) {
            val changeset = HgChangesets.read(forkLog, rev) ?: continue
            if (HgChangesets.revOf(targetLog, changeset.node) >= 0) continue
            io.github.search5.hg4j.lfs.HgLfsFiles.changedBy(fork.repository, rev, false).values.forEach { pointer ->
                if (pointer != null) HgLfsBlobs.copy(source, targetDir, pointer.oid)
            }
        }
    }

    /** 예약 이름표 [ref]가 가리키는 노드이다. 없으면 `null`이다. */
    fun resolve(owner: String, name: String, ref: String): String? =
        repositories.open(owner, name).use { hg -> HgRevisions.allBookmarks(hg)[ref] }

    /** 예약 이름표 [ref]를 [node]로 옮긴다(없으면 만든다). */
    fun point(owner: String, name: String, ref: String, node: String) {
        repositories.open(owner, name).use { hg -> BookmarkCommand(hg.repository).setBookmarkName(ref).setRevision(node).setForce(true).call() }
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
