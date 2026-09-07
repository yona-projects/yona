package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.config.vcs.RepoAccessPolicy
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant
import java.util.regex.Pattern

@Service
class SshAuthServiceImpl(
    private val sshKeyRepository: SshKeyRepository,
    private val deployKeyRepository: DeployKeyRepository,
    private val deployKeyService: DeployKeyService,
    private val projectRepository: ProjectRepository,
    private val repoAccessPolicy: RepoAccessPolicy,
    // GitServletConfig와 동일한 프로퍼티/기본값 — 두 경로(HTTPS/SSH) 모두 같은 물리 저장소를
    // 가리켜야 한다.
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val baseDir: String
) : SshAuthService {

    // GitServletConfig.gitServletRegistrationBean()의 setReceivePackFactory 리졸버가 쓰는
    // git-upload-pack/git-receive-pack/git-upload-archive + 인자(따옴표로 감싼 경로) 파싱과
    // 동일한 형식. OpenSSH가 SSH_ORIGINAL_COMMAND로 그대로 넘겨주는 형태다.
    private val commandPattern = Pattern.compile(
        "^(git-upload-pack|git-receive-pack|git-upload-archive)\\s+'([^']+)'$"
    )

    @Transactional
    override fun authenticate(rawPublicKeyLine: String): SshAuthPrincipal? {
        val parsed = try {
            SshPublicKeyFingerprint.parse(rawPublicKeyLine)
        } catch (e: SshPublicKeyFingerprint.InvalidPublicKeyException) {
            return null
        }
        return authenticateByFingerprint(parsed.fingerprint)
    }

    @Transactional
    override fun authenticateByFingerprint(fingerprint: String): SshAuthPrincipal? {
        sshKeyRepository.findByFingerprint(fingerprint).orElse(null)?.let { sshKey ->
            val user = sshKey.user ?: return null
            sshKey.lastUsedAt = Instant.now()
            sshKeyRepository.save(sshKey)
            return SshAuthPrincipal.SshKeyPrincipal(user, sshKey)
        }

        deployKeyRepository.findByFingerprint(fingerprint).orElse(null)?.let { deployKey ->
            deployKeyService.markUsed(deployKey)
            return SshAuthPrincipal.DeployKeyPrincipal(deployKey)
        }

        return null
    }

    override fun resolveProject(owner: String, projectName: String): Project? =
        projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)

    override fun encodePrincipal(principal: SshAuthPrincipal): String = when (principal) {
        is SshAuthPrincipal.SshKeyPrincipal -> "sshkey:${principal.sshKey.id}"
        is SshAuthPrincipal.DeployKeyPrincipal -> "deploykey:${principal.deployKey.id}"
    }

    @Transactional(readOnly = true)
    override fun resolvePrincipal(encoded: String): SshAuthPrincipal? {
        val (type, idStr) = encoded.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
        val id = idStr.toLongOrNull() ?: return null
        return when (type) {
            "sshkey" -> {
                val sshKey = sshKeyRepository.findById(id).orElse(null) ?: return null
                val user = sshKey.user ?: return null
                SshAuthPrincipal.SshKeyPrincipal(user, sshKey)
            }
            "deploykey" -> {
                val deployKey = deployKeyRepository.findById(id).orElse(null) ?: return null
                SshAuthPrincipal.DeployKeyPrincipal(deployKey)
            }
            else -> null
        }
    }

    @Transactional(readOnly = true)
    override fun authorizeGitCommand(principal: SshAuthPrincipal, command: String): SshCommandAuthorization {
        val matcher = commandPattern.matcher(command.trim())
        if (!matcher.matches()) {
            return SshCommandAuthorization.denied("지원하지 않는 명령입니다: $command")
        }

        val service = matcher.group(1)
        val repoPath = matcher.group(2).removeSuffix("/")
        val isWrite = service == "git-receive-pack"

        val (owner, projectName) = splitOwnerAndProject(repoPath)
            ?: return SshCommandAuthorization.denied("저장소 경로를 해석할 수 없습니다: $repoPath")

        val project = resolveProject(owner, projectName)
            ?: return SshCommandAuthorization.denied("존재하지 않는 저장소입니다: $owner/$projectName")

        return when (principal) {
            is SshAuthPrincipal.DeployKeyPrincipal -> authorizeForDeployKey(principal.deployKey.let { it }, project, isWrite, service)
            is SshAuthPrincipal.SshKeyPrincipal -> authorizeForUser(principal, project, isWrite, service)
        }
    }

    private fun authorizeForDeployKey(
        deployKey: com.github.yonaprojects.yona.domain.deploykey.DeployKey,
        project: Project,
        isWrite: Boolean,
        service: String
    ): SshCommandAuthorization {
        if (deployKey.project?.id != project.id) {
            return SshCommandAuthorization.denied("이 Deploy Key는 이 저장소에 접근할 수 없습니다.")
        }
        if (isWrite && deployKey.readOnly) {
            return SshCommandAuthorization.denied("읽기 전용 Deploy Key로는 push할 수 없습니다.")
        }
        return SshCommandAuthorization(
            allowed = true, isWrite = isWrite, repoDir = repoDirOf(project), service = service,
            project = project, pusher = null
        )
    }

    private fun authorizeForUser(
        principal: SshAuthPrincipal.SshKeyPrincipal,
        project: Project,
        isWrite: Boolean,
        service: String
    ): SshCommandAuthorization {
        val loginId = principal.user.loginId

        if (repoAccessPolicy.isGuestUser(loginId)) {
            return SshCommandAuthorization.denied("게스트 계정은 git 접근이 허용되지 않습니다.")
        }

        // SSH 세션은 이미 공개키로 인증된 상태이므로(GitAuthorizationFilter의 401 분기에 대응하는
        // "미인증" 케이스 자체가 없음), HTTPS 경로의 requiresAuth=true에 대응하는 멤버십 검사만
        // 그대로 재사용한다 — requiresAuth=false(공개 프로젝트 읽기)이면 게스트가 아닌 이상 통과.
        if (repoAccessPolicy.requiresAuth(project, isWrite) && !repoAccessPolicy.isMember(project, loginId)) {
            return SshCommandAuthorization.denied("이 저장소에 접근할 권한이 없습니다.")
        }

        return SshCommandAuthorization(
            allowed = true, isWrite = isWrite, repoDir = repoDirOf(project), service = service,
            project = project, pusher = principal.user
        )
    }

    // GitServletConfig의 setRepositoryResolver와 동일한 물리 경로 규칙("owner/name.git",
    // .git 접미어 정규화)을 그대로 재사용한다 — 두 프로토콜이 같은 디스크 경로를 가리켜야 한다.
    private fun repoDirOf(project: Project): File {
        val normalizedName = "${project.owner}/${project.name}.git"
        return File(baseDir, normalizedName)
    }

    private fun splitOwnerAndProject(repoPath: String): Pair<String, String>? {
        val trimmed = repoPath.removePrefix("/")
        val segments = trimmed.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val owner = segments[segments.size - 2]
        val projectName = segments.last().removeSuffix(".git")
        return owner to projectName
    }
}
