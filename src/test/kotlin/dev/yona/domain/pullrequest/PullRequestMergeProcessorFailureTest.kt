package dev.yona.domain.pullrequest

import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import kotlin.test.assertEquals

/** 병합 검사 중 예외가 나도 병합 중 표시가 꺼지는지 검증한다(R-42-2). v1.6은 표시를 그대로 두어 화면이 계속 "병합 중"이었다. */
class PullRequestMergeProcessorFailureTest {

    @Test
    fun `검사가 실패하면 병합 중 표시를 끈다`() {
        val pullRequests = mock(PullRequestRepository::class.java)
        val users = mock(UserRepository::class.java)
        val merger = mock(PullRequestMerger::class.java)
        val pr = PullRequest(id = 1, isMerging = true)
        val sender = User(id = 2, loginId = "sender")
        `when`(pullRequests.findById(1L)).thenReturn(Optional.of(pr))
        `when`(users.findById(2L)).thenReturn(Optional.of(sender))
        org.mockito.Mockito.doReturn(pr).`when`(pullRequests).save(pr)
        org.mockito.Mockito.doThrow(IllegalStateException("검사 실패")).`when`(merger).update(pr)
        val processor = PullRequestMergeProcessor(
            pullRequests, mock(PullRequestCommitRepository::class.java), merger, mock(PullRequestEventService::class.java), users,
            mock(ApplicationEventPublisher::class.java), mock(PullRequestReviewService::class.java)
        )
        processor.process(1, 2, newPullRequest = false)
        assertEquals(false, pr.isMerging)
    }
}
