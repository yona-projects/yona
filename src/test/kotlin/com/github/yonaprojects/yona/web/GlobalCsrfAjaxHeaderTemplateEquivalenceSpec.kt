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
// 2026-09-10 최초 수정: site/layout.html::head에 <meta name="_csrf">/<meta name="_csrf_header">를
// 추가하고, 그 두 meta를 읽어 모든 $.ajax 호출에 CSRF 헤더를 붙이는 $.ajaxSetup(beforeSend)
// 스크립트를 추가했다.
//
// 이후 별도 세션(P3-legal/P3-44, CSRF 보호 재활성화)에서 쿠키 기반 더블서브밋 패턴
// (CookieCsrfTokenRepository + SpaCsrfTokenRequestHandler, config/CsrfSupport.kt)으로 CSRF
// 아키텍처 자체가 더 완전한 형태로 교체됐다 — XSRF-TOKEN 쿠키를 읽어 $.ajax와 raw fetch() 양쪽
// 모두에 X-XSRF-TOKEN 헤더를 자동으로 붙인다. 최초의 meta 태그 기반 $.ajaxSetup 블록은 이
// 두번째 메커니즘에 의해 조용히 무력화됐다(jQuery.ajaxSetup의 beforeSend는 여러 번 호출해도
// 체이닝되지 않고 마지막 등록이 이전 것을 완전히 대체한다) — 이번 P3-48 화면별 재현 세션에서
// 이 사실을 확인하고 죽은 블록을 제거했다(메타 태그 자체는 남겨둠 — 아래 계약 검증 대상).
//
// 같은 세션에서 그 "beforeSend는 체이닝되지 않고 마지막 등록이 이긴다"는 성질 때문에 생기는
// 진짜 살아있는 버그를 하나 더 발견했다: common/yona.Tasklist.js(이슈/게시글 본문 tasklist
// 체크박스 토글, PATCH)가 자기 자신의 $.ajax() 호출에 개별 beforeSend 옵션
// (NProgress.start() 호출용)을 넘기고 있었는데, 이게 전역 $.ajaxSetup(beforeSend)를 완전히
// 대체해버려 CSRF 헤더가 아예 안 실렸다(Playwright로 실제 체크박스를 클릭해 403 Forbidden으로
// 재현). 개별 파일을 고치는 대신(재발 방지 안 됨) 전역 메커니즘 자체를 jQuery의 전역 ajax
// 이벤트(document의 ajaxSend)로 바꿔 이런 종류의 셰도잉이 구조적으로 불가능하게 만들었다 —
// ajaxSend는 등록된 모든 핸들러가 모든 요청마다 실행되고, 개별 호출의 beforeSend 옵션과
// 완전히 독립적이다.
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

            // 개별 $.ajax() 호출의 beforeSend 옵션(예: yona.Tasklist.js의 NProgress.start())이
            // 전역 CSRF 주입을 셰도잉하지 못하도록, ajaxSetup(beforeSend) 대신 항상 함께 실행되는
            // 전역 ajax 이벤트(ajaxSend)를 쓴다 — 이 계약이 다시 ajaxSetup(beforeSend)로
            // 회귀하지 않도록 마크업으로 고정한다.
            it("CSRF 헤더 주입은 개별 호출의 beforeSend로 셰도잉될 수 없는 전역 ajaxSend 이벤트를 써야 한다 (P3-48)") {
                val html = Jsoup.parse(
                    mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                        .andExpect(status().isOk).andReturn().response.contentAsString
                ).outerHtml()

                // 설명용 HTML 주석에는 역사적 맥락(과거 ajaxSetup 방식)을 설명하기 위해 여전히
                // 그 단어가 등장할 수 있으므로, 주석이 아니라 실제 "호출 형태"(jQuery.ajaxSetup(
                // 또는 $.ajaxSetup()의 유무로 판단한다.
                html.contains("ajaxSend") shouldBe true
                html.contains("jQuery.ajaxSetup(") shouldBe false
                html.contains("\$.ajaxSetup(") shouldBe false
            }
        }
    }
}
