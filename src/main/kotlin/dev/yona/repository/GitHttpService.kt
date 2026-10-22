package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.transport.PacketLineOut
import org.eclipse.jgit.transport.RefAdvertiser
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.springframework.stereotype.Service
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

    /** `# service=…` 한 줄과 서비스가 광고하는 ref 목록을 [out]에 쓴다. */
    fun advertise(project: Project, service: String, out: OutputStream) {
        val packetLine = PacketLineOut(out)
        packetLine.writeString("# service=$service\n")
        packetLine.end()
        val advertiser = RefAdvertiser.PacketLineOutRefAdvertiser(packetLine)
        repositories.openRepository(project).use { repository ->
            if (service == UPLOAD_PACK) {
                UploadPack(repository).apply { isBiDirectionalPipe = false }.sendAdvertisedRefs(advertiser)
            } else if (service == RECEIVE_PACK) {
                ReceivePack(repository).sendAdvertisedRefs(advertiser)
            }
        }
        out.flush()
    }

    /** 클라이언트의 요청 [input]을 처리해 결과를 [out]에 쓴다. 푸시(`git-receive-pack`)는 푸시 전후 훅을 건다. */
    fun rpc(project: Project, service: String, input: InputStream, out: OutputStream, sender: dev.yona.domain.user.User? = null) {
        repositories.openRepository(project).use { repository ->
            when (service) {
                UPLOAD_PACK -> UploadPack(repository).apply { isBiDirectionalPipe = false }.upload(input, out, null)
                RECEIVE_PACK -> ReceivePack(repository).apply {
                    isBiDirectionalPipe = false
                    preReceiveHook = hooks.preReceive()
                    postReceiveHook = hooks.postReceive(project, sender)
                }.receive(input, out, null)
            }
        }
        out.flush()
    }

    companion object {
        const val UPLOAD_PACK = "git-upload-pack"
        const val RECEIVE_PACK = "git-receive-pack"

        /** 지원하는 서비스이면 `true`이다 (v1.6 `GitApp.isSupportedService`). */
        fun isSupported(service: String?): Boolean = service == UPLOAD_PACK || service == RECEIVE_PACK
    }
}
