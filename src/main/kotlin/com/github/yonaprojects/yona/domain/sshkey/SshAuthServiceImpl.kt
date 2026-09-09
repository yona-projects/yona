package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.config.vcs.RepoAccessPolicy
import com.github.yonaprojects.yona.domain.deploykey.DeployKey
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
    private val baseDir: String,
    // RepositoryService/HgRepository.kt와 동일한 프로퍼티/기본값.
    @Value("\${yona.hg.base-dir:/tmp/yona/hg}")
    private val hgBaseDir: String
) : SshAuthService {

    // GitServletConfig.gitServletRegistrationBean()의 setReceivePackFactory 리졸버가 쓰는
    // git-upload-pack/git-receive-pack/git-upload-archive + 인자(따옴표로 감싼 경로) 파싱과
    // 동일한 형식. OpenSSH가 SSH_ORIGINAL_COMMAND로 그대로 넘겨주는 형태다.
    private val commandPattern = Pattern.compile(
        "^(git-upload-pack|git-receive-pack|git-upload-archive)\\s+'([^']+)'$"
    )

    // real hg 클라이언트의 ui.ssh 원격 명령 고정 형식(mercurial/sshpeer.py 실측,
    // "hg -R <path> serve --stdio") — 경로에 공백이 없으면 따옴표 없이, 있으면 작은따옴표로
    // 감싸서 보낸다(둘 다 인식). yona 쪽에서 새로 지어낸 규약이 아니라 real hg가 실제로 보내는
    // 그대로다 — 그래야 이 저장소가 나중에 real hg 바이너리를 향한 진짜 forced command로도
    // 그대로 재사용될 수 있다.
    private val hgCommandPattern = Pattern.compile(
        "^hg\\s+-R\\s+'?([^'\\s]+)'?\\s+serve\\s+--stdio\\s*$"
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

    @Transactional(readOnly = true)
    override fun authorizeHgCommand(principal: SshAuthPrincipal, command: String): SshCommandAuthorization {
        val matcher = hgCommandPattern.matcher(command.trim())
        if (!matcher.matches()) {
            return SshCommandAuthorization.denied("지원하지 않는 명령입니다: $command")
        }

        val repoPath = matcher.group(1).removePrefix("/").removeSuffix("/")
        val (owner, projectName) = splitOwnerAndProject(repoPath)
            ?: return SshCommandAuthorization.denied("저장소 경로를 해석할 수 없습니다: $repoPath")

        val project = resolveProject(owner, projectName)
            ?: return SshCommandAuthorization.denied("존재하지 않는 저장소입니다: $owner/$projectName")

        return when (principal) {
            is SshAuthPrincipal.DeployKeyPrincipal -> authorizeHgForDeployKey(principal.deployKey, project)
            is SshAuthPrincipal.SshKeyPrincipal -> authorizeHgForUser(principal, project)
        }
    }

    // git의 authorizeForDeployKey()와 동일한 스코프 검사 — readOnly 여부는 (git과 달리 명령줄
    // 자체로 read/write를 구분할 수 없으므로) 연결 자체를 막지 않고 isWrite에만 반영해, 실제
    // push 시도는 HgSshProtocolHandler의 pre-changegroup 훅에서 거부한다.
    private fun authorizeHgForDeployKey(
        deployKey: DeployKey,
        project: Project
    ): SshCommandAuthorization {
        if (deployKey.project?.id != project.id) {
            return SshCommandAuthorization.denied("이 Deploy Key는 이 저장소에 접근할 수 없습니다.")
        }
        return SshCommandAuthorization(
            allowed = true, isWrite = !deployKey.readOnly, repoDir = hgRepoDirOf(project), service = "hg-serve",
            project = project, pusher = null
        )
    }

    // git의 authorizeForUser()와 동일한 게스트/멤버십 검사. 연결(읽기) 허용 여부는
    // requiresAuth(project, isWriteRequest=false) 기준(공개 저장소는 비멤버도 clone 가능)이지만,
    // isWrite(=push 허용 여부)는 항상 requiresAuth(project, isWriteRequest=true) 기준으로 별도
    // 계산한다 — Hg SSH 세션 하나가 pull/push 모두를 처리할 수 있어 명령줄 시점에는 이 세션이
    // 실제로 push를 시도할지 알 수 없기 때문이다.
    private fun authorizeHgForUser(
        principal: SshAuthPrincipal.SshKeyPrincipal,
        project: Project
    ): SshCommandAuthorization {
        val loginId = principal.user.loginId

        if (repoAccessPolicy.isGuestUser(loginId)) {
            return SshCommandAuthorization.denied("게스트 계정은 hg 접근이 허용되지 않습니다.")
        }

        val isMember = repoAccessPolicy.isMember(project, loginId)
        if (repoAccessPolicy.requiresAuth(project, false) && !isMember) {
            return SshCommandAuthorization.denied("이 저장소에 접근할 권한이 없습니다.")
        }
        val canWrite = !repoAccessPolicy.requiresAuth(project, true) || isMember

        return SshCommandAuthorization(
            allowed = true, isWrite = canWrite, repoDir = hgRepoDirOf(project), service = "hg-serve",
            project = project, pusher = principal.user
        )
    }

    // RepositoryService/HgRepository.kt와 동일한 물리 경로 규칙("owner/name", git과 달리 bare
    // 저장소 접미어 없음).
    private fun hgRepoDirOf(project: Project): File {
        return File(hgBaseDir, "${project.owner}/${project.name}")
    }

    private fun authorizeForDeployKey(
        deployKey: DeployKey,
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
