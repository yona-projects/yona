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

// P3-46 #2: 프로필 사진 크롭 위젯(Jcrop -> Cropper.js) 교체.
//
// 이 스펙은 실제 크롭 UI 동작(브라우저 JS 상호작용, 128x128 렌더링 결과)은 검증하지 않는다 —
// MockMvc+Jsoup 하네스는 렌더링된 마크업과 로드되는 스크립트/CSS 경로까지만 볼 수 있다. 대신
// 아래 "마크업 계약"이 회귀 없이 유지되는지를 검증한다.
//
// 대상 화면: user/edit(GET /user/editform) 단 하나 (yobi.user.Setting.js에서 유일하게 참조).
class AvatarCropWidgetTemplateEquivalenceSpec @Autowired constructor(
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

        describe("P3-46 #2 프로필 사진 크롭 위젯(Jcrop -> Cropper.js) 마크업 계약 회귀 검증") {
            val owner = userRepository.findByLoginId("avatarcrop-owner").orElseGet {
                userRepository.save(User(loginId = "avatarcrop-owner", name = "아바타크롭사용자", email = "avatarcrop-owner@yona.io"))
            }

            val ownerDetails = YonaUserDetails(
                id = owner.id!!,
                loginId = owner.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )

            it("user/edit 화면은 Jcrop 리소스를 더 이상 로드하지 않고 Cropper.js 리소스를 로드해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/user/editform")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                doc.select("link[href*=jcrop]").size shouldBe 0
                doc.select("script[src*=Jcrop]").size shouldBe 0
                doc.select("link[href*='/javascripts/lib/cropperjs/'][rel=stylesheet]").size shouldBe 1
                doc.select("script[src*='/javascripts/lib/cropperjs/']").size shouldBe 1
                // canvas-to-blob.js는 이번 작업 범위 밖(Jcrop 전용이 아닌 범용 폴리필)이므로 그대로 유지되어야 한다.
                doc.select("script[src*='canvas-to-blob.js']").size shouldBe 1
            }

            it("user/edit 화면은 크롭 모달 마크업(원본 이미지/미리보기/캔버스/버튼)을 그대로 유지해야 한다") {
                val doc = Jsoup.parse(
                    mockMvc.perform(
                        get("/user/editform")
                            .with(SecurityMockMvcRequestPostProcessors.user(ownerDetails))
                    ).andExpect(status().isOk).andReturn().response.contentAsString
                )

                val modal = doc.select("div#avatarCropWrap.modal")
                modal.size shouldBe 1

                // 크롭 대상 원본 이미지 (htElement.welAvatarCropImg: ".modal-body > img")
                modal.select(".modal-body > img").size shouldBe 1
                // 실시간 미리보기 썸네일 (htElement.welAvatarCropPreviewImg: ".avatar-wrap > img")
                modal.select(".avatar-wrap > img").size shouldBe 1
                // 최종 128x128 렌더링 캔버스 (htElement.elAvatarCropCanvas)
                val canvas = modal.select(".modal-body canvas")
                canvas.size shouldBe 1
                canvas.attr("width") shouldBe "128"
                canvas.attr("height") shouldBe "128"
                // 적용 버튼 (htElement.welBtnSubmitCrop)
                modal.select("button.btnSubmitCrop").size shouldBe 1
            }
        }
    }
}
