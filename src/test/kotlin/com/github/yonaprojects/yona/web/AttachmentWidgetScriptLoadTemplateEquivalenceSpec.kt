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

// P3-50 조사 중 발견(중대): 첨부파일 업로드 위젯을 실제로 구현하는
// common/yobi.Files.js·common/yobi.Attachments.js가 어느 템플릿에서도 <script src>로 로드된 적이
// 없었다 — site/layout.html이 이미 yobi.Markdown.js에 대해 명시적으로 문서화해둔 것과 완전히
// 동일한 함정(전역 레거시 번들 yona-lib.js에 박혀있던, EasyMDE를 전혀 모르는 구버전 사본이
// 계속 실행되고 있었음). 그 결과 새 댓글/게시글 작성 폼·댓글 수정 폼에서 파일을 업로드해 카드를
// 클릭해 본문에 링크를 삽입해도, EasyMDE(CodeMirror)가 그 변경을 전혀 인지하지 못해(raw
// textarea만 바뀜) 저장 버튼 클릭 등으로 포커스가 빠지는 순간 CodeMirror가 자신의 변경 없는
// 내부 버퍼를 textarea에 다시 덮어써 방금 넣은 링크가 통째로 사라졌다 — 실제 제출되는 내용에서
// 첨부파일 참조가 사라지는 데이터 손실급 버그(Playwright로 실제 재현: 업로드 -> 카드 클릭 ->
// 저장 버튼 클릭 -> 서버가 받은 내용에 링크가 없음). site/layout.html::scripts에서
// yona-lib.js 바로 다음에 두 파일을 명시적으로 로드해 전역 yobi.Files/yobi.Attachments가
// EasyMDE를 인지하는 최신 버전으로 최종 덮어써지도록 정정했다(yobi.Markdown.js와 동일한 순서
// 원칙 — yona-lib.js보다 반드시 뒤).
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

        describe("P3-50: yobi.Files.js/yobi.Attachments.js가 yona-lib.js보다 뒤에 실제로 로드돼야 한다") {
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

            it("인덱스 화면(공통 레이아웃)은 yona-lib.js 뒤에 yobi.Files.js/yobi.Attachments.js를 로드해야 한다") {
                val html = mockMvc.perform(get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails)))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                val libIdx = html.indexOf("/javascripts/yona-lib.js")
                val filesIdx = html.indexOf("/javascripts/common/yobi.Files.js")
                val attachmentsIdx = html.indexOf("/javascripts/common/yobi.Attachments.js")

                (libIdx >= 0) shouldBe true
                (filesIdx > libIdx) shouldBe true
                (attachmentsIdx > libIdx) shouldBe true
            }
        }
    }
}
