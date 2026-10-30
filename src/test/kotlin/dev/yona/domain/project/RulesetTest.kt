package dev.yona.domain.project

import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.State
import dev.yona.domain.pullrequest.MergeMethod
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestReview
import dev.yona.domain.pullrequest.acceptable
import dev.yona.domain.pullrequest.allowedMergeMethods
import dev.yona.domain.pullrequest.blockingRulesets
import dev.yona.domain.pullrequest.defaultMergeMethodOrFirst
import dev.yona.domain.pullrequest.requiredReviewerCount
import dev.yona.domain.pullrequest.reviewBlocked
import dev.yona.domain.pullrequest.reviewed
import dev.yona.domain.user.User
import org.eclipse.jgit.transport.ReceiveCommand.Type
import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 룰셋의 대상 맞춤, 합산, 병합 조건, 푸시 검사를 검증한다(로드맵 X-34, `decisions.md` D-46).
 */
class RulesetTest {

    private fun rule(pattern: String, approvals: Int = 0, name: String = pattern, configure: Ruleset.() -> Unit = {}) =
        Ruleset(name = name, pattern = pattern, requiredApprovals = approvals).apply(configure)

    private fun projectOf(vararg rules: Ruleset) = Project().also { p -> rules.forEach { it.project = p; p.rulesets.add(it) } }

