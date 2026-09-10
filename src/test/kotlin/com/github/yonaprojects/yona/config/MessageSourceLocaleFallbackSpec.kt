package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Locale

// P3-51: Spring Boot의 spring.messages.fallback-to-system-locale 기본값(true)은 요청 로케일
// 전용 번들 파일(예: messages_en.properties)이 없으면 root(messages.properties, 영어) 이전에
// "서버 JVM 시스템 기본 로케일" 번들을 먼저 시도한다. 이 개발 샌드박스처럼 JVM 기본 로케일이
// ko_KR인 환경에서는 Locale.ENGLISH를 명시해도 한국어 메시지가 새어나온다 — 배포 환경마다
// 달라지는 비결정적 버그(legacy `application.langs`의 결정적 첫 값 en-US와 동치가 아님).
// application.yml에 spring.messages.fallback-to-system-locale: false를 추가해 root(영어)
// 번들로만 결정적으로 폴백하도록 고정한다.
class MessageSourceLocaleFallbackSpec @Autowired constructor(
    private val messageSource: MessageSource,
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

            // BootstrapSetupInterceptor가 DB에 회원이 0명이면 "/"를 무조건
            // /bootstrap-setup으로 302 리다이렉트한다 — 익명 사용자로 "/"의 실제 인트로
            // 화면(로그인 필요없는 sec:authorize="isAnonymous()" 블록)을 검증하려면 최소 1명의
            // 회원이 존재해야 한다(이 사용자로 로그인하지는 않는다 — 요청 자체는 익명 유지).
            if (userRepository.count() == 0L) {
                userRepository.save(User(loginId = "locale-bootstrap-user", name = "로케일테스트용부트스트랩사용자", email = "locale-bootstrap-user@yona.io"))
            }
        }

        describe("P3-51: MessageSource는 시스템 로케일이 아니라 root(영어) 번들로 결정적으로 폴백해야 한다") {
            it("messages_en.properties가 없는 상태에서 Locale.ENGLISH를 요청하면 root(영어) 문구를 반환해야 한다") {
                val message = messageSource.getMessage("common.attach.clickToPost", null, Locale.ENGLISH)
                message shouldBe "Click to post"
            }

            it("Locale.KOREAN을 명시적으로 요청하면 여전히 한국어 문구를 반환해야 한다(결정적 로케일 선택 자체는 유지)") {
                val message = messageSource.getMessage("common.attach.clickToPost", null, Locale.KOREAN)
                message shouldBe "본문에 넣기"
            }
        }

        // 로그인한 사용자의 대시보드 화면에는 title.login/button.login 문구 자체가
        // sec:authorize="isAnonymous()" 블록 안에 있어 렌더링되지 않는다(Thymeleaf가 구조적으로
        // 제거함) — 반드시 익명(비로그인) 요청으로 검증해야 한다.
        describe("P3-51: Accept-Language 헤더가 없는 익명 요청은 서버 JVM/OS 시스템 로케일과 무관하게 영어를 기본값으로 써야 한다") {
            it("Accept-Language 헤더 없이 요청하면 영어(root) UI 문구가 렌더링돼야 한다(legacy application.langs의 첫 값 en-US와 동치)") {
                val body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "Log in"
            }

            it("Accept-Language: ko로 명시하면 여전히 한국어 UI 문구가 렌더링돼야 한다") {
                val body = mockMvc.perform(get("/").locale(Locale.KOREAN))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "로그인"
            }
        }
    }
}
