package dev.yona.ssh

import dev.yona.repository.svn.SvnRepositories
import dev.yona.ssh.svn.RaSvnResolution
import dev.yona.ssh.svn.RaSvnResolver
import dev.yona.ssh.svn.RaSvnServer
import org.springframework.stereotype.Component

/**
 * SSH로 들어오는 Subversion 접근(`svn+ssh://git@호스트:포트/소유자/이름`)을 처리한다. Subversion 클라이언트는 원격에서 `svnserve -t`를 실행하고 표준 입출력으로
 * `ra_svn` 프로토콜을 주고받는다. 명령 줄에는 저장소가 없고 프로토콜의 첫 메시지(클라이언트가 보내는 주소)에 들어 있으므로 요청은 저장소를 나중에 아는 요청이다
 * ([SshRepositoryRequest.deferred]).
 *
 * 인가는 Git, Mercurial과 같은 [SshAccess]가 하며, 읽기 권한만 있는 접속은 읽기만 하고 커밋은 거절한다([RaSvnResolution.Found.writable]). 프로토콜은 시스템의
 * `svnserve`를 쓰지 않고 [RaSvnServer]가 직접 처리하므로 서버에 Subversion 패키지가 필요 없다.
 */
@Component
class SvnSshHandler(private val svn: SvnRepositories) : SshVcsHandler {

    override fun parse(command: String): SshRepositoryRequest? =
        if (command.trim() == COMMAND) SshRepositoryRequest(VCS, "", "", write = false, service = "serve", writeOptional = true, deferred = true) else null

    override fun execute(grant: SshAuthorization.Allowed, request: SshRepositoryRequest, command: String, streams: SshStreams): Int =
        throw UnsupportedOperationException("Subversion은 저장소를 나중에 아는 요청이다")

    override fun executeDeferred(context: SshDeferredContext, request: SshRepositoryRequest, command: String, streams: SshStreams): Int {
        RaSvnServer(streams.input, streams.output, context.login, resolver(context)).serve()
        streams.output.flush()
        return 0
    }

    /** 주소의 경로(`/소유자/이름/하위경로`)에서 앞 두 단계를 저장소로 보고 인가한다. 읽을 수 없거나 없으면 같은 문구로 거절한다. */
    private fun resolver(context: SshDeferredContext) = RaSvnResolver { path ->
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size < 2 || segments.any { it == ".." || it == "." }) return@RaSvnResolver RaSvnResolution.Failed(NOT_FOUND)
        val (owner, name) = segments
        when (val grant = context.authorize(owner, name)) {
            is SshAuthorization.Allowed -> RaSvnResolution.Found(svn.directoryOf(owner, name), "/$owner/$name", grant.writable)
            else -> RaSvnResolution.Failed(NOT_FOUND)
        }
    }

    private companion object {
        /** 클라이언트가 실행하는 명령이다. 터널 옵션은 `svn`이 붙이지 않는다. */
        const val COMMAND = "svnserve -t"
        const val VCS = "Subversion"
        const val NOT_FOUND = "Repository not found."
    }
}
