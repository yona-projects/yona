package dev.yona.domain.sshkey

import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 사용자 SSH 키 등록 결과이다. */
sealed interface SshKeyAddition {
    /** 등록했다. */
    data class Added(val key: SshKey) : SshKeyAddition

    /** 거절했다. [code]는 사용자에게 보일 메시지 키(`sshKey.error.*`)이다. */
    data class Rejected(val code: String) : SshKeyAddition
}

/**
 * 사용자 SSH 공개키 등록 서비스이다(추가 개발 항목 X-3, `decisions.md` D-66). 읽기와 정책은 [SshKeyParser]가, 저장과 소유 규칙은 여기서 맡는다.
 * 키는 사용자가 추가하고 삭제만 할 수 있다(편집 없음, D-65 8번의 키 설정 구성).
 */
@Service
class SshKeyService(private val keys: SshKeyRepository, private val deployKeys: DeployKeyRepository, private val audit: AuditRecorder) {

    companion object {
        /** 사용자당 키 최대 개수이다. */
        const val MAX_KEYS = 50

        const val NAME_MAX = 100
    }

    /**
     * 공개키를 등록한다. 읽을 수 없거나 정책에 맞지 않으면, 이름이 100자를 넘으면, 사용자당 한도를 넘으면, 지문이 이미(사용자 키나 배포 키로) 등록되어 있으면 거절한다.
     *
     * @param name 이름. 비우면 키의 주석(없으면 지문)으로 정한다
     */
    @Transactional
    fun add(user: User, name: String?, text: String, now: Date = Date()): SshKeyAddition {
        val given = name?.trim().orEmpty()
        if (given.length > NAME_MAX) return SshKeyAddition.Rejected("sshKey.error.name")
        val parsed = try {
            SshKeyParser.parse(text)
        } catch (e: SshKeyException) {
            return SshKeyAddition.Rejected("sshKey.error.${e.code}")
        }
        val userId = requireNotNull(user.id)
        if (keys.countByUserId(userId) >= MAX_KEYS) return SshKeyAddition.Rejected("sshKey.error.limit")
        if (keys.existsByFingerprint(parsed.fingerprint) || deployKeys.existsByFingerprint(parsed.fingerprint)) return SshKeyAddition.Rejected("sshKey.error.duplicate")
        val saved = keys.save(
            SshKey(user = user, name = given.ifEmpty { parsed.comment.ifEmpty { parsed.fingerprint } }.take(NAME_MAX), fingerprint = parsed.fingerprint, keyType = parsed.type, publicKey = parsed.publicKey, createdDate = now)
        )
        audit.record(event("ssh_key.added", saved))
        return SshKeyAddition.Added(saved)
    }

    /** 사용자의 키를 최근에 등록한 순서로 읽는다. */
    @Transactional(readOnly = true)
    fun list(user: User): List<SshKey> = keys.findByUserIdOrderByCreatedDateDescIdDesc(requireNotNull(user.id))

    /** 사용자 자신의 키 하나를 읽는다. 없거나 다른 사용자의 것이면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(user: User, id: Long): SshKey? = keys.findById(id).orElse(null)?.takeIf { it.user?.id == user.id }

    /** 키를 지운다. 없거나 다른 사용자의 키이면 지우지 않고 `false`이다. */
    @Transactional
    fun delete(user: User, id: Long): Boolean {
        val key = find(user, id) ?: return false
        keys.delete(key)
        audit.record(event("ssh_key.deleted", key))
        return true
    }

    /** 지문으로 등록된 사용자 키를 찾는다. SSH 접속의 인증에서 키 주인을 알아낼 때 쓴다. */
    @Transactional(readOnly = true)
    fun findByFingerprint(fingerprint: String): SshKey? = keys.findByFingerprint(fingerprint)

    /** 키 이벤트이다. 지문과 종류만 남기고 공개키 본문은 넣지 않는다. */
    private fun event(action: String, key: SshKey) = AuditEvent(
        action, targetType = "ssh_key", targetId = key.id?.toString(), targetLabel = key.name, details = linkedMapOf<String, Any?>("fingerprint" to key.fingerprint, "keyType" to key.keyType)
    )
}

/** 배포 키 등록 결과이다. */
sealed interface DeployKeyAddition {
    /** 등록했다. */
    data class Added(val key: DeployKey) : DeployKeyAddition

    /** 거절했다. [code]는 사용자에게 보일 메시지 키(`deployKey.error.*`)이다. */
    data class Rejected(val code: String) : DeployKeyAddition
}

