package dev.yona.domain.project

import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.State
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestReview
import dev.yona.domain.pullrequest.acceptable
import dev.yona.domain.pullrequest.requiredReviewerCount
import dev.yona.domain.pullrequest.reviewBlocked
import dev.yona.domain.pullrequest.reviewed
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 브랜치 보호 규칙의 브랜치 맞춤과, 규칙이 풀 리퀘스트의 병합 조건에 미치는 영향을 검증한다(로드맵 X-32, `decisions.md` D-42).
 */
class BranchProtectionRuleTest {

    private fun project(vararg rules: Triple<String, Int, Boolean>) = Project().also { p ->
        rules.forEach { (pattern, approvals, admins) -> p.branchProtectionRules.add(BranchProtectionRule(project = p, pattern = pattern, requiredApprovals = approvals, enforceAdmins = admins)) }
    }

    private fun rule(pattern: String) = BranchProtectionRule(pattern = pattern)

    // --- 브랜치 맞춤 ---

    @Test
    fun `정확한 이름은 refs heads 접두사를 떼고 비교한다`() {
        assertTrue(rule("master").matches("refs/heads/master")); assertTrue(rule("master").matches("master"))
        assertFalse(rule("master").matches("refs/heads/master2")); assertFalse(rule("master").matches(null))
    }

    @Test
    fun `별표는 슬래시를 넘지 않고 이중 별표는 넘는다`() {
        assertTrue(rule("release/*").matches("refs/heads/release/1.0"))
        assertFalse(rule("release/*").matches("refs/heads/release/1.0/hotfix"))
        assertTrue(rule("release/**").matches("refs/heads/release/1.0/hotfix"))
        assertTrue(rule("*").matches("refs/heads/master")); assertFalse(rule("*").matches("refs/heads/feature/x"))
        assertTrue(rule("v1.*").matches("refs/heads/v1.2")); assertFalse(rule("v1.*").matches("refs/heads/v1x2"), "점은 글자 그대로이다")
    }

    @Test
    fun `이름이 정확히 같은 규칙이 패턴 규칙보다 우선하고 패턴끼리는 긴 것이 우선한다`() {
        val p = project(Triple("*", 1, false), Triple("release/*", 2, false), Triple("release/1.0", 3, false))
        assertEquals(3, p.protectionFor("refs/heads/release/1.0")!!.requiredApprovals)
        assertEquals(2, p.protectionFor("refs/heads/release/2.0")!!.requiredApprovals)
        assertEquals(1, p.protectionFor("refs/heads/master")!!.requiredApprovals)
        assertNull(project().protectionFor("refs/heads/master"))
        assertNull(project(Triple("release/*", 1, false)).protectionFor("refs/heads/master"))
    }

    // --- 병합 조건 ---

    private fun pullRequest(p: Project, approvals: Int = 0, changesRequested: Boolean = false): PullRequest {
        val author = User(id = 1)
        val pr = PullRequest(toProject = p, toBranch = "refs/heads/master", contributor = author, state = State.OPEN, isConflict = false, isMerging = false, number = 1)
        repeat(approvals) { pr.reviewers.add(User(id = 10L + it)) }
        if (changesRequested) pr.reviewHistory.add(PullRequestReview(authorId = 99, authorLoginId = "x", verdict = ReviewVerdict.CHANGES_REQUESTED, createdDate = Date(), id = 5))
        return pr
    }

    @Test
    fun `규칙이 없는 브랜치는 승인이 없어도 변경 요청이 남아 있어도 막지 않는다`() {
        val pr = pullRequest(project(), approvals = 0, changesRequested = true)
        assertEquals(0, pr.requiredReviewerCount); assertTrue(pr.reviewed); assertFalse(pr.reviewBlocked); assertTrue(pr.acceptable)
    }

    @Test
    fun `규칙이 있으면 승인이 모자랄 때 막고 채우면 연다`() {
        val p = project(Triple("master", 2, false))
        assertTrue(pullRequest(p, approvals = 1).reviewBlocked); assertEquals(2, pullRequest(p).requiredReviewerCount)
        assertFalse(pullRequest(p, approvals = 2).reviewBlocked); assertTrue(pullRequest(p, approvals = 2).acceptable)
    }

    @Test
    fun `규칙이 있으면 승인을 채워도 변경 요청이 남아 있으면 막는다`() {
        assertTrue(pullRequest(project(Triple("master", 1, false)), approvals = 1, changesRequested = true).reviewBlocked)
    }

    @Test
    fun `다른 브랜치의 규칙은 영향이 없다`() {
        assertFalse(pullRequest(project(Triple("release/*", 3, false)), approvals = 0).reviewBlocked)
    }

    // --- 프로젝트 전체 기본 규칙 (D-43) ---

    private fun withDefault(using: Boolean?, count: Int?, vararg rules: Triple<String, Int, Boolean>) =
        project(*rules).also { it.isUsingReviewerCount = using; it.defaultReviewerCount = count }

    @Test
    fun `규칙이 없고 프로젝트가 리뷰어를 쓰면 설정한 수가 모든 브랜치의 기본 규칙이 된다`() {
        val p = withDefault(true, 2)
        val rule = p.protectionFor("refs/heads/master")!!
        assertEquals(2, rule.requiredApprovals); assertFalse(rule.enforceAdmins == true, "기본 규칙은 관리자가 우회할 수 있다"); assertTrue(rule.isProjectDefault)
        assertEquals(2, p.protectionFor("refs/heads/feature/x")!!.requiredApprovals)
        assertTrue(pullRequest(p, approvals = 1).reviewBlocked); assertFalse(pullRequest(p, approvals = 2).reviewBlocked)
    }

    @Test
    fun `프로젝트가 리뷰어를 쓰지 않으면 규칙이 없는 브랜치는 막지 않는다`() {
        assertNull(withDefault(false, 3).protectionFor("refs/heads/master")); assertNull(withDefault(null, 3).protectionFor("refs/heads/master"))
        assertFalse(pullRequest(withDefault(false, 3), approvals = 0).reviewBlocked)
    }

    @Test
    fun `브랜치 보호 규칙이 있으면 프로젝트 기본 규칙보다 우선한다`() {
        val p = withDefault(true, 3, Triple("master", 1, true))
        val rule = p.protectionFor("refs/heads/master")!!
        assertEquals(1, rule.requiredApprovals); assertTrue(rule.enforceAdmins == true); assertFalse(rule.isProjectDefault)
        assertEquals(3, p.protectionFor("refs/heads/develop")!!.requiredApprovals, "규칙이 없는 브랜치는 기본 규칙을 따른다")
    }

    @Test
    fun `기본 규칙의 승인 수가 비었거나 1보다 작으면 1로 본다`() {
        assertEquals(1, withDefault(true, null).protectionFor("master")!!.requiredApprovals); assertEquals(1, withDefault(true, 0).protectionFor("master")!!.requiredApprovals)
    }
}
