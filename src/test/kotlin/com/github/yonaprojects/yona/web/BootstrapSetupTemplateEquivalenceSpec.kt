package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// legacy welcome/secret.scala.html -> bootstrap-setup.html,
// welcome/restart.scala.html -> bootstrap-restart.html
// 이 화면은 가입자 0명(최초 부팅) 상태에서만 도달 가능하므로 다른 스펙과 픽스처를 공유하지 않고
// 매 테스트마다 userRepository를 완전히 비워 legacy Global.java의 "최초 관리자 미생성" 상태를 재현한다.
class BootstrapSetupTemplateEquivalenceSpec @Autowired constructor(
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

        describe("bootstrap-setup / bootstrap-restart 템플릿 동치성 검증") {
            beforeTest {
                userRepository.deleteAll()
            }

            it("가입자가 0명일 때 GET /bootstrap-setup 은 legacy welcome/secret.scala.html과 동치인 최초 관리자 생성 화면을 렌더링해야 한다") {
                val result = mockMvc.perform(get("/bootstrap-setup"))
                    .andExpect(status().isOk)
                    .andReturn()

                val doc = Jsoup.parse(result.response.contentAsString)

                // legacy: <title>@Messages("app.welcome", siteName)</title> (ko-KR: 사이트명 그대로)
                doc.title() shouldBe "Yona"
                doc.select("h3").text() shouldBe "Yona"
                doc.select(".alert-block h4").text() shouldBe "사이트 관리자 계정을 생성합니다."
                doc.select(".alert-block").text().contains("사이트 관리자의 비밀번호가 알려지지 않도록 주의해 주세요.") shouldBe true

                // legacy dl 필드 5개(아이디/이름/이메일/비밀번호/비밀번호 재입력) 그대로
                doc.select("label[for=loginId]").text() shouldBe "아이디"
                doc.select("input#loginId").hasAttr("readonly") shouldBe true
                doc.select("input#loginId").attr("value") shouldBe "admin"
                doc.select("label[for=uname]").text() shouldBe "이름"
                doc.select("label[for=email]").text() shouldBe "이메일"
                doc.select("label[for=password]").text() shouldBe "비밀번호"
                doc.select("label[for=retypedPassword]").text() shouldBe "비밀번호를 한 번 더 입력해 주세요."

                doc.select("button[type=submit]").text() shouldBe "생성하기"

                // 에러가 없는 초기 진입 상태에서는 legacy처럼 필드별 에러 뱃지(label-important)가 하나도 없어야 한다
                doc.select("dt .label-important").size shouldBe 0
            }

            it("이미 가입자가 존재하면 GET /bootstrap-setup 은 홈으로 리다이렉트해야 한다(legacy Global.java 진입 가드에 대응)") {
                userRepository.save(User(loginId = "admin", name = "관리자", email = "admin@yona.io", state = UserState.SITE_ADMIN))

                mockMvc.perform(get("/bootstrap-setup"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))
            }

            it("loginId가 admin이 아니면 legacy와 동일하게 아이디 라벨 옆에 user.wrongloginId.alert 뱃지가 노출되어야 한다") {
                val result = mockMvc.perform(
                    post("/bootstrap-setup")
                        .with(csrf())
                        .param("loginId", "notadmin")
                        .param("name", "관리자")
                        .param("email", "admin@yona.io")
                        .param("password", "pw12345!")
                        .param("retypedPassword", "pw12345!")
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val doc = Jsoup.parse(result.response.contentAsString)
                val loginIdDt = doc.select("dt:has(label[for=loginId])")
                loginIdDt.select(".label-important").text() shouldBe "올바른 아이디를 입력하세요."
                // 다른 필드에는 에러가 없어야 함
                doc.select("dt:has(label[for=email]) .label-important").size shouldBe 0
                userRepository.count() shouldBe 0L
            }

            it("비밀번호와 비밀번호 확인이 다르면 legacy와 동일하게 재입력 라벨 옆에 user.confirmPassword.alert 뱃지가 노출되어야 한다") {
                val result = mockMvc.perform(
                    post("/bootstrap-setup")
                        .param("loginId", "admin")
                        .param("name", "관리자")
                        .param("email", "admin@yona.io")
                        .param("password", "pw12345!")
                        .param("retypedPassword", "different!")
                        .with(csrf())
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val doc = Jsoup.parse(result.response.contentAsString)
                doc.select("dt:has(label[for=retypedPassword]) .label-important").text() shouldBe "입력한 두 비밀번호가 서로 일치하지 않습니다"
            }

            it("이메일이 비어있으면 legacy와 동일하게 이메일 라벨 옆에 validation.invalidEmail 뱃지가 노출되어야 한다") {
                val result = mockMvc.perform(
                    post("/bootstrap-setup")
                        .param("loginId", "admin")
                        .param("name", "관리자")
                        .param("email", "")
                        .param("password", "pw12345!")
                        .param("retypedPassword", "pw12345!")
                        .with(csrf())
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val doc = Jsoup.parse(result.response.contentAsString)
                doc.select("dt:has(label[for=email]) .label-important").text() shouldBe "올바른 이메일을 입력해 주세요."
            }

            it("모든 입력이 올바르면 SITE_ADMIN 계정이 생성되고 legacy welcome/restart.scala.html과 동치인 재시작 안내 화면이 렌더링되어야 한다") {
                val result = mockMvc.perform(
                    post("/bootstrap-setup")
                        .with(csrf())
                        .param("loginId", "admin")
                        .param("name", "관리자")
                        .param("email", "admin@yona.io")
                        .param("password", "pw12345!")
                        .param("retypedPassword", "pw12345!")
                )
                    .andExpect(status().isOk)
                    .andReturn()

                val doc = Jsoup.parse(result.response.contentAsString)
                // legacy: <title>@Messages("app.restart.welcome")</title>, <h3>@Messages("app.restart.welcome")</h3>
                doc.title() shouldBe "환영합니다!"
                doc.select("h3").text() shouldBe "환영합니다!"
                doc.select(".secret-box").text() shouldBe "서버를 재시작해야합니다."

                val created = userRepository.findByLoginId("admin").orElse(null)
                created shouldNotBe null
                created?.state shouldBe UserState.SITE_ADMIN
                created?.email shouldBe "admin@yona.io"
            }
        }
    }
}
