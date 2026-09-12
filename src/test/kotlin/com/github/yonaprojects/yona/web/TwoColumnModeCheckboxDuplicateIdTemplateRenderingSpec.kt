package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.assertions.withClue
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 사용자 요청(2026-09-12)으로 정리: legacy common/showSubtasksCheckbox.scala.html/
// common/twoColumnModeCheckboxArea.scala.html 둘 다 원본부터 바깥 wrapper div에
// id="two-column-mode-checkbox"를 중복 사용하는 버그를 갖고 있었다(legacy 원본 확인됨).
// Playwright 실측 결과 실제 동작(popover 표시/체크박스 토글)에는 문제가 없었지만, HTML id
// 유일성 위반 자체는 legacy 버그라도 정리하기로 결정 - "자식이슈 펼쳐보기" 위젯(.show-subtasks)
// 쪽 id만 show-subtasks-checkbox로 고유하게 바꾼다("2단 보기" 위젯 쪽 id는
// yona.twoColumnMode.js가 명시적으로 참조하므로 유지).
class TwoColumnModeCheckboxDuplicateIdTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("2단 보기/자식이슈 펼쳐보기 위젯의 id가 서로 달라야 한다") {
            val suffix = System.currentTimeMillis().toString()
            val author = userRepository.save(User(loginId = "dupid-author-$suffix", name = "작성자", email = "dupid-author-$suffix@yona.io"))

            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            fun assertDistinctIds(html: String, screenName: String) {
                val doc = Jsoup.parse(html)
                val twoColumnId = doc.select(".two-column-icon").attr("id")
                val showSubtasksId = doc.select(".show-subtasks").attr("id")
                twoColumnId shouldBe "two-column-mode-checkbox"
                showSubtasksId shouldNotBe "two-column-mode-checkbox"
                showSubtasksId shouldNotBe ""
                withClue(screenName) { showSubtasksId shouldNotBe twoColumnId }
            }

            it("/user/issues(내 이슈) 화면에서 두 id가 달라야 한다") {
                val html = mockMvc.perform(get("/user/issues").with(authOf(author)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                assertDistinctIds(html, "/user/issues")
            }

            it("/user/{loginId}(프로필) 화면에서 두 id가 달라야 한다") {
                val html = mockMvc.perform(get("/user/${author.loginId}").with(authOf(author)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                assertDistinctIds(html, "/user/{loginId}")
            }
        }
    }
}
