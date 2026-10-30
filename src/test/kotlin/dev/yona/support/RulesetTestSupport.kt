package dev.yona.support

import dev.yona.domain.project.Project
import dev.yona.domain.project.Ruleset
import java.util.Date

/**
 * 테스트가 프로젝트의 브랜치에 규칙 묶음을 거는 도우미이다(로드맵 X-34, `decisions.md` D-46). 프로젝트가 들고 있는 규칙 묶음 목록에도 넣어 같은 트랜잭션의 판정이 바로 따른다.
 * 돌려받은 규칙 묶음은 호출한 쪽이 저장한다. 이름은 대상 패턴과 같게 하며, 승인 수와 함께 변경 요청 해결을 기본으로 켜 두고 프로젝트 관리자가 우회하게([bypassAdmins]) 둔다.
 */
fun ruleset(
    project: Project, pattern: String = "master", approvals: Int = 1, bypassAdmins: Boolean = true, noChangesRequested: Boolean = true,
    conversationResolution: Boolean = false, linearHistory: Boolean = false, restrictDeletions: Boolean = true, blockForcePushes: Boolean = true, requirePullRequest: Boolean = false
): Ruleset =
    Ruleset(
        project = project, name = pattern, pattern = pattern, requiredApprovals = approvals, bypassAdmins = bypassAdmins, requireNoChangesRequested = noChangesRequested,
        requireConversationResolution = conversationResolution, requireLinearHistory = linearHistory, restrictDeletions = restrictDeletions, blockForcePushes = blockForcePushes,
        requirePullRequest = requirePullRequest, createdDate = Date()
    ).also { project.rulesets.add(it) }
