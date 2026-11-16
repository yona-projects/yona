package dev.yona.ssh

import java.io.InputStream
import java.io.OutputStream

/**
 * SSH 명령이 요구하는 저장소 접근이다(추가 개발 항목 X-3, `decisions.md` D-66). 어떤 저장소 종류의 명령인지는 처리기가 정하고, 인가는 종류와 무관하게 이 요청으로 판정한다.
 *
 * @property vcs 저장소 종류(`GIT`, 나중에 Mercurial)
 * @property owner 프로젝트 소유자 이름
 * @property name 프로젝트 이름
 * @property write 푸시처럼 저장소를 바꾸는 명령이면 `true`, 복제처럼 읽기만 하면 `false`이다
 * @property service 지표의 `service` 태그 값이다. git은 `upload_pack`(복제)과 `receive_pack`(푸시)이다. Mercurial은 `serve`이다
 * @property writeOptional 명령 줄만으로는 읽기인지 쓰기인지 알 수 없을 때(Mercurial의 `serve --stdio`) `true`이다. 이때 인가는 읽기 권한만 요구하고, 쓸 수 있는지는
 *   [SshAuthorization.Allowed.writable]로 알려 처리기가 푸시를 막게 한다
 * @property deferred 저장소가 명령 줄에 없고 프로토콜의 첫 메시지로 알려지는 요청이면 `true`이다(Subversion의 `svnserve -t`). 이때 [owner], [name]은 비어 있고
 *   처리기가 저장소를 알게 된 뒤 [SshDeferredContext.authorize]로 인가한다([SshVcsHandler.executeDeferred])
 */
data class SshRepositoryRequest(
    val vcs: String, val owner: String, val name: String, val write: Boolean, val service: String = if (write) "receive_pack" else "upload_pack", val writeOptional: Boolean = false,
    val deferred: Boolean = false
)

/**
 * 저장소를 나중에 알게 되는 요청([SshRepositoryRequest.deferred])을 실행할 때 처리기가 받는 문맥이다.
 *
 * @property login 이 접속의 사용자 로그인 아이디이다. 배포 키로 접속했으면 `deploy-key-<번호>`이다(저장소가 기록하는 작성자로 쓴다)
 * @property authorize 처리기가 알아낸 저장소(`owner`, `name`)를 이 접속이 읽거나 쓸 수 있는지 판정한다. 읽을 수 없거나 없으면 [SshAuthorization.NotFound]이다
 */
class SshDeferredContext(val login: String, val authorize: (owner: String, name: String) -> SshAuthorization)

/** 명령 하나를 실행할 때 쓰는 세 줄의 입출력이다. 입력은 클라이언트가 보내는 바이트, 출력과 오류는 클라이언트로 가는 바이트이다. */
class SshStreams(val input: InputStream, val output: OutputStream, val error: OutputStream)

/**
 * 저장소 종류별 SSH 명령 처리기이다(`decisions.md` D-66 6번). 인증과 인가(키 조회, 접근 권한, 읽기 전용 배포 키)는 [SshAccess]가 공통으로 하고, 처리기는 명령을 읽는 일과
 * 인가를 통과한 뒤 실제 프로토콜을 실행하는 일만 맡는다. [GitSshHandler]와 Mercurial 처리기([dev.yona.ssh.HgSshHandler], `hg -R <저장소> serve --stdio`)가 있다.
 */
interface SshVcsHandler {

    /** 명령 줄을 읽는다. 이 처리기의 명령이 아니거나 올바르지 않으면 `null`이다. */
    fun parse(command: String): SshRepositoryRequest?

    /**
     * 인가를 통과한 요청을 실행한다. 프로토콜의 오류는 클라이언트가 읽을 수 있게 [SshStreams.error]에 쓴다.
     *
     * @param grant 인가 결과. 프로젝트와, 규칙의 바이패스 판정에 쓰는 사용자([SshAuthorization.Allowed.sender], 배포 키는 `null`)와, 푸시 뒤 작업을 귀속할 사용자([SshAuthorization.Allowed.pusher])가 있다
     * @return 프로세스 종료 코드와 같은 값. 성공은 0이다
     */
    fun execute(grant: SshAuthorization.Allowed, request: SshRepositoryRequest, command: String, streams: SshStreams): Int

    /**
     * 저장소가 명령 줄에 없는 요청([SshRepositoryRequest.deferred])을 실행한다. 처리기가 프로토콜의 첫 메시지에서 저장소를 알아낸 뒤 [SshDeferredContext.authorize]로 인가하고,
     * 허용되지 않으면 프로토콜의 오류로 알린다. 기본은 지원하지 않는다.
     */
    fun executeDeferred(context: SshDeferredContext, request: SshRepositoryRequest, command: String, streams: SshStreams): Int =
        throw UnsupportedOperationException("이 처리기는 저장소를 나중에 아는 요청을 지원하지 않는다")

    /**
     * 프로토콜을 시작하기 전에 거절하는 사유를 이 종류의 클라이언트가 읽을 수 있는 방식으로 쓴다. 쓰지 못했으면(기본) `false`이고, 호출한 쪽이 접속 방식의 기본 방식(오류 채널이나 git의
     * 오류 줄)으로 알린다. Mercurial은 클라이언트가 인사를 보내고 응답을 기다리므로 그에 맞춰 답해야 한다.
     */
    fun rejectProtocol(request: SshRepositoryRequest, message: String, streams: SshStreams): Boolean = false
}
