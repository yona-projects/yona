package dev.yona.ssh

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import java.io.InputStream
import java.io.OutputStream

/**
 * SSH 명령이 요구하는 저장소 접근이다(추가 개발 항목 X-3, `decisions.md` D-66). 어떤 저장소 종류의 명령인지는 처리기가 정하고, 인가는 종류와 무관하게 이 요청으로 판정한다.
 *
 * @property vcs 저장소 종류(`GIT`, 나중에 Mercurial)
 * @property owner 프로젝트 소유자 이름
 * @property name 프로젝트 이름
 * @property write 푸시처럼 저장소를 바꾸는 명령이면 `true`, 복제처럼 읽기만 하면 `false`이다
 */
data class SshRepositoryRequest(val vcs: String, val owner: String, val name: String, val write: Boolean)

/** 명령 하나를 실행할 때 쓰는 세 줄의 입출력이다. 입력은 클라이언트가 보내는 바이트, 출력과 오류는 클라이언트로 가는 바이트이다. */
class SshStreams(val input: InputStream, val output: OutputStream, val error: OutputStream)

/**
 * 저장소 종류별 SSH 명령 처리기이다(`decisions.md` D-66 6번). 인증과 인가(키 조회, 접근 권한, 읽기 전용 배포 키)는 [SshAccess]가 공통으로 하고, 처리기는 명령을 읽는 일과
 * 인가를 통과한 뒤 실제 프로토콜을 실행하는 일만 맡는다. 지금은 [GitSshHandler]만 있고 Mercurial 처리기(`hg -R <저장소> serve --stdio`)는 X-7에서 더한다.
 */
interface SshVcsHandler {

    /** 명령 줄을 읽는다. 이 처리기의 명령이 아니거나 올바르지 않으면 `null`이다. */
    fun parse(command: String): SshRepositoryRequest?

    /**
     * 인가를 통과한 요청을 실행한다. 프로토콜의 오류는 클라이언트가 읽을 수 있게 [SshStreams.error]에 쓴다.
     *
     * @param sender 푸시한 사용자. 배포 키로 접속했으면 `null`이다
     * @return 프로세스 종료 코드와 같은 값. 성공은 0이다
     */
    fun execute(project: Project, request: SshRepositoryRequest, command: String, sender: User?, streams: SshStreams): Int
}
