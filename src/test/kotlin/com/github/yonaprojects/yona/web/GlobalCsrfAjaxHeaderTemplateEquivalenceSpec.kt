package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-48: raw $.ajax(PUT/DELETE/PATCH) 호출이 CSRF 토큰을 못 실어 403으로 거부되던 문제 대응.
//
// site/layout.html::head에 <meta name="_csrf">/<meta name="_csrf_header">를 추가하고,
// site/layout.html::scripts에 그 두 meta를 읽어 모든 $.ajax 호출에 전역으로 CSRF 헤더를 붙이는
// $.ajaxSetup(beforeSend) 스크립트를 추가했다. 이 스펙은 (1) 두 meta 태그가 실제로 렌더링되고
// 값이 비어있지 않은지, (2) 전역 ajaxSetup 스크립트가 로드되는지를 마크업 계약으로 검증한다
// (실제 $.ajax 호출이 헤더를 붙이는 런타임 동작 자체는 Playwright로 실제 재현해 확인 완료 —
// board/edit 저장 시 403 Forbidden이 사라지고 실제로 저장까지 성공하는 것을 end-to-end로 검증함).
class GlobalCsrfAjaxHeaderTemplateEquivalenceSpec @Autowired constructor(
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

        describe("P3-48 전역 CSRF ajaxSetup 마크업 계약") {
            val user = userRepository.findByLoginId("csrf-meta-user").orElseGet {
                userRepository.save(User(loginId = "csrf-meta-user", name = "csrf메타테스트", email = "csrf-meta-user@yona.io"))
            }
            val userDetails = YonaUserDetails(
                id = user.id!!,
                loginId = user.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("인덱스 화면은 비어있지 않은 _csrf/_csrf_header meta와 전역 ajaxSetup 스크립트를 렌더링해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                        .andExpect(status().isOk).andReturn().response.contentAsString
                )

                val csrfTokenMeta = doc.select("meta[name=_csrf]")
                val csrfHeaderMeta = doc.select("meta[name=_csrf_header]")

                csrfTokenMeta.size shouldBe 1
                csrfHeaderMeta.size shouldBe 1
                csrfTokenMeta.attr("content").shouldNotBeBlank()
                csrfHeaderMeta.attr("content").shouldNotBeBlank()

                val html = doc.outerHtml()
                (html.contains("ajaxSetup") && html.contains("beforeSend")) shouldBe true
            }
        }
    }
}
