package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import java.util.Locale

// site/layout.html의 tplAttachedFile/tplDropFilesHere는 <script type="text/x-jquery-tmpl">
// 안이라 th:text 같은 속성 프로세서는 처리되지 않는다(스크립트 내용은 raw text) — 로케일과
// 무관하게 항상 템플릿에 적힌 한국어 기본값이 노출되는 i18n 회귀가 있었다. v1.6(Twirl)은
// 같은 위치에서 @Messages(...)를 순수 텍스트 치환으로 처리해 로케일별로 정상 동작했다
// (app/views/common/fileUploader.scala.html). th:inline="text" + [[#{...}]] 인라인 토큰으로
// 고쳤다 - 실제로 처리됐는지(원문 토큰이 그대로 남아있지 않은지)와 값이 채워지는지를 검증한다.
class AttachmentJqueryTemplateI18nSpec @Autowired constructor(
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

        describe("첨부파일 jquery-tmpl 문구는 th:inline 처리를 거쳐 실제 메시지 값으로 채워져야 한다") {
            val user = userRepository.findByLoginId("attach-i18n-user").orElseGet {
                userRepository.save(User(loginId = "attach-i18n-user", name = "첨부i18n테스트", email = "attach-i18n-user@yona.io"))
            }
            val userDetails = YonaUserDetails(
                id = user.id!!,
                loginId = user.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("한국어 로케일 요청 시 실제 메시지 값(한국어)이 렌더링되고, 미처리 인라인 토큰이 원문 그대로 남지 않아야 한다") {
                val body = mockMvc.perform(
                    get("/").locale(Locale.KOREAN)
                        .with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                // th:inline="text"가 실제로 [[#{...}]] 토큰을 처리했다는 직접 증거 — 처리되지
                // 않았다면 원문 토큰이 그대로 노출된다.
                body.contains("[[#{common.attach.clickToPost}]]") shouldBe false
                body.contains("[[#{common.attach.dropFilesHere}]]") shouldBe false
                // th:text 속성 자체도 raw text라 처리되지 않은 채 그대로 노출되던 것이 과거 버그였다.
                body.contains("th:text=\"#{common.attach.clickToPost}\"") shouldBe false

                body.contains("본문에 넣기") shouldBe true
                body.contains("여기에 파일을 끌어다 놓으면 업로드 됩니다") shouldBe true
            }
        }
    }
}
