package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.transport.PacketLineOut
import org.eclipse.jgit.transport.RefAdvertiser
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.springframework.stereotype.Service
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Git smart-HTTP 서비스의 광고(`info/refs`)와 RPC(`git-upload-pack`, `git-receive-pack`)를 JGit으로 처리한다.
 * v1.6 `RepositoryService.gitAdvertise`, `gitRpc`(`app/playRepository/RepositoryService.java:183-300`)에 해당한다.
 *
 * v1.6은 RPC를 별도 스레드에서 파이프로 흘렸다. v2는 서블릿 스레드에서 응답 스트림에 바로 쓴다(D-15, 동작은 같다).
 * 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class GitHttpService(private val repositories: RepositoryService, private val hooks: GitHooks) {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    /** 프로젝트의 Git 저장소가 디스크에 있으면 `true`이다. 없으면(만들다 만 프로젝트 등) 응답을 쓰기 전에 알 수 있게 한다. */
    fun exists(project: Project): Boolean = try {
        repositories.openRepository(project).close()
        true
    } catch (e: org.eclipse.jgit.errors.RepositoryNotFoundException) {
        false
    }

    /** `# service=…` 한 줄과 서비스가 광고하는 ref 목록을 [out]에 쓴다. */
    fun advertise(project: Project, service: String, out: OutputStream) {
        // 디렉터리가 없어도 빈 저장소와 같은 광고를 낸다 (v1.6 `gitAdvertise`, 저장소 존재를 확인하지 않는다).
        repositories.openRepositoryForHttp(project).use { repository ->
            val packetLine = PacketLineOut(out)
            packetLine.writeString("# service=$service\n")
            packetLine.end()
            val advertiser = RefAdvertiser.PacketLineOutRefAdvertiser(packetLine)
            if (service == UPLOAD_PACK) {
                UploadPack(repository).apply { isBiDirectionalPipe = false }.sendAdvertisedRefs(advertiser)
            } else if (service == RECEIVE_PACK) {
                ReceivePack(repository).sendAdvertisedRefs(advertiser)
            }
        }
        out.flush()
    }

    /**
     * 클라이언트의 요청 [input]을 처리해 결과를 [out]에 쓴다. 푸시(`git-receive-pack`)는 푸시 전후 훅을 건다.
     *
     * 처리 중 `IOException`(잘못된 want, 깨진 팩, 연결 끊김 등)은 밖으로 던지지 않고 로그만 남긴다. v1.6은 RPC를 별도 스레드에서 돌려
     * 응답 상태 200을 먼저 보내고 `Logger.error("uploadPack failed", e)`, `Logger.error("receivePack failed", e)`로만 남긴다.
     * 끝에는 입력과 출력을 닫는다. 디렉터리가 없어도 같은 방식으로 처리한다(`info/refs`와 같다).
     */
    fun rpc(project: Project, service: String, input: InputStream, out: OutputStream, sender: dev.yona.domain.user.User? = null) {
        try {
            repositories.openRepositoryForHttp(project).use { repository ->
                try {
                    when (service) {
                        UPLOAD_PACK -> UploadPack(repository).apply { isBiDirectionalPipe = false }.upload(input, out, null)
                        RECEIVE_PACK -> ReceivePack(repository).apply {
                            isBiDirectionalPipe = false
                            preReceiveHook = hooks.preReceive(project, sender)
                            postReceiveHook = hooks.postReceive(project, sender)
                        }.receive(input, out, null)
                    }
                } catch (e: IOException) {
                    log.error(if (service == UPLOAD_PACK) "uploadPack failed" else "receivePack failed", e)
                }
            }
        } finally {
            closeQuietly(service, "input stream") { input.close() }
            closeQuietly(service, "output stream") { out.flush(); out.close() }
        }
    }

    private fun closeQuietly(service: String, what: String, close: () -> Unit) {
        try {
            close()
        } catch (e: IOException) {
            log.error("{}: Failed to close {}", if (service == UPLOAD_PACK) "uploadPack" else "receivePack", what, e)
        }
    }

    companion object {
        const val UPLOAD_PACK = "git-upload-pack"
        const val RECEIVE_PACK = "git-receive-pack"

        /** 지원하는 서비스이면 `true`이다 (v1.6 `GitApp.isSupportedService`). */
        fun isSupported(service: String?): Boolean = service == UPLOAD_PACK || service == RECEIVE_PACK
    }
}
