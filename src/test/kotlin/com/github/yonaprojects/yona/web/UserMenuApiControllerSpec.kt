package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.MenuIssue
import com.github.yonaprojects.yona.domain.user.MenuOrganization
import com.github.yonaprojects.yona.domain.user.MenuProject
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserMenu
import com.github.yonaprojects.yona.domain.user.UserMenuService
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

class UserMenuApiControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val userMenuService = mockk<UserMenuService>()
    val mockMvc = MockMvcBuilders.standaloneSetup(UserMenuApiController(userRepository, userMenuService)).build()

    val me = User(id = 1L, loginId = "me", name = "나", email = "me@example.com")
    val auth = UsernamePasswordAuthenticationToken("me", "pw")
    val project = MenuProject(10L, "mine", "me", "설명", "/me/mine/go", true)
    val emptyMenu = UserMenu(
        loginId = "me", personal = emptyList(), favoriteOrganizations = emptyList(), organizations = emptyList(),
        favoriteProjects = emptyList(), recentlyVisited = emptyList(), createdByMe = emptyList(),
        watching = emptyList(), joinmember = emptyList(), visitedIssues = emptyList()
    )

    describe("GET /-_-api/v1/usermenu") {
        it("인증되지 않은 요청은 401이어야 하고 데이터를 조회하지 않아야 한다") {
            mockMvc.perform(get("/-_-api/v1/usermenu")).andExpect(status().isUnauthorized)
            verify(exactly = 0) { userMenuService.load(any()) }
        }

        it("알 수 없는 로그인 사용자는 401이어야 한다") {
            every { userRepository.findByLoginId("me") } returns Optional.empty()
            mockMvc.perform(get("/-_-api/v1/usermenu").principal(auth)).andExpect(status().isUnauthorized)
        }

        it("목록이 모두 비어 있어도 모든 키를 배열로 내려줘야 한다") {
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { userMenuService.load(me) } returns emptyMenu

            mockMvc.perform(get("/-_-api/v1/usermenu").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.loginId").value("me"))
                .andExpect(jsonPath("$.personal").isArray)
                .andExpect(jsonPath("$.favoriteOrganizations").isArray)
                .andExpect(jsonPath("$.organizations").isArray)
                .andExpect(jsonPath("$.favoriteProjects").isArray)
                .andExpect(jsonPath("$.recentlyVisited").isArray)
                .andExpect(jsonPath("$.createdByMe").isArray)
                .andExpect(jsonPath("$.watching").isArray)
                .andExpect(jsonPath("$.joinmember").isArray)
                .andExpect(jsonPath("$.visitedIssues").isArray)
        }

        it("프로젝트·조직·이슈 항목의 필드를 그대로 직렬화해야 한다") {
            every { userRepository.findByLoginId("me") } returns Optional.of(me)
            every { userMenuService.load(me) } returns emptyMenu.copy(
                createdByMe = listOf(project),
                favoriteOrganizations = listOf(MenuOrganization(20L, "acme", true, listOf(project))),
                visitedIssues = listOf(MenuIssue("버그 수정", "/me/mine/issue/3"))
            )

            mockMvc.perform(get("/-_-api/v1/usermenu").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.createdByMe[0].id").value(10))
                .andExpect(jsonPath("$.createdByMe[0].href").value("/me/mine/go"))
                .andExpect(jsonPath("$.createdByMe[0].favorite").value(true))
                .andExpect(jsonPath("$.favoriteOrganizations[0].name").value("acme"))
                .andExpect(jsonPath("$.favoriteOrganizations[0].projects[0].name").value("mine"))
                .andExpect(jsonPath("$.visitedIssues[0].title").value("버그 수정"))
                .andExpect(jsonPath("$.visitedIssues[0].href").value("/me/mine/issue/3"))
        }
    }
})