    // --- 대상 맞춤 ---

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
        assertTrue(rule("**").matches("refs/heads/feature/x/y"))
        assertTrue(rule("v1.*").matches("refs/heads/v1.2")); assertFalse(rule("v1.*").matches("refs/heads/v1x2"), "점은 글자 그대로이다")
    }

    // --- 적용되는 룰셋 ---

    @Test
    fun `대상이 맞는 활성 룰셋을 모두 돌려주고 비활성이나 다른 브랜치의 룰셋은 뺀다`() {
        val p = projectOf(rule("master"), rule("*", name = "all"), rule("release/*"), rule("master", name = "off") { enforcement = Ruleset.DISABLED })
        assertEquals(setOf("master", "all"), p.rulesetsFor("refs/heads/master").map { it.name }.toSet())
        assertEquals(listOf("release/*"), p.rulesetsFor("refs/heads/release/1.0").map { it.name })
        assertTrue(Project().rulesetsFor("refs/heads/master").isEmpty())
    }

    @Test
    fun `적용 상태가 비어 있으면 적용으로 본다`() {
        assertTrue(rule("master") { enforcement = null }.isActive); assertFalse(rule("master") { enforcement = Ruleset.DISABLED }.isActive)
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
    fun `룰셋이 없는 브랜치는 승인이 없어도 변경 요청이 남아 있어도 열린 대화가 있어도 막지 않는다`() {
        val pr = pullRequest(Project(), approvals = 0, changesRequested = true)
        assertEquals(0, pr.requiredReviewerCount); assertTrue(pr.reviewed); assertFalse(pr.reviewBlocked(openConversations = 3)); assertTrue(pr.acceptable(3))
    }

    @Test
    fun `승인 수를 요구하는 룰셋은 모자랄 때 막고 채우면 연다`() {
        val p = projectOf(rule("master", approvals = 2))
        assertTrue(pullRequest(p, approvals = 1).reviewBlocked()); assertEquals(2, pullRequest(p).requiredReviewerCount)
        assertFalse(pullRequest(p, approvals = 2).reviewBlocked()); assertTrue(pullRequest(p, approvals = 2).acceptable())
    }

    @Test
    fun `승인 수가 0이면 승인으로는 막지 않는다`() {
        assertFalse(pullRequest(projectOf(rule("master", approvals = 0))).reviewBlocked())
    }

    @Test
    fun `변경 요청은 그 조건을 켠 룰셋에서만 병합을 막는다`() {
        assertFalse(pullRequest(projectOf(rule("master", approvals = 1)), approvals = 1, changesRequested = true).reviewBlocked())
        assertTrue(pullRequest(projectOf(rule("master", approvals = 1) { requireNoChangesRequested = true }), approvals = 1, changesRequested = true).reviewBlocked())
    }

    @Test
    fun `열린 대화는 그 조건을 켠 룰셋에서만 병합을 막는다`() {
        assertFalse(pullRequest(projectOf(rule("master"))).reviewBlocked(openConversations = 2))
        val strict = projectOf(rule("master") { requireConversationResolution = true })
        assertTrue(pullRequest(strict).reviewBlocked(openConversations = 1)); assertFalse(pullRequest(strict).reviewBlocked(openConversations = 0))
    }

    @Test
    fun `비활성 룰셋과 다른 브랜치의 룰셋은 병합을 막지 않는다`() {
        assertFalse(pullRequest(projectOf(rule("release/*", approvals = 3))).reviewBlocked())
        assertFalse(pullRequest(projectOf(rule("master", approvals = 3) { enforcement = Ruleset.DISABLED })).reviewBlocked())
    }

    @Test
    fun `리뷰어 설정 컬럼은 더 이상 병합 판정에 영향을 주지 않는다`() {
        val p = Project().also { it.isUsingReviewerCount = true; it.defaultReviewerCount = 3 }
        assertTrue(p.rulesetsFor("refs/heads/master").isEmpty()); assertFalse(pullRequest(p).reviewBlocked())
    }

    // --- 합산 ---

    @Test
    fun `룰셋이 겹치면 필요한 승인 수는 가장 큰 값이고 막는 룰셋만 골라낸다`() {
        val p = projectOf(rule("master", approvals = 1, name = "a"), rule("*", approvals = 3, name = "b"), rule("master", approvals = 0, name = "c") { requireConversationResolution = true })
        assertEquals(3, pullRequest(p).requiredReviewerCount)
        assertEquals(listOf("b"), pullRequest(p, approvals = 1).blockingRulesets(openConversations = 0).map { it.name }, "승인 1명이면 3명을 요구하는 룰셋만 막는다")
        assertEquals(setOf("b", "c"), pullRequest(p, approvals = 1).blockingRulesets(openConversations = 1).map { it.name }.toSet())
        assertTrue(pullRequest(p, approvals = 3).blockingRulesets(openConversations = 0).isEmpty())
    }

    // --- 선형 이력 ---

    @Test
    fun `선형 이력을 요구하면 병합 커밋 방식을 허용하지 않는다`() {
        val p = projectOf(rule("master") { requireLinearHistory = true }).also { it.allowMergeCommit = true; it.allowSquashMerge = true; it.allowRebaseMerge = true }
        val pr = pullRequest(p)
        assertEquals(setOf(MergeMethod.SQUASH, MergeMethod.REBASE), pr.allowedMergeMethods())
        assertEquals(MergeMethod.SQUASH, pr.defaultMergeMethodOrFirst())
        val loose = pullRequest(projectOf(rule("master")).also { it.allowMergeCommit = true; it.allowSquashMerge = true; it.allowRebaseMerge = false })
        assertEquals(setOf(MergeMethod.MERGE, MergeMethod.SQUASH), loose.allowedMergeMethods())
    }

    @Test
    fun `겹친 룰셋 중 하나라도 선형 이력을 요구하면 병합 커밋을 뺀다`() {
        val p = projectOf(rule("master"), rule("*", name = "linear") { requireLinearHistory = true }).also { it.allowMergeCommit = true; it.allowSquashMerge = true; it.allowRebaseMerge = false }
        assertEquals(setOf(MergeMethod.SQUASH), pullRequest(p).allowedMergeMethods())
    }

    @Test
    fun `선형 이력을 요구하는데 허용한 방식이 병합 커밋뿐이면 쓸 방식이 없다`() {
        val p = projectOf(rule("master") { requireLinearHistory = true }).also { it.allowMergeCommit = true; it.allowSquashMerge = false; it.allowRebaseMerge = false }
        assertTrue(pullRequest(p).allowedMergeMethods().isEmpty())
    }

    // --- 푸시 검사 ---

    @Test
    fun `기본 룰셋은 삭제와 강제 푸시를 막고 생성과 빨리 감기 푸시는 막지 않는다`() {
        val rule = Ruleset(pattern = "master")
        assertEquals("deletion", rule.pushViolation(Type.DELETE)); assertEquals("force", rule.pushViolation(Type.UPDATE_NONFASTFORWARD))
        assertNull(rule.pushViolation(Type.UPDATE)); assertNull(rule.pushViolation(Type.CREATE))
    }

    @Test
    fun `삭제 제한과 강제 푸시 차단을 끄면 막지 않는다`() {
        val rule = Ruleset(pattern = "master", restrictDeletions = false, blockForcePushes = false)
        Type.entries.forEach { assertNull(rule.pushViolation(it), it.name) }
    }

    @Test
    fun `풀 리퀘스트를 요구하면 직접 갱신을 막지만 생성은 막지 않고 삭제는 삭제 제한을 따른다`() {
        val rule = Ruleset(pattern = "master", requirePullRequest = true, restrictDeletions = false, blockForcePushes = false)
        assertEquals("pull-request", rule.pushViolation(Type.UPDATE)); assertEquals("pull-request", rule.pushViolation(Type.UPDATE_NONFASTFORWARD))
        assertNull(rule.pushViolation(Type.CREATE)); assertNull(rule.pushViolation(Type.DELETE))
    }
}
