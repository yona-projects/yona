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

// CSRF는 쿠키 기반 더블서브밋 패턴(CookieCsrfTokenRepository + SpaCsrfTokenRequestHandler,
// config/CsrfSupport.kt)으로 처리한다 — XSRF-TOKEN 쿠키를 읽어 fetch()에 X-XSRF-TOKEN 헤더를
// 자동으로 붙인다.
//
// 함정: jQuery.ajaxSetup(beforeSend)는 여러 번 호출해도 체이닝되지 않고 마지막 등록이 이전
// 것을 완전히 대체한다. 그래서 개별 $.ajax() 호출이 자기만의 beforeSend 옵션(예:
// common/yona.Tasklist.js의 NProgress.start() 호출용)을 넘기면 전역 CSRF 주입이 통째로
// 사라진다(Playwright로 체크박스 클릭 -> 403 Forbidden 재현). 저장소 전체가 jQuery ajax에서
// fetch로 전환되면서 이 문제 자체가 사라졌고, 지금은 전역 window.fetch 패치만이 CSRF 주입을
// 담당한다.
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

        describe("P3-48 전역 CSRF 마크업 계약") {
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

            it("인덱스 화면은 비어있지 않은 _csrf/_csrf_header meta를 렌더링해야 한다") {
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
            }

            it("인덱스 화면은 GET이 아닌 요청에 CSRF 헤더를 자동으로 얹는 전역 fetch 래퍼를 렌더링해야 한다") {
                val html = Jsoup.parse(
                    mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                        .andExpect(status().isOk).andReturn().response.contentAsString
                ).outerHtml()

                (html.contains("window.fetch") && html.contains("originalFetch")) shouldBe true
            }

            // jQuery 코어 자체가 제거되어 저장소 전체에 $.ajax() 호출이 0건이다(모두 fetch로
            // 전환). 예전 대체 메커니즘이던 `jQuery(document).ajaxSend(...)` 블록은 jQuery가 없으면
            // 예외를 던지는 죽은 코드였음이 Playwright 실측(전역 pageerror)으로 드러나 제거했다 -
            // 다시 jQuery ajax 메커니즘으로 회귀하면 이 테스트가 잡아낸다.
            it("CSRF 헤더 주입에 더 이상 jQuery 기반 ajaxSend/ajaxSetup 호출을 쓰지 않아야 한다 (P3-48, P3-70 라운드12)") {
                val html = Jsoup.parse(
                    mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                        .andExpect(status().isOk).andReturn().response.contentAsString
                ).outerHtml()

                // 설명용 HTML 주석에는 역사적 맥락(과거 ajaxSetup/ajaxSend 방식)을 설명하기 위해
                // 여전히 그 단어들이 등장할 수 있으므로, 주석이 아니라 실제 "호출 형태"의
                // 유무로 판단한다.
                html.contains("jQuery(document).ajaxSend(") shouldBe false
                html.contains("jQuery.ajaxSetup(") shouldBe false
                html.contains("\$.ajaxSetup(") shouldBe false
            }
        }
    }
}