/**
 * 배포 키 등록 서비스이다(추가 개발 항목 X-3, `decisions.md` D-66). 배포 키는 저장소 하나에만 접근하는 SSH 공개키이며, 기본은 읽기 전용이다.
 * 사용자 키와 같은 지문을 쓸 수 없다. 키를 등록하고 지울 수 있는 사람(프로젝트 관리자)의 판정은 호출하는 쪽에서 한다.
 */
@Service
class DeployKeyService(private val keys: DeployKeyRepository, private val sshKeys: SshKeyRepository, private val audit: AuditRecorder) {

    companion object {
        /** 저장소당 키 최대 개수이다. */
        const val MAX_KEYS = 50

        const val NAME_MAX = 100
    }

    /**
     * 배포 키를 등록한다. 거절 조건은 [SshKeyService.add]와 같고 한도는 저장소 단위이다.
     *
     * @param writable 쓰기(푸시)를 허용하면 `true`이다. 기본은 읽기 전용이다
     * @param createdBy 키를 등록한 사용자. 이 키로 푸시한 뒤의 작업이 이 사용자에게 귀속된다(D-68)
     */
    @Transactional
    fun add(project: Project, name: String?, text: String, writable: Boolean, now: Date = Date(), createdBy: User? = null): DeployKeyAddition {
        val given = name?.trim().orEmpty()
        if (given.length > NAME_MAX) return DeployKeyAddition.Rejected("deployKey.error.name")
        val parsed = try {
            SshKeyParser.parse(text)
        } catch (e: SshKeyException) {
            return DeployKeyAddition.Rejected("deployKey.error.${e.code}")
        }
        val projectId = requireNotNull(project.id)
        if (keys.countByProjectId(projectId) >= MAX_KEYS) return DeployKeyAddition.Rejected("deployKey.error.limit")
        if (keys.existsByFingerprint(parsed.fingerprint) || sshKeys.existsByFingerprint(parsed.fingerprint)) return DeployKeyAddition.Rejected("deployKey.error.duplicate")
        val saved = keys.save(
            DeployKey(
                project = project, name = given.ifEmpty { parsed.comment.ifEmpty { parsed.fingerprint } }.take(NAME_MAX), fingerprint = parsed.fingerprint, keyType = parsed.type,
                publicKey = parsed.publicKey, readOnly = !writable, createdById = createdBy?.id, createdDate = now
            )
        )
        audit.record(event("deploy_key.added", saved))
        return DeployKeyAddition.Added(saved)
    }

    /** 저장소의 배포 키를 최근에 등록한 순서로 읽는다. */
    @Transactional(readOnly = true)
    fun list(project: Project): List<DeployKey> = keys.findByProjectIdOrderByCreatedDateDescIdDesc(requireNotNull(project.id))

    /** 저장소의 배포 키 하나를 읽는다. 없거나 다른 저장소의 것이면 `null`이다. */
    @Transactional(readOnly = true)
    fun find(project: Project, id: Long): DeployKey? = keys.findById(id).orElse(null)?.takeIf { it.project?.id == project.id }

    /** 키를 지운다. 없거나 다른 저장소의 키이면 지우지 않고 `false`이다. */
    @Transactional
    fun delete(project: Project, id: Long): Boolean {
        val key = find(project, id) ?: return false
        keys.delete(key)
        audit.record(event("deploy_key.deleted", key))
        return true
    }

    /** 지문으로 등록된 배포 키를 찾는다. SSH 접속의 인증에서 저장소를 알아낼 때 쓴다. */
    @Transactional(readOnly = true)
    fun findByFingerprint(fingerprint: String): DeployKey? = keys.findByFingerprint(fingerprint)

    /** 배포 키의 이름이다. 감사 기록의 행위자 표시에 쓴다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun findName(id: Long): String? = keys.findById(id).orElse(null)?.name

    /** 키 이벤트이다. 지문, 종류, 쓰기 허용 여부, 저장소만 남기고 공개키 본문은 넣지 않는다. */
    private fun event(action: String, key: DeployKey) = AuditEvent(
        action, targetType = "deploy_key", targetId = key.id?.toString(), targetLabel = key.name, organizationId = key.project?.organization?.id, projectId = key.project?.id,
        details = linkedMapOf<String, Any?>("fingerprint" to key.fingerprint, "keyType" to key.keyType, "readOnly" to key.readOnly, "project" to "${key.project?.owner}/${key.project?.name}")
    )
}
