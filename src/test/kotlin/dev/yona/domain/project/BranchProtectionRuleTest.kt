package dev.yona.domain.project

import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.State
import dev.yona.domain.pullrequest.MergeMethod
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestReview
import dev.yona.domain.pullrequest.acceptable
import dev.yona.domain.pullrequest.allowedMergeMethods
import dev.yona.domain.pullrequest.defaultMergeMethodOrFirst
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

    private fun projectOf(vararg rules: BranchProtectionRule) = Project().also { p -> rules.forEach { it.project = p; p.branchProtectionRules.add(it) } }

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

    // --- 병합 조건 (D-45) ---

    private fun pullRequest(p: Project, approvals: Int = 0, changesRequested: Boolean = false): PullRequest {
        val author = User(id = 1)
        val pr = PullRequest(toProject = p, toBranch = "refs/heads/master", contributor = author, state = State.OPEN, isConflict = false, isMerging = false, number = 1)
        repeat(approvals) { pr.reviewers.add(User(id = 10L + it)) }
        if (changesRequested) pr.reviewHistory.add(PullRequestReview(authorId = 99, authorLoginId = "x", verdict = ReviewVerdict.CHANGES_REQUESTED, createdDate = Date(), id = 5))
        return pr
    }

    @Test
    fun `규칙이 없는 브랜치는 승인이 없어도 변경 요청이 남아 있어도 열린 대화가 있어도 막지 않는다`() {
        val pr = pullRequest(project(), approvals = 0, changesRequested = true)
        assertEquals(0, pr.requiredReviewerCount); assertTrue(pr.reviewed); assertFalse(pr.reviewBlocked(openConversations = 3)); assertTrue(pr.acceptable(3))
    }

    @Test
    fun `승인 수를 요구하는 규칙은 모자랄 때 막고 채우면 연다`() {
        val p = project(Triple("master", 2, false))
        assertTrue(pullRequest(p, approvals = 1).reviewBlocked()); assertEquals(2, pullRequest(p).requiredReviewerCount)
        assertFalse(pullRequest(p, approvals = 2).reviewBlocked()); assertTrue(pullRequest(p, approvals = 2).acceptable())
    }

    @Test
    fun `승인 수가 0이면 승인으로는 막지 않는다`() {
        val p = projectOf(BranchProtectionRule(pattern = "master", requiredApprovals = 0))
        assertFalse(pullRequest(p, approvals = 0).reviewBlocked())
    }

    @Test
    fun `변경 요청은 그 조건을 켠 규칙에서만 병합을 막는다`() {
        assertFalse(pullRequest(projectOf(BranchProtectionRule(pattern = "master", requiredApprovals = 1)), approvals = 1, changesRequested = true).reviewBlocked())
        assertTrue(pullRequest(projectOf(BranchProtectionRule(pattern = "master", requiredApprovals = 1, requireNoChangesRequested = true)), approvals = 1, changesRequested = true).reviewBlocked())
    }

    @Test
    fun `열린 대화는 그 조건을 켠 규칙에서만 병합을 막는다`() {
        val plain = projectOf(BranchProtectionRule(pattern = "master", requiredApprovals = 0))
        assertFalse(pullRequest(plain).reviewBlocked(openConversations = 2))
        val strict = projectOf(BranchProtectionRule(pattern = "master", requiredApprovals = 0, requireConversationResolution = true))
        assertTrue(pullRequest(strict).reviewBlocked(openConversations = 1)); assertFalse(pullRequest(strict).reviewBlocked(openConversations = 0))
    }

    @Test
    fun `다른 브랜치의 규칙은 영향이 없다`() {
        assertFalse(pullRequest(project(Triple("release/*", 3, false)), approvals = 0).reviewBlocked())
    }

    @Test
    fun `리뷰어 설정 컬럼은 더 이상 규칙에 영향을 주지 않는다`() {
        val p = project().also { it.isUsingReviewerCount = true; it.defaultReviewerCount = 3 }
        assertNull(p.protectionFor("refs/heads/master")); assertFalse(pullRequest(p).reviewBlocked())
    }

    // --- 선형 이력 ---

    @Test
    fun `선형 이력을 요구하면 병합 커밋 방식을 허용하지 않는다`() {
        val p = projectOf(BranchProtectionRule(pattern = "master", requireLinearHistory = true)).also { it.allowMergeCommit = true; it.allowSquashMerge = true; it.allowRebaseMerge = true }
        val pr = pullRequest(p)
        assertEquals(setOf(MergeMethod.SQUASH, MergeMethod.REBASE), pr.allowedMergeMethods())
        assertEquals(MergeMethod.SQUASH, pr.defaultMergeMethodOrFirst())
        val loose = pullRequest(projectOf(BranchProtectionRule(pattern = "master")).also { it.allowMergeCommit = true; it.allowSquashMerge = true; it.allowRebaseMerge = false })
        assertEquals(setOf(MergeMethod.MERGE, MergeMethod.SQUASH), loose.allowedMergeMethods())
    }

    @Test
    fun `선형 이력을 요구하는데 허용한 방식이 병합 커밋뿐이면 쓸 방식이 없다`() {
        val p = projectOf(BranchProtectionRule(pattern = "master", requireLinearHistory = true)).also { it.allowMergeCommit = true; it.allowSquashMerge = false; it.allowRebaseMerge = false }
        assertTrue(pullRequest(p).allowedMergeMethods().isEmpty())
    }

    // --- 푸시 검사 (D-45 2단계) ---

    private fun violation(rule: BranchProtectionRule, type: org.eclipse.jgit.transport.ReceiveCommand.Type) = rule.pushViolation(type)

    @Test
    fun `규칙이 있으면 강제 푸시와 삭제는 기본으로 막고 생성과 빨리 감기 푸시는 막지 않는다`() {
        val rule = BranchProtectionRule(pattern = "master")
        assertEquals("force", violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD))
        assertEquals("deletion", violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE))
        assertNull(violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE)); assertNull(violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE))
    }

    @Test
    fun `강제 푸시와 삭제를 허용하면 막지 않는다`() {
        val rule = BranchProtectionRule(pattern = "master", allowForcePushes = true, allowDeletions = true)
        org.eclipse.jgit.transport.ReceiveCommand.Type.entries.forEach { assertNull(violation(rule, it), it.name) }
    }

    @Test
    fun `풀 리퀘스트를 요구하면 직접 갱신을 막지만 생성은 막지 않고 삭제는 삭제 허용을 따른다`() {
        val rule = BranchProtectionRule(pattern = "master", requirePullRequest = true, allowForcePushes = true, allowDeletions = true)
        assertEquals("pull-request", violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE))
        assertEquals("pull-request", violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.UPDATE_NONFASTFORWARD))
        assertNull(violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE)); assertNull(violation(rule, org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE))
    }
}
