package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.CommitInfo
import org.eclipse.jgit.lib.ObjectId
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.util.Date
import java.util.Optional
import kotlin.test.assertEquals

/**
 * 병합 검사가 만드는 이벤트와 커밋 행을 검증한다.
 * 보낼 커밋이 없어 병합으로 판정되면 병합 이벤트와 알림을 남기고(C038, R-42-1), 빠졌다 돌아온 커밋은 새 행을 만들지 않고 되살린다(R-42-6, 현재는 설명만 드린 상태).
 */
class PullRequestMergeProcessorEventsTest {

    private val pullRequests = mock(PullRequestRepository::class.java)
    private val commits = mock(PullRequestCommitRepository::class.java)
    private val merger = mock(PullRequestMerger::class.java)
    private val events = mock(PullRequestEventService::class.java)
    private val users = mock(UserRepository::class.java)
    private val publisher = mock(ApplicationEventPublisher::class.java)
    private val processor = PullRequestMergeProcessor(pullRequests, commits, merger, events, users, publisher, mock(PullRequestReviewService::class.java))
    private val sender = User(id = 2, loginId = "sender")

    private fun attempt(list: List<CommitInfo>) = MergeUpdate(MergeAttempt(false, list, ObjectId.zeroId(), ObjectId.zeroId()), null)

    private fun prepare(): PullRequest {
        val pr = PullRequest(id = 1, state = State.OPEN)
        `when`(pullRequests.findById(1L)).thenReturn(Optional.of(pr))
        `when`(users.findById(2L)).thenReturn(Optional.of(sender))
        org.mockito.Mockito.doReturn(pr).`when`(pullRequests).save(pr)
        return pr
    }

    @Test
    fun `보낼 커밋이 없어 병합으로 판정되면 병합 이벤트와 병합 알림을 남긴다 (C038, R-42-1)`() {
        val pr = prepare()
        `when`(merger.update(pr)).thenReturn(attempt(emptyList()))
        processor.process(1, 2, newPullRequest = false)
        assertEquals(State.MERGED, pr.state)
        verify(events).addMerge(sender, EventType.PULL_REQUEST_MERGED, State.MERGED, pr)
        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(publisher).publishEvent(captor.capture())
        val activity = captor.value as PullRequestChangedActivity
        assertEquals(EventType.PULL_REQUEST_STATE_CHANGED, activity.type)
        assertEquals("open", activity.oldValue)
        assertEquals("merged", activity.newValue)
    }

    @Test
    fun `이미 병합된 풀 리퀘스트는 이벤트를 다시 남기지 않는다 (C038)`() {
        val pr = prepare().also { it.state = State.MERGED }
        `when`(merger.update(pr)).thenReturn(attempt(emptyList()))
        processor.process(1, 2, newPullRequest = false)
        verify(events, never()).addMerge(sender, EventType.PULL_REQUEST_MERGED, State.MERGED, pr)
    }

    @Test
    fun `새 커밋이 생기면 갱신 시각이 남는다 (R-42-3)`() {
        val pr = prepare()
        `when`(merger.update(pr)).thenReturn(attempt(listOf(commit("c1"))))
        `when`(commits.findByPullRequestId(1L)).thenReturn(emptyList())
        org.mockito.Mockito.doAnswer { it.arguments[0] }.`when`(commits).save(org.mockito.ArgumentMatchers.any(PullRequestCommit::class.java))
        processor.process(1, 2, newPullRequest = false)
        kotlin.test.assertNotNull(pr.updated)
    }

    @Test
    fun `빠졌다 돌아온 커밋은 새 행을 만들지 않고 PRIOR를 CURRENT로 되돌린다 (R-42-6)`() {
        val pr = prepare()
        val prior = PullRequestCommit(id = 7, pullRequest = pr, commitId = "c1", state = PullRequestCommitState.PRIOR)
        `when`(merger.update(pr)).thenReturn(attempt(listOf(commit("c1"))))
        `when`(commits.findByPullRequestId(1L)).thenReturn(listOf(prior))
        org.mockito.Mockito.doAnswer { it.arguments[0] }.`when`(commits).save(org.mockito.ArgumentMatchers.any(PullRequestCommit::class.java))
        processor.process(1, 2, newPullRequest = false)
        assertEquals(PullRequestCommitState.CURRENT, prior.state)
        verify(commits, never()).save(org.mockito.ArgumentMatchers.argThat { it !== prior && it.commitId == "c1" })
    }

    private fun commit(id: String) = CommitInfo(id, id, "m", "m", "a", "a@x", Date(), "a", "a@x", Date(), 1)
}
