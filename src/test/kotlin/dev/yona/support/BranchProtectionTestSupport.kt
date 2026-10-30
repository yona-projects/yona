package dev.yona.support

import dev.yona.domain.project.BranchProtectionRule
import dev.yona.domain.project.Project
import java.util.Date

/**
 * 테스트가 프로젝트의 브랜치에 보호 규칙을 거는 도우미이다(로드맵 X-32, X-33, `decisions.md` D-42, D-45). 승인 수와 함께 변경 요청 해결을 기본으로 켜 둔다. 프로젝트가 들고 있는 규칙 목록에도 넣어 같은 트랜잭션의 판정이 바로 따른다.
 * 돌려받은 규칙은 호출한 쪽이 저장한다.
 */
fun protectionRule(
    project: Project, pattern: String = "master", approvals: Int = 1, enforceAdmins: Boolean = false, noChangesRequested: Boolean = true,
    conversationResolution: Boolean = false, linearHistory: Boolean = false
): BranchProtectionRule =
    BranchProtectionRule(
        project = project, pattern = pattern, requiredApprovals = approvals, enforceAdmins = enforceAdmins, requireNoChangesRequested = noChangesRequested,
        requireConversationResolution = conversationResolution, requireLinearHistory = linearHistory, createdDate = Date()
    ).also { project.branchProtectionRules.add(it) }
