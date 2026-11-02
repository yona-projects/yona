package dev.yona.ssh

import dev.yona.repository.GitHooks
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.transport.ReceivePack
import org.eclipse.jgit.transport.UploadPack
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * SSH로 들어오는 git 명령(`git-upload-pack`, `git-receive-pack`)을 처리한다(추가 개발 항목 X-3, `decisions.md` D-66). 복제와 푸시는 HTTP와 같은 JGit과 같은 푸시 훅
 * ([GitHooks])을 쓰므로 브랜치 보호 규칙, 서명 커밋 필수 규칙, 풀 리퀘스트와 웹훅 처리가 HTTP와 똑같이 적용된다. 연결이 양방향 파이프라 `git-upload-pack`도 요청과 응답을 한 연결에서 주고받는다.
 */
@Component
class GitSshHandler(private val repositories: RepositoryService, private val hooks: GitHooks, private val wikis: dev.yona.repository.WikiRepositories) : SshVcsHandler {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun parse(command: String): SshRepositoryRequest? = Companion.parse(command)

    override fun execute(grant: SshAuthorization.Allowed, request: SshRepositoryRequest, command: String, streams: SshStreams): Int {
        val project = grant.project
        if (grant.wiki && request.write) wikis.create(requireNotNull(project.owner), requireNotNull(project.name))
        return try {
            open(grant).use { repository ->
                if (request.write) {
                    ReceivePack(repository).apply {
                        if (grant.wiki) {
                            // 위키 저장소는 HTTP와 같이 푸시 전후 훅이 없고 브랜치 삭제는 거부한다(기본 브랜치를 지우면 위키가 비어 버린다)
                            isAllowDeletes = false
                        } else {
                            // 규칙의 바이패스는 사용자 키의 사용자만 받는다(배포 키는 규칙을 우회하지 못한다). 푸시 뒤 작업은 귀속할 사용자가 있으면 그 사용자의 푸시로 처리한다(D-68)
                            preReceiveHook = hooks.preReceive(project, grant.sender)
                            postReceiveHook = hooks.postReceive(project, grant.pusher)
                        }
                    }.receive(streams.input, streams.output, streams.error)
                } else {
                    UploadPack(repository).upload(streams.input, streams.output, streams.error)
                }
            }
            0
        } catch (e: RepositoryNotFoundException) {
            streams.error.write("ERROR: Repository not found.\n".toByteArray())
            1
        } catch (e: IOException) {
            // 연결이 끊기거나 팩이 깨진 경우이다. HTTP와 같이 로그만 남긴다
            log.error("{} failed", if (request.write) "receivePack" else "uploadPack", e)
            1
        } finally {
            streams.output.flush()
            streams.error.flush()
        }
    }

    /**
     * 저장소를 연다. 위키는 푸시를 받을 때 없으면 먼저 만들고, 복제는 위키를 한 번도 쓰지 않았어도 빈 저장소처럼 열린다(HTTP와 같다).
     */
    private fun open(grant: SshAuthorization.Allowed): org.eclipse.jgit.lib.Repository {
        if (!grant.wiki) return repositories.openRepository(grant.project)
        val owner = requireNotNull(grant.project.owner); val name = requireNotNull(grant.project.name)
        return wikis.openForHttp(owner, name)
    }

    companion object {
        private val SERVICES = mapOf("git-upload-pack" to false, "git upload-pack" to false, "git-receive-pack" to true, "git receive-pack" to true)

        /** 경로 한 단계의 허용 글자이다. 따옴표, 역슬래시, 공백, 셸 메타 문자는 받지 않는다. */
        private val SEGMENT = Regex("^[\\p{L}\\p{N}._-]+$")

        /**
         * `git-upload-pack '/소유자/저장소.git'` 같은 명령 줄을 읽는다. 경로는 작은따옴표나 큰따옴표로 싸거나 싸지 않아도 되고, 앞의 슬래시, `.git` 접미어, 끝 슬래시는 있어도 없어도 된다.
         * 지원하지 않는 명령이거나 경로가 두 단계(소유자, 저장소)가 아니거나 `..`이나 셸 메타 문자가 있으면 `null`이다.
         */
        fun parse(command: String): SshRepositoryRequest? {
            val line = command.trim()
            val (name, write) = SERVICES.entries.firstOrNull { line.startsWith(it.key + " ") }?.toPair() ?: return null
            val raw = line.substring(name.length).trim()
            val path = when {
                raw.length >= 2 && raw.first() == '\'' && raw.last() == '\'' -> raw.substring(1, raw.length - 1)
                raw.length >= 2 && raw.first() == '"' && raw.last() == '"' -> raw.substring(1, raw.length - 1)
                else -> raw
            }.trim('/').removeSuffix(".git").trimEnd('/')
            val parts = path.split('/')
            if (parts.size != 2 || parts.any { it.isEmpty() || it == "." || it == ".." || !SEGMENT.matches(it) }) return null
            return SshRepositoryRequest(RepositoryService.VCS_GIT, parts[0], parts[1], write)
        }
    }
}
