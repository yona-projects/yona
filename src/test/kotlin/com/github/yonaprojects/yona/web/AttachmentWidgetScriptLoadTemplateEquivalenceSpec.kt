package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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

// P3-50 조사 당시 common/yona.Files.js·common/yona.Attachments.js가 어느 템플릿에서도 <script src>로
// 로드된 적이 없어, 전역 레거시 번들 yona-lib.js에 박혀있던 EasyMDE를 전혀 모르는 구버전 사본이
// 계속 실행되는 회귀가 있었다. 당시에는 site/layout.html에서 yona-lib.js 다음에 두 파일을 개별
// 로드해 전역 정의를 최신 버전으로 덮어쓰는 방식(override)으로 정정했었다. 이후
// support-script/js-bundle/minify-js.sh(legacy 번들링 스크립트 부활)로 yona-lib.js 자체를 최신
// 소스로 재생성해 번들 안의 사본이 더 이상 구버전이 아니게 됐으므로, 이 override용 개별 로드는
// 순수 중복이 되어 site/layout.html에서 제거했다 — 이제는 개별 로드가 없어야 하는 쪽을 검증한다.
class AttachmentWidgetScriptLoadTemplateEquivalenceSpec @Autowired constructor(
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

        describe("P3-50 후속: yona.Files.js/yona.Attachments.js는 yona-lib.js 번들 재생성 후 더 이상 개별 로드되지 않아야 한다") {
            val user = userRepository.findByLoginId("attach-script-user").orElseGet {
                userRepository.save(User(loginId = "attach-script-user", name = "첨부스크립트사용자", email = "attach-script-user@yona.io"))
            }
            val userDetails = YonaUserDetails(
                id = user.id!!,
                loginId = user.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("인덱스 화면(공통 레이아웃)은 yona-lib.js는 로드하되 yona.Files.js/yona.Attachments.js는 개별 로드하지 않아야 한다") {
                val html = mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select("script[src='/javascripts/yona-lib.js']").size shouldBe 1
                doc.select("script[src='/javascripts/common/yona.Files.js']").size shouldBe 0
                doc.select("script[src='/javascripts/common/yona.Attachments.js']").size shouldBe 0
            }
        }
    }
}
