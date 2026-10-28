package dev.yona.domain.pullrequest

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 풀 리퀘스트 병합 때 자동으로 닫을 이슈 번호를 글에서 뽑는 규칙을 검증한다 (로드맵 X-11, v1.6에 없는 추가 항목).
 * `close`, `fix`, `resolve`와 그 활용형(`closes`, `closed`, `fixes`, `fixed`, `resolves`, `resolved`) 뒤에 공백과 `#번호`가 붙은 것만 센다.
 */
class PullRequestIssueReferenceTest {

    @Test
    fun `닫는 말과 활용형 뒤의 번호를 뽑는다`() {
        val text = "close #1, Closes #2 closed #3\nfix #4 FIXES #5 fixed #6 resolve #7 Resolves #8 resolved #9"
        assertEquals((1L..9L).toSet(), PullRequestIssueReference.closingNumbers(listOf(text)))
    }

    @Test
    fun `여러 글에서 모으고 같은 번호는 한 번만 센다`() {
        assertEquals(setOf(3L, 4L), PullRequestIssueReference.closingNumbers(listOf("fixes #3", "closes #3 and resolves #4", null, "")))
    }

    @Test
    fun `그냥 언급했거나 말이 다르면 세지 않는다`() {
        val text = "see #1, related to #2, closing #3, fixing #4, unfixed #5, disclose #6, fixes#7, close #abc, fixes # 8, refs close"
        assertEquals(emptySet(), PullRequestIssueReference.closingNumbers(listOf(text)))
    }
}
