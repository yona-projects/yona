package dev.yona.ssh

import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.user.UserRepository
import dev.yona.observability.VcsMetrics
import org.springframework.stereotype.Component

/**
 * 인증한 SSH 접속이 보낸 명령 한 줄을 읽고, 인가하고, 실행한다(추가 개발 항목 X-3, `decisions.md` D-66, D-67). 내장 SSH 서버([SshServerLifecycle])와 시스템 sshd 중계([SshRelayServer])가
 * 접속을 받는 방식만 다르고 이 뒤의 일은 같으므로 한곳에 둔다: 처리기([SshVcsHandler])가 명령을 읽고, [SshAccess]가 인가하고, 처리기가 프로토콜을 실행하며,
 * 푸시 훅이 남기는 감사 기록에 행위자와 접속 주소가 들어가고, 시간과 결과가 `yona.vcs.request` 지표로 남는다.
 */
@Component
class SshCommandRunner(
    private val handlers: List<SshVcsHandler>,
    private val access: SshAccess,
    private val audit: AuditRecorder,
    private val metrics: VcsMetrics,
    private val users: UserRepository,
    private val deployKeys: DeployKeyService
) {

    /** 인증한 접속의 감사 행위자이다. 사용자 키이면 사용자, 배포 키이면 배포 키(번호와 이름)이다. */
    fun actorOf(principal: SshPrincipal): AuditActor {
        principal.userId?.let { id -> return AuditActor(AuditActorType.USER, id, users.findById(id).orElse(null)?.loginId) }
        val id = principal.deployKeyId
        return AuditActor(AuditActorType.DEPLOY_KEY, id, id?.let { deployKeys.findName(it) })
    }

    /** 감사 기록에 접속 주소와 접속 종류(`ssh`)를 남기도록 문맥을 정해 [block]을 실행한다. [actor]가 없으면 익명이다. */
    fun <T> withRemote(actor: AuditActor?, address: String?, block: () -> T): T =
        audit.withRemote(actor ?: AuditActor(AuditActorType.ANONYMOUS, null, null), address, CHANNEL, block)

    /**
     * 명령을 실행하고 종료 코드(성공 0)를 돌려준다. 명령을 읽을 수 없거나 인가하지 못하면 프로토콜을 시작하기 전에 [reject]로 사유 한 줄을 알리고 1을 돌려준다.
     * 사유를 어떻게 전할지는 접속 방식이 정한다(내장 서버는 오류 채널에 쓰고, 중계는 git의 오류 줄(pkt-line `ERR`)로 보낸다).
     *
     * @param address 접속한 클라이언트 주소. 알 수 없으면 `null`
     */
    fun run(principal: SshPrincipal, line: String, streams: SshStreams, address: String?, reject: (String) -> Unit): Int {
        val (handler, request) = handlers.firstNotNullOfOrNull { h -> h.parse(line)?.let { h to it } } ?: return fail(reject, "ERROR: Invalid command.")
        // 푸시 훅이 남기는 감사 기록(git.push 등)에 행위자와 접속 주소가 들어가도록 이 스레드의 문맥을 정한다
        return withRemote(actorOf(principal), address) { execute(handler, request, line, principal, streams, reject) }
    }

    /** 인가하고 실행한다. 시간과 결과를 `yona.vcs.request`로 남긴다(HTTP와 같은 지표, 결과는 `ok`, `denied`, `not_found`, `error`). */
    private fun execute(handler: SshVcsHandler, request: SshRepositoryRequest, line: String, principal: SshPrincipal, streams: SshStreams, reject: (String) -> Unit): Int {
        var exit = 1
        val vcs = request.vcs.lowercase()
        metrics.request(vcs, request.service, "code") { measured ->
            exit = when (val result = access.authorize(principal, request)) {
                SshAuthorization.NotFound -> { measured.result = "not_found"; fail(reject, "ERROR: Repository not found.") }
                is SshAuthorization.Denied -> { measured.result = "denied"; fail(reject, result.message) }
                is SshAuthorization.Allowed -> {
                    val target = if (result.wiki) "wiki" else "code"
                    measured.target = target
                    handler.execute(
                        result, request, line,
                        SshStreams(metrics.countedInput(vcs, target, streams.input), metrics.countedOutput(vcs, target, streams.output), streams.error)
                    ).also { if (it != 0) measured.result = "error" }
                }
            }
        }
        return exit
    }

    private fun fail(reject: (String) -> Unit, message: String): Int {
        reject(message)
        return 1
    }

    companion object {
        /** 로그와 지표, 감사 기록의 채널 이름이다. */
        const val CHANNEL = "ssh"
    }
}
