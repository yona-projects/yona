package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.eclipse.jgit.transport.PostReceiveHook
import org.eclipse.jgit.transport.PreReceiveHook
import org.eclipse.jgit.transport.ReceiveCommand
import org.eclipse.jgit.transport.ReceivePack
import org.springframework.stereotype.Component

/**
 * Git 푸시 전후의 훅을 JGit에 연결한다. v1.6 `RepositoryService.createPreReceiveHook`, `createPostReceiveHook`
 * (`app/playRepository/RepositoryService.java:253-270`)에 해당한다.
 *
 * 검사와 뒤처리의 본문은 저장소 종류와 무관한 [PushPolicy]와 [PushEvents]에 있다. 이 클래스는 JGit의 푸시 명령(`ReceiveCommand`)을
 * [dev.yona.domain.vcs.RefUpdate]로 바꿔 넘기고, 거부 결과를 명령에 적고, 서명 확인할 커밋을 JGit으로 구하는 일만 한다(Mercurial 도입을 위한 공통화 3단계, D-86).
 * 푸시 전에는 내부용 `refs/yona` 아래 ref와 규칙 묶음이 막는 푸시를 거부하고, 푸시 뒤에는 마지막 푸시 시각, 최근 푸시한 브랜치, 이슈 언급 이벤트,
 * 풀 리퀘스트 확인, 새 커밋 알림, 감사 기록을 처리한다.
 */
@Component
class GitHooks(
    private val policy: PushPolicy,
    private val events: PushEvents,
    private val gpgVerification: dev.yona.domain.gpgkey.GpgVerificationService
) {

    /**
     * 푸시 전 훅이다. 검사는 [PushPolicy.evaluate]가 하고, 거부한 명령에 이유를 적는다. 규칙 묶음이 바이패스를 허용하면 프로젝트 관리자와 사이트 관리자인
     * [sender]는 그 규칙 묶음을 우회한다. 풀 리퀘스트 병합처럼 서버가 직접 브랜치를 갱신하는 것은 이 검사를 거치지 않는다.
     */
    fun preReceive(project: Project, sender: User?): PreReceiveHook = PreReceiveHook { receivePack, commands ->
        val byRef = commands.associateBy { it.refName }
        val updates = GitRefUpdates.of(commands)
        policy.evaluate(project, sender, VCS_TAG, updates) { update -> unverifiedCommits(receivePack.repository, byRef.getValue(update.ref)) }
            .forEach { byRef.getValue(it.update.ref).setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, it.message) }
    }

    /** 이번 푸시가 더하는 커밋 가운데 Verified 서명이 아닌 것의 (짧은 번호, 이유 코드) 목록이다. 새 커밋은 새 팁에서 거슬러 오르되 옛 팁과 저장소의 다른 참조가 이미 가진 커밋은 뺀 것이다. */
    private fun unverifiedCommits(repository: org.eclipse.jgit.lib.Repository, command: ReceiveCommand): List<Pair<String, String>> =
        org.eclipse.jgit.revwalk.RevWalk(repository).use { walk ->
            walk.markStart(walk.parseCommit(command.newId))
            if (command.oldId != org.eclipse.jgit.lib.ObjectId.zeroId()) runCatching { walk.markUninteresting(walk.parseCommit(command.oldId)) }
            repository.refDatabase.refs.forEach { ref ->
                val id = ref.objectId
                if (id != null && ref.name != command.refName && !ref.name.startsWith("refs/yona")) runCatching { walk.markUninteresting(walk.parseCommit(id)) }
            }
            walk.mapNotNull { commit ->
                val signed = if (commit.rawGpgSignature != null) dev.yona.domain.gpgkey.GitSignatures.ofCommit(commit.rawBuffer) else null
                val result = gpgVerification.verifyCommit(signed, commit.committerIdent.emailAddress)
                if (result.verified) null else commit.abbreviate(SHORT_ID).name() to result.reason.code
            }
        }

    /** 푸시 뒤 훅이다. 뒤처리는 [PushEvents.afterPush]가 한다. */
    fun postReceive(project: Project, sender: User?): PostReceiveHook = PostReceiveHook { _: ReceivePack, commands ->
        events.afterPush(project, sender, VCS_TAG, GitRefUpdates.of(commands))
    }

    private companion object {
        /** 지표와 감사에 쓰는 종류 이름이다. */
        const val VCS_TAG = "git"

        /** 서명 검사에서 거부 메시지에 쓰는 짧은 커밋 번호의 길이이다. */
        const val SHORT_ID = 7
    }
}
