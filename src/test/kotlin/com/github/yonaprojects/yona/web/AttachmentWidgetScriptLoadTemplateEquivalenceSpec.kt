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

// yona-lib.js 번들이 minify-js.sh로 최신 소스에서 재생성되므로, 번들 안의 yona.Files.js/
// yona.Attachments.js는 항상 최신이다 - 개별 <script src> 로드로 덮어쓸 필요가 없고,
// 남아있다면 순수 중복이다.
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
