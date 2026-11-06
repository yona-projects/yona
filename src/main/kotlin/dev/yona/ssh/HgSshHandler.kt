package dev.yona.ssh

import dev.yona.repository.hg.HgHooks
import dev.yona.repository.hg.HgRepositories
import dev.yona.repository.hg.HgRevisions
import io.github.search5.hg4j.api.HgHook
import io.github.search5.hg4j.transport.HgSshWireServer
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * SSH로 들어오는 Mercurial 명령(`hg -R <소유자/이름> serve --stdio`)을 처리한다(X-7 6단계, D-86). `hg`는 `ssh://git@호스트:포트/소유자/이름` 주소에서 원격으로
 * 이 명령을 실행하고 표준 입출력으로 와이어 프로토콜을 주고받는다. 복제와 푸시가 같은 명령이라 명령 줄만으로는 구분하지 못하므로 요청은 쓰기가 선택인 읽기로
 * 인가하고([SshRepositoryRequest.writeOptional]), 쓸 수 없는 접속([SshAuthorization.Allowed.writable]이 `false`)은 푸시 훅이 막는다. 푸시 전 검사와 뒤처리는
 * HTTP와 같은 [HgHooks]이다.
 *
 * 프로토콜 처리는 hg4j의 [HgSshWireServer]가 한다. 거절은 클라이언트가 인사를 보내고 응답을 기다리므로 [HgSshWireServer.rejectConnection]으로 알린다.
 */
@Component
class HgSshHandler(private val repositories: HgRepositories, private val hooks: HgHooks) : SshVcsHandler {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun parse(command: String): SshRepositoryRequest? = Companion.parse(command)

    override fun execute(grant: SshAuthorization.Allowed, request: SshRepositoryRequest, command: String, streams: SshStreams): Int {
        val project = grant.project
        return try {
            repositories.open(requireNotNull(project.owner), requireNotNull(project.name)).use { hg ->
                val server = HgSshWireServer(hg.repository)
                val session = hooks.session(project, grant.sender, grant.pusher)
                if (grant.writable) {
                    // 규칙의 바이패스는 사용자 키의 사용자만 받는다(배포 키는 규칙을 우회하지 못한다). 푸시 뒤 작업은 귀속할 사용자가 있으면 그 사용자의 푸시로 처리한다(D-68)
                    session.attachTo(server)
                } else {
                    server.registerPreChangegroupHook(HgHook { false }).registerPrePushkeyHook(HgHook { false })
                }
                server.handleConnection(streams.input, streams.output)
                if (grant.writable) session.finish(HgRevisions.changelog(hg))
            }
            0
        } catch (e: IOException) {
            // 연결이 끊기거나 데이터가 깨진 경우이다. HTTP와 같이 로그만 남긴다
            log.error("hg serve --stdio failed", e)
            1
        } finally {
            streams.output.flush()
            streams.error.flush()
        }
    }

    override fun rejectProtocol(request: SshRepositoryRequest, message: String, streams: SshStreams): Boolean = try {
        HgSshWireServer.rejectConnection(streams.input, streams.output, message.removePrefix("ERROR: "))
        streams.output.flush()
        true
    } catch (e: IOException) {
        false
    }

    companion object {
        /** `hg -R <경로> serve --stdio`의 앞뒤 부분이다. 경로는 따옴표로 싸거나 싸지 않아도 된다. */
        private val COMMAND = Regex("^hg -R (.+) serve --stdio$")

        /**
         * `hg -R 소유자/이름 serve --stdio` 같은 명령 줄을 읽는다. 경로의 앞뒤 슬래시와 따옴표는 있어도 없어도 된다. `.git`은 이름의 일부라 떼지 않는다. 지원하지 않는
         * 명령이거나 경로가 두 단계(소유자, 이름)가 아니거나 `..`이나 셸 메타 문자가 있으면 `null`이다.
         */
        fun parse(command: String): SshRepositoryRequest? {
            val path = COMMAND.matchEntire(command.trim())?.groupValues?.get(1) ?: return null
            val (owner, name) = GitSshHandler.parsePath(path, stripGitSuffix = false) ?: return null
            return SshRepositoryRequest("Mercurial", owner, name, write = false, service = "serve", writeOptional = true)
        }
    }
}
