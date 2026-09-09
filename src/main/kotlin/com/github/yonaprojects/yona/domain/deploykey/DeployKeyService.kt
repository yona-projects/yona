package com.github.yonaprojects.yona.domain.deploykey

import com.github.yonaprojects.yona.domain.project.Project

data class IssuedDeployKey(
    val deployKey: DeployKey,
    val rawHttpsToken: String
)

interface DeployKeyService {
    fun listByProject(projectId: Long): List<DeployKey>

    // 실패 시 IllegalArgumentException(사용자 입력 오류, 폼에 표시) 또는
    // SshPublicKeyFingerprint.InvalidPublicKeyException을 던진다.
    fun create(project: Project, title: String, rawPublicKey: String, readOnly: Boolean): IssuedDeployKey

    // project와 무관한 id로 삭제를 시도해도(다른 프로젝트의 deploy key id를 추측) 아무 일도
    // 일어나지 않아야 한다 — project 소유 확인 후에만 삭제.
    fun delete(project: Project, deployKeyId: Long)

    // HTTPS Basic 인증(DeployKeyAuthenticationProvider)이 쓰는 조회.
    fun findByHttpsToken(rawHttpsToken: String): DeployKey?

    fun markUsed(deployKey: DeployKey)

    // repository_id 스코프 밖 프로젝트 접근을 거부하기 위한 순수 판정 로직.
    fun isAuthorizedForProject(deployKey: DeployKey, projectId: Long): Boolean
}
