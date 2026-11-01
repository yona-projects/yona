package dev.yona.ssh

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증한 SSH 접속이다. 접속한 키가 사용자 키이면 [userId], 배포 키이면 [deployKeyId]가 있다(둘 중 하나). 엔티티가 아니라 번호만 들고 있다가
 * 명령을 실행할 때마다 다시 읽으므로, 접속 도중 사용자가 잠기거나 키가 지워지면 그 뒤의 명령은 인가되지 않는다.
 */
data class SshPrincipal(val fingerprint: String, val userId: Long?, val deployKeyId: Long?)

/** 인가 결과이다. */
sealed interface SshAuthorization {

    /** 허용한다. [sender]는 푸시한 사용자이며 배포 키로 접속했으면 `null`이다. */
    data class Allowed(val project: Project, val sender: User?) : SshAuthorization

    /** 저장소를 읽을 수 없거나 없다. 있는지 없는지 알려 주지 않으려고 둘을 구분하지 않는다. */
    data object NotFound : SshAuthorization

    /** 저장소는 읽을 수 있지만 요청한 일(쓰기)은 할 수 없다. [message]를 클라이언트에 보인다. */
    data class Denied(val message: String) : SshAuthorization
}

/**
 * SSH 접속의 인증과 인가이다(추가 개발 항목 X-3, `decisions.md` D-66). 저장소 종류와 무관하게 공통이다. 인증은 접속한 공개키의 지문으로 사용자 키([SshKeyService])나
 * 배포 키([DeployKeyService])를 찾는 일이고, 잠기거나 삭제된 사용자의 키는 인증하지 못한다. 인가는 HTTP와 같은 접근 규칙([AccessControl])으로 판정한다.
 * 배포 키는 자기 저장소에만 접근하며 읽기 전용이면 쓸 수 없다.
 */
@Service
class SshAccess(
    private val sshKeys: SshKeyService,
    private val deployKeys: DeployKeyService,
    private val users: UserRepository,
    private val projects: ProjectService,
    private val accessControl: AccessControl
) {

    /** 지문으로 접속한 키를 인증한다. 등록되지 않았거나 주인이 잠기거나 삭제되었으면 `null`이다. */
    @Transactional(readOnly = true)
    fun authenticate(fingerprint: String): SshPrincipal? {
        sshKeys.findByFingerprint(fingerprint)?.let { key ->
            val user = key.user ?: return null
            return if (usable(user)) SshPrincipal(fingerprint, user.id, null) else null
        }
        return deployKeys.findByFingerprint(fingerprint)?.let { SshPrincipal(fingerprint, null, it.id) }
    }

    /** 인증한 접속이 [request]의 저장소에서 읽거나 쓸 수 있는지 판정한다. 접속한 뒤에 바뀐 상태(잠김, 키 삭제)도 반영한다. */
    @Transactional(readOnly = true)
    fun authorize(principal: SshPrincipal, request: SshRepositoryRequest): SshAuthorization {
        val project = projects.findByOwnerAndProjectName(request.owner, request.name)?.takeIf { it.vcs.equals(request.vcs, ignoreCase = true) } ?: return SshAuthorization.NotFound
        principal.deployKeyId?.let { return authorizeDeployKey(it, project, request) }
        val user = principal.userId?.let { users.findById(it).orElse(null) }?.takeIf { usable(it) } ?: return SshAuthorization.NotFound
        if (!allowed(project, user, Operation.READ)) return SshAuthorization.NotFound
        if (request.write && !allowed(project, user, Operation.UPDATE)) return SshAuthorization.Denied("ERROR: Permission to ${request.owner}/${request.name} denied to ${user.loginId}.")
        return SshAuthorization.Allowed(project, user)
    }

    private fun authorizeDeployKey(id: Long, project: Project, request: SshRepositoryRequest): SshAuthorization {
        val key = deployKeys.find(project, id) ?: return SshAuthorization.NotFound
        if (request.write && key.readOnly != false) return SshAuthorization.Denied("ERROR: The deploy key for ${request.owner}/${request.name} is read-only.")
        return SshAuthorization.Allowed(project, null)
    }

    private fun usable(user: User) = user.state != UserState.LOCKED && user.state != UserState.DELETED

    /**
     * 읽기는 읽기 권한, 쓰기는 수정 권한이다. 코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 수정 권한을 요구한다.
     * [dev.yona.web.git.GitHttpController]와 같은 판정이다.
     */
    private fun allowed(project: Project, user: User, operation: Operation): Boolean {
        var needed = operation
        if (project.isCodeAccessibleMemberOnly == true && !projects.hasMember(project, user)) needed = Operation.UPDATE
        return accessControl.isAllowed(user, dev.yona.access.ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), needed)
    }
}
