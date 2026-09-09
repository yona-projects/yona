package com.github.yonaprojects.yona.domain.deploykey

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.sshkey.SshKeyRepository
import com.github.yonaprojects.yona.domain.sshkey.SshPublicKeyFingerprint
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class DeployKeyServiceImpl(
    private val deployKeyRepository: DeployKeyRepository,
    // SshKey(사용자 전역 키)가 있어 이제 두 테이블을 모두 검사해야
    // "이미 등록된 공개키" 전역 유일성(계정/저장소 사칭 방지)이 성립한다.
    private val sshKeyRepository: SshKeyRepository
) : DeployKeyService {

    @Transactional(readOnly = true)
    override fun listByProject(projectId: Long): List<DeployKey> = deployKeyRepository.findByProjectId(projectId)

    @Transactional
    override fun create(project: Project, title: String, rawPublicKey: String, readOnly: Boolean): IssuedDeployKey {
        require(title.isNotBlank()) { "이름은 필수입니다." }

        val parsed = SshPublicKeyFingerprint.parse(rawPublicKey)

        // 보안 검토 반영 — 이미 등록된 공개키(다른 프로젝트의 Deploy Key든, 이 계정 저 계정의
        // SshKey든)를 재등록하면 "내가 이 키의 소유자다"를 사칭할 수 있으므로 전역적으로 거부한다.
        if (deployKeyRepository.findByFingerprint(parsed.fingerprint).isPresent) {
            throw IllegalArgumentException("이미 등록된 공개키입니다.")
        }
        if (sshKeyRepository.findByFingerprint(parsed.fingerprint).isPresent) {
            throw IllegalArgumentException("이미 등록된 공개키입니다.")
        }

        val rawHttpsToken = generateRawHttpsToken()
        val deployKey = DeployKey(
            project = project,
            title = title.trim(),
            publicKey = rawPublicKey.trim(),
            fingerprint = parsed.fingerprint,
            httpsTokenHash = hashDeployKeyToken(rawHttpsToken),
            readOnly = readOnly,
            createdAt = Instant.now()
        )
        val saved = deployKeyRepository.save(deployKey)
        return IssuedDeployKey(saved, rawHttpsToken)
    }

    @Transactional
    override fun delete(project: Project, deployKeyId: Long) {
        val deployKey = deployKeyRepository.findById(deployKeyId).orElse(null) ?: return
        if (deployKey.project?.id != project.id) return
        deployKeyRepository.delete(deployKey)
    }

    @Transactional(readOnly = true)
    override fun findByHttpsToken(rawHttpsToken: String): DeployKey? =
        deployKeyRepository.findByHttpsTokenHash(hashDeployKeyToken(rawHttpsToken)).orElse(null)

    @Transactional
    override fun markUsed(deployKey: DeployKey) {
        deployKey.lastUsedAt = Instant.now()
        deployKeyRepository.save(deployKey)
    }

    override fun isAuthorizedForProject(deployKey: DeployKey, projectId: Long): Boolean {
        return deployKey.project?.id == projectId
    }

    private fun generateRawHttpsToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val TOKEN_PREFIX = "yona_dk_"
    }
}
