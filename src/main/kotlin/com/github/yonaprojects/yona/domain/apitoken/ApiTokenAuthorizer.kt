package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import java.time.Instant

// yona-wiki P3-02 Step2 — "ApiToken + 대상 ResourceType + 요청 저장소 + 필요 권한" 조합의 허용 여부를
// 판정하는 순수 함수. Spring 빈으로 만들지 않은 이유는 DB/HTTP 등 어떤 인프라도 필요 없는 순수 판정
// 로직이라(ApiTokenAuthenticationFilterSpec처럼 단위 테스트로만 충분히 검증 가능) 굳이 서비스로
// 감싸 DI 오버헤드를 지울 이유가 없어서다 — 필요해지면(예: Step4+ 컨트롤러에서 재사용) 언제든
// 얇은 @Service 래퍼를 얹을 수 있다.
object ApiTokenAuthorizer {

    // resourceType이 null이면 "metadata" 스코프(GitHub의 "Metadata: Read-only" 자동 부여와 동일한
    // 개념 — 어떤 그룹/권한도 없이 그 저장소가 스코프에 포함되기만 하면 통과)로 취급한다. 이 경우
    // 그룹/권한 매트릭스는 아예 보지 않고 만료 여부 + repo scope 일치 여부만 확인한다(P3-02 Step6.5,
    // "metadata 스코프 세그먼트 설계" 참고).
    fun isAuthorized(
        token: ApiToken,
        resourceType: ResourceType?,
        project: Project?,
        requiredPermission: ApiTokenPermission,
        now: Instant = Instant.now()
    ): Boolean {
        if (requiredPermission == ApiTokenPermission.NONE) return true

        val expiresAt = token.expiresAt ?: return false
        if (!expiresAt.isAfter(now)) return false

        if (resourceType != null) {
            val group = resourceType.toApiTokenScopeGroup() ?: return false
            val grantedPermission = token.scopes.find { it.scopeGroup == group }?.permission ?: ApiTokenPermission.NONE
            if (grantedPermission.ordinal < requiredPermission.ordinal) return false
        }

        return isProjectInRepoScope(token, project)
    }

    private fun isProjectInRepoScope(token: ApiToken, project: Project?): Boolean {
        if (project != null && !token.allRepositories) {
            val projectId = project.id ?: return false
            if (token.scopedProjects.none { it.id == projectId }) return false
        }
        return true
    }
}
