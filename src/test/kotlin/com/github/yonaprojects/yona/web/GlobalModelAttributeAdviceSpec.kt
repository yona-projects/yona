package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.TemplateHelper
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.support.YonaUpdateService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserSetting
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

// yona controllers/Application.java의 addGlobalAttributes()류 대응 — 모든 뷰에 공통으로
// 주입되는 @ModelAttribute들을 검증한다.
class GlobalModelAttributeAdviceSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val markdownService = mockk<MarkdownService>()
    val templateHelper = mockk<TemplateHelper>()
    val yonaUpdateService = mockk<YonaUpdateService>()
    val issueRepository = mockk<IssueRepository>()
    val userSettingRepository = mockk<UserSettingRepository>()

    val advice = GlobalModelAttributeAdvice(
        userRepository, markdownService, templateHelper, yonaUpdateService,
        issueRepository, userSettingRepository,
        true, true, "링크이름", "http://link", true
    )

    beforeTest {
        clearMocks(userRepository, issueRepository, userSettingRepository)
        SecurityContextHolder.clearContext()
    }

    describe("currentUser()") {
        it("authentication이 없으면 null을 반환해야 한다") {
            advice.currentUser() shouldBe null
        }

        it("authentication이 있어도 isAuthenticated가 false면 null을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")
            auth.isAuthenticated = false
            SecurityContextHolder.getContext().authentication = auth

            advice.currentUser() shouldBe null
        }

        it("anonymousUser면 null을 반환해야 한다") {
            val auth = UsernamePasswordAuthenticationToken("anonymousUser", null, emptyList())
            SecurityContextHolder.getContext().authentication = auth

            advice.currentUser() shouldBe null
        }

        it("정상 인증된 사용자가 DB에 있으면 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("gildong", "password", emptyList())

            advice.currentUser() shouldBe user
        }

        it("정상 인증됐어도 DB에 사용자가 없으면 null을 반환해야 한다") {
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("ghost", "password", emptyList())

            advice.currentUser() shouldBe null
        }
    }

    describe("단순 위임 @ModelAttribute들") {
        it("markdownService/templateHelper/yonaUpdateService를 그대로 반환해야 한다") {
            advice.markdownService() shouldBe markdownService
            advice.templateHelper() shouldBe templateHelper
            advice.yonaUpdateService() shouldBe yonaUpdateService
        }

        it("currentRequestPath는 요청의 requestURI를 그대로 반환해야 한다") {
            val request = mockk<HttpServletRequest>()
            every { request.requestURI } returns "/issues/1"

            advice.currentRequestPath(request) shouldBe "/issues/1"
        }

        it("생성자로 주입된 설정값들을 그대로 반환해야 한다") {
            advice.sendYonaUsage() shouldBe true
            advice.hideProjectListing() shouldBe true
            advice.navbarCustomLinkName() shouldBe "링크이름"
            advice.navbarCustomLinkUrl() shouldBe "http://link"
            advice.useSocialLoginOnly() shouldBe true
        }
    }

    describe("myOpenIssueCount()") {
        it("로그인하지 않았으면(currentUser null) 0을 반환해야 한다") {
            advice.myOpenIssueCount() shouldBe 0L
        }

        it("로그인했으면 해당 사용자의 열린 이슈 개수를 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { issueRepository.countByAssigneeAndState(1L, State.OPEN) } returns 5L
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("gildong", "password", emptyList())

            advice.myOpenIssueCount() shouldBe 5L
        }
    }

    describe("loginDefaultPage()") {
        it("로그인하지 않았으면(currentUser null) null을 반환해야 한다") {
            advice.loginDefaultPage() shouldBe null
        }

        it("사용자 설정이 없으면 null을 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { userSettingRepository.findByUserId(1L) } returns Optional.empty()
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("gildong", "password", emptyList())

            advice.loginDefaultPage() shouldBe null
        }

        it("사용자 설정이 있으면 loginDefaultPage를 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            val setting = UserSetting(id = 1L, user = user, loginDefaultPage = "/my/issues")
            every { userSettingRepository.findByUserId(1L) } returns Optional.of(setting)
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("gildong", "password", emptyList())

            advice.loginDefaultPage() shouldBe "/my/issues"
        }
    }
})
