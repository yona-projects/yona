package dev.yona.ssh

import dev.yona.domain.lfs.LfsTokens
import dev.yona.repository.RepositoryService
import dev.yona.util.SiteUrls
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * SSH의 `git-lfs-authenticate <경로> <download|upload>` 명령에 답한다(추가 개발 항목 X-8 단계 4, `decisions.md` D-79 8번). git-lfs 클라이언트는 SSH 주소의 저장소에서
 * 이 명령으로 HTTP 주소와 인증 헤더를 받아 **객체 전송은 HTTP로** 한다. 서버는 짧은 수명의 서명 토큰([LfsTokens])을 `Authorization: Bearer …`로 알려 준다.
 * 인가([SshAccess])는 복제와 푸시처럼 그대로 적용된다: 내려받기는 읽기, 올리기는 쓰기 권한이다.
 */
@Component
class LfsSshHandler(private val tokens: LfsTokens, private val siteUrls: SiteUrls) : SshVcsHandler {

    private val mapper = JsonMapper.builder().build()

    override fun parse(command: String): SshRepositoryRequest? {
        val line = command.trim()
        if (!line.startsWith("git-lfs-authenticate ")) return null
        val rest = line.removePrefix("git-lfs-authenticate ").trim()
        val operation = OPERATIONS.firstOrNull { rest.endsWith(" $it") } ?: return null
        val raw = rest.removeSuffix(operation).trim()
        val (owner, name) = GitSshHandler.parsePath(raw) ?: return null
        return SshRepositoryRequest(RepositoryService.VCS_GIT, owner, name, write = operation == "upload", service = "lfs_authenticate")
    }

    override fun execute(grant: SshAuthorization.Allowed, request: SshRepositoryRequest, command: String, streams: SshStreams): Int {
        val user = grant.sender ?: grant.pusher
        if (user == null) {
            streams.error.write("ERROR: git-lfs-authenticate needs a user key.\n".toByteArray()); streams.error.flush()
            return 1
        }
        val token = tokens.issue(user, grant.project, request.write)
        val href = "${siteUrls.origin()}${siteUrls.path("/${grant.project.owner}/${grant.project.name}.git/info/lfs")}"
        val body = linkedMapOf("header" to mapOf("Authorization" to "Bearer $token"), "href" to href, "expires_in" to LfsTokens.TTL_SECONDS)
        streams.output.write(mapper.writeValueAsBytes(body)); streams.output.write('\n'.code)
        streams.output.flush()
        return 0
    }

    private companion object {
        val OPERATIONS = listOf("download", "upload")
    }
}
