package dev.yona.repository.hg

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.vcs.RefUpdate
import dev.yona.repository.PushEvents
import dev.yona.repository.PushPolicy
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.lib.HgRepository
import io.github.search5.hg4j.transport.HgHttpWireServer
import io.github.search5.hg4j.transport.HgSshWireServer
import org.springframework.stereotype.Component

/**
 * Mercurial 푸시의 검사와 뒤처리를 저장소 종류와 무관한 [PushPolicy], [PushEvents]에 연결하는 어댑터이다(X-7 6단계, D-86). Git의 [dev.yona.repository.GitHooks]에
 * 해당한다. hg4j 서버의 훅을 받아 [HgPushUpdates]로 [RefUpdate]로 바꿔 넘길 뿐 판단은 하지 않는다.
 *
 * - **푸시 전**: 이름표(bookmark) 변경(`prepushkey`)마다 보호 규칙을 따지고 막아야 하면 훅이 `false`를 돌려 푸시를 되돌린다. 체인지셋이 도착하는 일
 *   자체(`pretxnchangegroup`)는 막지 않는다: 같은 푸시의 이름표 이동이 뒤따라 오는지 그 시점에 알 수 없고, 이름표를 움직이지 않는 체인지셋은 브랜치를
 *   바꾸지 않기 때문이다(`decisions.md` A-379). 거부 사유는 훅이 [io.github.search5.hg4j.errors.HgHookAbortException]을 던져 클라이언트 화면에 보이고
 *   감사 기록(`hg.push_rejected`)에도 남는다.
 * - **푸시 뒤**: 요청 하나 동안의 체인지그룹(`changegroup`)과 이름표 이동(`pushkey`)을 모았다가 요청이 끝난 뒤 한 번에 [PushEvents.afterPush]로 넘긴다.
 */
@Component
class HgHooks(private val policy: PushPolicy, private val events: PushEvents, private val signed: HgSignedCommits) {

    /**
     * 요청 하나의 푸시를 다루는 훅 묶음이다. 요청마다 새로 만들고, [attachTo]로 서버에 걸고, 서블릿이 끝난 뒤 [finish]를 부른다.
     */
    inner class Session(private val project: Project, private val sender: User?, private val pusher: User?) {
        private val nodes = mutableListOf<String>()
        private val moves = mutableListOf<RefUpdate>()

        /** HTTP 서버에 푸시 전 검사와 수집 훅을 건다. */
        fun attachTo(server: HgHttpWireServer): HgHttpWireServer = server
            .registerPrePushkeyHook(prePushkey).registerPostPushkeyHook(postPushkey).registerPostChangegroupHook(postChangegroup)

        /** SSH 서버에 푸시 전 검사와 수집 훅을 건다. */
        fun attachTo(server: HgSshWireServer): HgSshWireServer = server
            .registerPrePushkeyHook(prePushkey).registerPostPushkeyHook(postPushkey).registerPostChangegroupHook(postChangegroup)

        private val prePushkey = HgHook { context -> allowed(context) }
        private val postPushkey = HgHook { context -> collectMove(context); true }
        private val postChangegroup = HgHook { context -> collectNodes(context); true }

        private fun isBookmark(context: Map<String, Any?>) = context["namespace"] == "bookmarks"

        private fun update(context: Map<String, Any?>): RefUpdate {
            val repository = context["repository"] as HgRepository
            // 이 훅은 같은 푸시의 체인지그룹을 적용한 뒤에 불려(hg4j 수정 `dd32851`) 새 체인지셋도 변경 로그에서 읽힌다
            return HgPushUpdates.bookmark(HgRevisions.changelog(repository), context["key"] as String, context["old"] as String, context["new"] as String)
        }

        private fun allowed(context: Map<String, Any?>): Boolean {
            if (!isBookmark(context)) return true
            val update = update(context)
            // 서명 커밋 요구는 Mercurial에서 아직 지원하지 않는다(D-86 7번): 서명 검사 목록은 비워 둔다
            val rejections = policy.evaluate(project, sender, VCS_TAG, listOf(update)) { signed.unverified(project, it) }
            // 거절하면 사유를 클라이언트에 알린다: `error: prepushkey hook failed: <사유>`와 `abort: <사유>`(hg4j `HgHookAbortException`)
            if (rejections.isNotEmpty()) throw io.github.search5.hg4j.errors.HgHookAbortException(rejections.first().message)
            return true
        }

        private fun collectMove(context: Map<String, Any?>) {
            if (isBookmark(context)) moves += update(context)
        }

        @Suppress("UNCHECKED_CAST")
        private fun collectNodes(context: Map<String, Any?>) {
            nodes += context["nodes"] as List<String>
        }

        /** 푸시가 반영된 뒤의 처리를 한다. 받은 체인지셋도 이름표 이동도 없으면 아무것도 하지 않는다. */
        fun finish(changelog: io.github.search5.hg4j.storage.Revlog) {
            val updates = HgPushUpdates.of(changelog, nodes, moves)
            if (updates.isNotEmpty()) events.afterPush(project, pusher, VCS_TAG, updates)
        }
    }

    /**
     * [project]에 대한 푸시 하나를 다룰 훅 묶음을 만든다. [sender]는 보호 규칙의 바이패스를 판정하는 사용자이고(배포 키는 `null`), [pusher]는 푸시 뒤 작업을
     * 귀속할 사용자이다. 사용자가 푸시하면 둘이 같다.
     */
    fun session(project: Project, sender: User?, pusher: User? = sender): Session = Session(project, sender, pusher)

    private companion object {
        /** 지표와 감사에 쓰는 종류 이름이다. */
        const val VCS_TAG = "hg"
    }
}
