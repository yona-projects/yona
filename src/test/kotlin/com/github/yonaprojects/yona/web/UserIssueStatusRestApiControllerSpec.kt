package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — UserIssueStatusRestApiController(GET
// /api/v1/user/issues/status). `gh issue status`의 최소 버전(담당/작성 이슈 개수·목록)만 검증했었다.
//
// yona-wiki P3-02 Step8.6 항목2(2026-09-01, 우선순위 2위) — UserViewController.userIssues()가
// 지원하는 mentioned/favorite/shared/commenter 필터와 페이지네이션/정렬 파라미터를 전부 노출하도록
// 확장된 것을 검증한다.
class UserIssueStatusRestApiControllerSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val userRepository = mockk<UserRepository>()
    val mentionService = mockk<MentionService>()

    val controller = UserIssueStatusRestApiController(issueRepository, userRepository, mentionService)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(issueRepository, userRepository, mentionService)
    }

    val auth = UsernamePasswordAuthenticationToken("tester", "password")
    val user = User(id = 1L, loginId = "tester", name = "테스터")
    val project = Project(id = 1L, owner = "yona", name = "yona")

    // 6개 섹션(assigned/created/commented/mentioned/favorite/shared) 전부가 항상 계산되므로
    // 개별 케이스에서 필요 없는 섹션도 공통으로 스텁해둔다.
    fun stubAllSectionsEmpty(userId: Long, mentionedFor: Long = userId) {
        val emptyPage = PageImpl<Issue>(emptyList(), PageRequest.of(0, 20), 0)
        every { issueRepository.findByAssigneeAndState(userId, State.OPEN, null, any()) } returns emptyPage
        every { issueRepository.findByAuthorIdAndState(userId, State.OPEN, null, any()) } returns emptyPage
        every { issueRepository.findCommentedByState(userId, State.OPEN, null, any()) } returns emptyPage
        every { issueRepository.findFavoriteByState(userId, State.OPEN, null, any()) } returns emptyPage
        every { issueRepository.findSharedByState(userId, State.OPEN, null, any()) } returns emptyPage
        every { mentionService.getMentioningIssueIds(mentionedFor) } returns emptyList()

        every { issueRepository.countByAssigneeAndState(userId, State.OPEN) } returns 0L
        every { issueRepository.countByAssigneeAndState(userId, State.CLOSED) } returns 0L
        every { issueRepository.countByAuthorIdAndState(userId, State.OPEN) } returns 0L
        every { issueRepository.countByAuthorIdAndState(userId, State.CLOSED) } returns 0L
        every { issueRepository.countCommentedByState(userId, State.OPEN) } returns 0L
        every { issueRepository.countCommentedByState(userId, State.CLOSED) } returns 0L
        every { issueRepository.countFavoriteByState(userId, State.OPEN) } returns 0L
        every { issueRepository.countFavoriteByState(userId, State.CLOSED) } returns 0L
        every { issueRepository.countSharedByState(userId, State.OPEN) } returns 0L
        every { issueRepository.countSharedByState(userId, State.CLOSED) } returns 0L
    }

    describe("GET /api/v1/user/issues/status") {
        it("비로그인 사용자는 401을 반환한다") {
            mockMvc.perform(get("/api/v1/user/issues/status"))
                .andExpect(status().isUnauthorized)
        }

        it("담당/작성 이슈 개수와 목록을 반환한다") {
            val assignedIssue = Issue(id = 1L, number = 1L, title = "담당 이슈", project = project)
            val createdIssue = Issue(id = 2L, number = 2L, title = "작성 이슈", project = project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubAllSectionsEmpty(1L)
            every { issueRepository.findByAssigneeAndState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(assignedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.findByAuthorIdAndState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(createdIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.countByAssigneeAndState(1L, State.OPEN) } returns 1L
            every { issueRepository.countByAssigneeAndState(1L, State.CLOSED) } returns 3L
            every { issueRepository.countByAuthorIdAndState(1L, State.OPEN) } returns 2L
            every { issueRepository.countByAuthorIdAndState(1L, State.CLOSED) } returns 5L

            mockMvc.perform(get("/api/v1/user/issues/status").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.assigned.openCount").value(1))
                .andExpect(jsonPath("$.assigned.closedCount").value(3))
                .andExpect(jsonPath("$.assigned.items[0].title").value("담당 이슈"))
                .andExpect(jsonPath("$.created.openCount").value(2))
                .andExpect(jsonPath("$.created.closedCount").value(5))
                .andExpect(jsonPath("$.created.items[0].title").value("작성 이슈"))
        }

        // yona-wiki P3-02 Step8.6 항목2 — commented/mentioned/favorite/shared 4개 섹션 신규 노출.
        it("commented/mentioned/favorite/shared 섹션을 함께 반환한다") {
            val commentedIssue = Issue(id = 3L, number = 3L, title = "댓글단 이슈", project = project)
            val mentionedIssue = Issue(id = 4L, number = 4L, title = "멘션된 이슈", project = project)
            val favoriteIssue = Issue(id = 5L, number = 5L, title = "즐겨찾기 이슈", project = project)
            val sharedIssue = Issue(id = 6L, number = 6L, title = "공유된 이슈", project = project)

            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubAllSectionsEmpty(1L)
            every { mentionService.getMentioningIssueIds(1L) } returns listOf(4L)
            every { issueRepository.findCommentedByState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(commentedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.findMentionedByState(listOf(4L), State.OPEN, null, any()) } returns
                PageImpl(listOf(mentionedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.findFavoriteByState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(favoriteIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.findSharedByState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(sharedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.countMentionedByState(listOf(4L), State.OPEN) } returns 1L
            every { issueRepository.countMentionedByState(listOf(4L), State.CLOSED) } returns 0L
            every { issueRepository.countCommentedByState(1L, State.OPEN) } returns 1L
            every { issueRepository.countFavoriteByState(1L, State.OPEN) } returns 1L
            every { issueRepository.countSharedByState(1L, State.OPEN) } returns 1L

            mockMvc.perform(get("/api/v1/user/issues/status").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.commented.items[0].title").value("댓글단 이슈"))
                .andExpect(jsonPath("$.mentioned.items[0].title").value("멘션된 이슈"))
                .andExpect(jsonPath("$.favorite.items[0].title").value("즐겨찾기 이슈"))
                .andExpect(jsonPath("$.shared.items[0].title").value("공유된 이슈"))
        }

        // yona-wiki P3-02 Step8.6 항목2 — 페이지네이션/정렬/검색 파라미터가 실제로 리포지토리
        // 호출에 그대로 전달되는지 검증(신규 백엔드 로직 없이 파라미터만 추가 전달).
        it("pageNum/state/filter/orderBy/orderDir 파라미터를 리포지토리 조회에 그대로 전달한다") {
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubAllSectionsEmptyForState(issueRepository, mentionService, 1L, State.CLOSED, "%bug%")

            mockMvc.perform(
                get("/api/v1/user/issues/status")
                    .param("pageNum", "2")
                    .param("state", "closed")
                    .param("filter", "bug")
                    .param("orderBy", "createdDate")
                    .param("orderDir", "asc")
                    .principal(auth)
            ).andExpect(status().isOk)

            verify(exactly = 1) {
                issueRepository.findByAssigneeAndState(1L, State.CLOSED, "%bug%", PageRequest.of(1, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdDate")))
            }
        }

        // yona-wiki P3-02 Step8.6 항목2 — commenterId/mentionId/sharerId/favoriteId를 명시하면
        // 로그인 사용자 자신이 아닌 다른 사용자 기준으로 조회할 수 있다(웹 UI와 동일한 유연성).
        it("commenterId/mentionId/sharerId/favoriteId를 명시하면 해당 id 기준으로 조회한다") {
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubAllSectionsEmpty(1L, mentionedFor = 1L)
            every { issueRepository.findCommentedByState(99L, State.OPEN, null, any()) } returns
                PageImpl(emptyList(), PageRequest.of(0, 20), 0)
            every { issueRepository.countCommentedByState(99L, State.OPEN) } returns 0L
            every { issueRepository.countCommentedByState(99L, State.CLOSED) } returns 0L
            every { mentionService.getMentioningIssueIds(88L) } returns emptyList()
            every { issueRepository.findFavoriteByState(77L, State.OPEN, null, any()) } returns
                PageImpl(emptyList(), PageRequest.of(0, 20), 0)
            every { issueRepository.countFavoriteByState(77L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(77L, State.CLOSED) } returns 0L
            every { issueRepository.findSharedByState(66L, State.OPEN, null, any()) } returns
                PageImpl(emptyList(), PageRequest.of(0, 20), 0)
            every { issueRepository.countSharedByState(66L, State.OPEN) } returns 0L
            every { issueRepository.countSharedByState(66L, State.CLOSED) } returns 0L

            mockMvc.perform(
                get("/api/v1/user/issues/status")
                    .param("commenterId", "99")
                    .param("mentionId", "88")
                    .param("favoriteId", "77")
                    .param("sharerId", "66")
                    .principal(auth)
            ).andExpect(status().isOk)

            verify(exactly = 1) { issueRepository.findCommentedByState(99L, State.OPEN, null, any()) }
            verify(exactly = 1) { mentionService.getMentioningIssueIds(88L) }
            verify(exactly = 1) { issueRepository.findFavoriteByState(77L, State.OPEN, null, any()) }
            verify(exactly = 1) { issueRepository.findSharedByState(66L, State.OPEN, null, any()) }
        }
    }
})

// state/filter 파라미터를 바꾼 케이스 전용 스텁 헬퍼 — 위 stubAllSectionsEmpty()는 항상
// State.OPEN + null keyword를 스텁하므로, 다른 state/keyword 조합을 검증하는 테스트는 별도로 둔다.
private fun stubAllSectionsEmptyForState(
    issueRepository: IssueRepository,
    mentionService: MentionService,
    userId: Long,
    state: State,
    keyword: String?
) {
    val emptyPage = PageImpl<Issue>(emptyList(), PageRequest.of(1, 20, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "createdDate")), 0)
    every { issueRepository.findByAssigneeAndState(userId, state, keyword, any()) } returns emptyPage
    every { issueRepository.findByAuthorIdAndState(userId, state, keyword, any()) } returns emptyPage
    every { issueRepository.findCommentedByState(userId, state, keyword, any()) } returns emptyPage
    every { issueRepository.findFavoriteByState(userId, state, keyword, any()) } returns emptyPage
    every { issueRepository.findSharedByState(userId, state, keyword, any()) } returns emptyPage
    every { mentionService.getMentioningIssueIds(userId) } returns emptyList()

    every { issueRepository.countByAssigneeAndState(userId, State.OPEN) } returns 0L
    every { issueRepository.countByAssigneeAndState(userId, State.CLOSED) } returns 0L
    every { issueRepository.countByAuthorIdAndState(userId, State.OPEN) } returns 0L
    every { issueRepository.countByAuthorIdAndState(userId, State.CLOSED) } returns 0L
    every { issueRepository.countCommentedByState(userId, State.OPEN) } returns 0L
    every { issueRepository.countCommentedByState(userId, State.CLOSED) } returns 0L
    every { issueRepository.countFavoriteByState(userId, State.OPEN) } returns 0L
    every { issueRepository.countFavoriteByState(userId, State.CLOSED) } returns 0L
    every { issueRepository.countSharedByState(userId, State.OPEN) } returns 0L
    every { issueRepository.countSharedByState(userId, State.CLOSED) } returns 0L
}
